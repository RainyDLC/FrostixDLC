import argparse
import base64
import hashlib
import re
from pathlib import Path


PART_SIZE = 192 * 1024
CHUNK_SIZE = 3072
MAX_SIZE = 256 * 1024 * 1024
IDENTIFIER = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*\Z")
SHA256 = re.compile(r"[0-9a-f]{64}\Z")
JAVA_RESERVED = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
    "const", "continue", "default", "do", "double", "else", "enum", "extends",
    "final", "finally", "float", "for", "goto", "if", "implements", "import",
    "instanceof", "int", "interface", "long", "native", "new", "package", "private",
    "protected", "public", "return", "short", "static", "strictfp", "super", "switch",
    "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile",
    "while", "true", "false", "null", "_", "record", "sealed", "permits", "yield",
    "var", "module", "open", "requires", "exports", "opens", "to", "uses", "provides",
    "with", "transitive",
}


def java_name(value: str) -> bool:
    return bool(IDENTIFIER.fullmatch(value)) and value not in JAVA_RESERVED


def write_java(path: Path, source: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(source + "\n", encoding="utf-8", newline="\n")


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            digest.update(block)
    return digest.hexdigest()


def generate_file(target: Path, package: str, name: str, data: bytes, digest: str) -> int:
    package_line = f"package {package};\n\n"
    part_names = []
    for part_index, start in enumerate(range(0, len(data), PART_SIZE)):
        block = data[start:start + PART_SIZE]
        part_name = f"{name}Part{part_index:04d}"
        part_names.append(part_name)
        lines = [package_line, f"final class {part_name} {{",
                 f"    private {part_name}() {{}}",
                 "    static void put(byte[] output, int offset) {",
                 "        int cursor = offset;"]
        for chunk_start in range(0, len(block), CHUNK_SIZE):
            encoded = base64.b64encode(block[chunk_start:chunk_start + CHUNK_SIZE]).decode("ascii")
            lines.append(f'        cursor = {name}.copy(output, cursor, "{encoded}");')
        lines.extend([
            f'        if (cursor != offset + {len(block)}) throw new IllegalStateException("Corrupt embedded data part");',
            "    }",
            "}",
        ])
        write_java(target / f"{part_name}.java", "\n".join(lines))

    lines = [package_line, "import java.util.Base64;", "", f"public final class {name} {{",
             f"    public static final int SIZE = {len(data)};",
             f'    public static final String SHA256 = "{digest}";',
             f"    private {name}() {{}}", "",
             "    public static byte[] bytes() {",
             "        byte[] output = new byte[SIZE];"]
    groups = (len(part_names) + 63) // 64
    for group in range(groups):
        lines.append(f"        group{group}(output);")
    lines.extend(["        return output;", "    }", "",
                  "    static int copy(byte[] output, int offset, String encoded) {",
                  "        byte[] decoded = Base64.getDecoder().decode(encoded);",
                  "        System.arraycopy(decoded, 0, output, offset, decoded.length);",
                  "        return offset + decoded.length;",
                  "    }"])
    for group in range(groups):
        lines.extend(["", f"    private static void group{group}(byte[] output) {{"])
        for part_index in range(group * 64, min((group + 1) * 64, len(part_names))):
            lines.append(f"        {part_names[part_index]}.put(output, {part_index * PART_SIZE});")
        lines.append("    }")
    lines.append("}")
    write_java(target / f"{name}.java", "\n".join(lines))
    return len(part_names)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--package", required=True)
    args = parser.parse_args()
    if not all(java_name(part) for part in args.package.split(".")):
        parser.error("--package must be a dotted Java package name")
    manifest = args.manifest.resolve(strict=True)
    package_dir = (args.out.resolve() / Path(*args.package.split("."))).resolve()
    if not package_dir.is_relative_to(args.out.resolve()):
        parser.error("Generated package escapes the output directory")

    names = set()
    generated_names = set()
    resources = []
    lines = manifest.read_text(encoding="utf-8-sig").splitlines()
    if not lines:
        parser.error("Embedded resource manifest is empty")
    for number, line in enumerate(lines, 1):
        fields = line.split("\t")
        if len(fields) != 3:
            parser.error(f"Line {number}: expected ClassName, relative path, SHA-256 separated by tabs")
        name, relative, expected = fields
        if not java_name(name) or name.casefold() in names:
            parser.error(f"Line {number}: invalid or duplicate Java class name")
        names.add(name.casefold())
        if not SHA256.fullmatch(expected):
            parser.error(f"Line {number}: expected a lowercase SHA-256 digest")
        source = (manifest.parent / relative).resolve()
        if Path(relative).is_absolute() or not source.is_relative_to(manifest.parent):
            parser.error(f"Line {number}: resource path escapes the manifest directory")
        if not source.is_file():
            parser.error(f"Line {number}: resource file is missing: {source}")
        size = source.stat().st_size
        if size > MAX_SIZE:
            parser.error(f"Line {number}: resource file exceeds {MAX_SIZE} bytes")
        digest = file_sha256(source)
        if digest != expected:
            parser.error(f"Line {number}: SHA-256 mismatch for {source}")
        parts = (size + PART_SIZE - 1) // PART_SIZE
        output_names = {name.casefold()} | {f"{name}Part{index:04d}".casefold() for index in range(parts)}
        if output_names & generated_names:
            parser.error(f"Line {number}: generated Java class name collision")
        generated_names.update(output_names)
        resources.append((name, source, size, digest))

    for name, source, size, digest in resources:
        data = source.read_bytes()
        if len(data) != size or hashlib.sha256(data).hexdigest() != digest:
            parser.error(f"Resource changed after validation: {source}")
        parts = generate_file(package_dir, args.package, name, data, digest)
        print(f"{name}: {len(data)} bytes, {parts} generated byte parts, SHA-256 {digest}")


if __name__ == "__main__":
    main()
