



#include "builder.h"
#include "jar.h"
#include "codegen.h"
#include <fstream>
#include <iostream>
#include <sstream>
#include <iomanip>
#include <ctime>
#include <array>
#include <filesystem>
#include <cstdlib>

namespace fs = std::filesystem;
















static std::vector<RuntimeClassEntry> loadRuntimeClasses(const std::string& dir) {
    std::vector<RuntimeClassEntry> result;
    if (dir.empty()) return result;

    fs::path rdir(dir);
    if (!fs::exists(rdir) || !fs::is_directory(rdir)) {
        std::cerr << "WARNING: runtimeClassDir not found: " << dir << "\n";
        std::cerr << "         Run scripts/build_runtime.ps1 first to compile runtime classes.\n";
        return result;
    }






    static const std::map<std::string, int> LOAD_ORDER = {
        {"mod/runtime/IClassBytecodeProvider",    0},
        {"mod/runtime/NativeBridge",              1},
        {"mod/runtime/NativeScrubberBridge",      2},
        {"mod/runtime/NativeScrubber",            3},
        {"mod/runtime/DllMixinServiceWrapper",    4},
        {"mod/runtime/DllRefmapRemapper",         5},
        {"mod/runtime/AccessWidenerBridge",       6},
        {"mod/runtime/MixinVersionAdapter",       7},
        {"mod/runtime/LoadedClassAdapter$LoaderWriter", 8},
        {"mod/runtime/LoadedClassAdapter",        9},
    };


    std::map<int, RuntimeClassEntry> ordered;
    std::vector<RuntimeClassEntry> unordered;

    for (const auto& entry : fs::recursive_directory_iterator(rdir)) {
        if (!entry.is_regular_file()) continue;
        if (entry.path().extension() != ".class") continue;


        fs::path rel = fs::relative(entry.path(), rdir);
        std::string internalName = rel.generic_string();

        if (internalName.size() > 6)
            internalName = internalName.substr(0, internalName.size() - 6);


        std::ifstream f(entry.path(), std::ios::binary);
        if (!f) {
            std::cerr << "WARNING: cannot read: " << entry.path() << "\n";
            continue;
        }
        std::vector<uint8_t> bytes((std::istreambuf_iterator<char>(f)),
                                    std::istreambuf_iterator<char>());
        if (bytes.empty()) continue;


        if (bytes.size() < 4 || bytes[0] != 0xCA || bytes[1] != 0xFE
                             || bytes[2] != 0xBA || bytes[3] != 0xBE) {
            std::cerr << "WARNING: not a valid .class file: " << internalName << "\n";
            continue;
        }

        RuntimeClassEntry rce;
        rce.internalName = internalName;
        rce.bytecode     = std::move(bytes);

        auto it = LOAD_ORDER.find(internalName);
        if (it != LOAD_ORDER.end()) {
            ordered[it->second] = std::move(rce);
        } else {

            unordered.push_back(std::move(rce));
        }
    }


    for (auto& [priority, rce] : ordered) {
        result.push_back(std::move(rce));
    }
    for (auto& rce : unordered) {
        result.push_back(std::move(rce));
    }

    std::cout << "G8: loaded " << result.size() << " runtime classes from " << dir << "\n";
    for (const auto& rce : result) {
        std::cout << "  [G8] " << rce.internalName << " ("
                  << rce.bytecode.size() << " bytes)\n";
    }
    return result;
}




static std::string computeFileSha256(const std::string& path) {

#ifdef _WIN32
    char buf[256];
    std::string cmd = "certutil -hashfile \"" + path + "\" SHA256";
    FILE* p = _popen(cmd.c_str(), "r");
    if (!p) return "";
    std::string result;
    while (fgets(buf, sizeof(buf), p)) result += buf;
    _pclose(p);

    size_t start = result.find('\n');
    if (start == std::string::npos) return "";
    start++;
    size_t end = result.find('\n', start);
    std::string hash = result.substr(start, end == std::string::npos ? std::string::npos : end - start);

    hash.erase(std::remove_if(hash.begin(), hash.end(), ::isspace), hash.end());

    std::transform(hash.begin(), hash.end(), hash.begin(), ::tolower);
    return hash;
#else
    char buf[256];
    std::string cmd = "sha256sum \"" + path + "\"";
    FILE* p = popen(cmd.c_str(), "r");
    if (!p) return "";
    std::string result;
    if (fgets(buf, sizeof(buf), p)) result = buf;
    pclose(p);
    size_t space = result.find(' ');
    return space != std::string::npos ? result.substr(0, space) : result;
#endif
}


static std::string jsonEscape(const std::string& s) {
    std::string out;
    for (char c : s) {
        if (c == '"')  out += "\\\"";
        else if (c == '\\') out += "\\\\";
        else if (c == '\n') out += "\\n";
        else if (c == '\r') out += "\\r";
        else if (c == '\t') out += "\\t";
        else out += c;
    }
    return out;
}
static std::string jstr(const std::string& k, const std::string& v, int indent=2) {
    return std::string(indent, ' ') + "\"" + k + "\": \"" + jsonEscape(v) + "\"";
}
static std::string jint(const std::string& k, int v, int indent=2) {
    return std::string(indent, ' ') + "\"" + k + "\": " + std::to_string(v);
}


struct BuildResult {
    bool success = false;
    std::string error;
    std::string payloadPath;
    std::string carrierPath;
    std::string payloadSha256;
    std::string carrierSha256;
    std::string cppPath;
    int classCount = 0;
    int resourceCount = 0;
    int mixinConfigCount = 0;
    int refmapCount = 0;
    int accessWidenerCount = 0;
    int nativeMethodCount = 0;
    std::vector<std::string> entrypoints;
    std::vector<std::string> mixinConfigs;
    std::vector<std::string> fallbackMixinConfigs;
    std::string buildDate;
    std::string platform;
    std::string mcVersion;
};


BuildResult build(const std::string& jarPath,
                  const std::string& outputDir,
                  const std::string& jdkPath,
                  const std::string& runtimeClassDir,
                  bool verbose) {
    BuildResult result;
    fs::create_directories(outputDir);


    {
        time_t t = time(nullptr);
        char buf[32];
        strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", gmtime(&t));
        result.buildDate = buf;
    }


    Jar jar;
    if (!jar.open(jarPath)) {
        result.error = "Cannot open JAR: " + jarPath;
        return result;
    }

    if (!jar.isValidMod()) {
        result.error = "Not a valid Fabric/Forge mod JAR (no fabric.mod.json)";
        return result;
    }

    std::string mcVersion;
    result.platform = jar.getPlatform(mcVersion);
    result.mcVersion = mcVersion;

    auto classes     = jar.getClasses();
    auto resources   = jar.getResources();
    auto entrypoints = jar.getFabricEntrypoints();
    auto mixinInfos  = jar.getMixinConfigInfos();
    auto refmaps     = jar.getRefmaps();
    auto accessWideners = jar.getAccessWideners();
    auto nativeMethods  = jar.getNativeMethods();

    result.classCount        = (int)classes.size();
    result.resourceCount     = (int)resources.size();
    result.mixinConfigCount  = (int)mixinInfos.size();
    result.refmapCount       = (int)refmaps.size();
    result.accessWidenerCount= (int)accessWideners.size();
    result.nativeMethodCount = (int)nativeMethods.size();

    for (const auto& ep : entrypoints)
        result.entrypoints.push_back(ep.type + ":" + ep.className);

    for (const auto& mi : mixinInfos) {
        if (mi.fromFabricModJson) result.mixinConfigs.push_back(mi.configPath);
        else result.fallbackMixinConfigs.push_back(mi.configPath + " [file scan]");
    }

    if (verbose) {
        std::cout << "JAR: " << jarPath << "\n";
        std::cout << "Platform: " << result.platform << " MC=" << mcVersion << "\n";
        std::cout << "Classes: " << result.classCount << "\n";
        std::cout << "Resources: " << result.resourceCount << "\n";
        std::cout << "MixinConfigs: " << result.mixinConfigCount
                  << " (" << result.fallbackMixinConfigs.size() << " from file scan)\n";
        std::cout << "Refmaps: " << result.refmapCount << "\n";
        std::cout << "AccessWideners: " << result.accessWidenerCount << "\n";
        std::cout << "NativeMethods: " << result.nativeMethodCount << "\n";
        std::cout << "Entrypoints: " << entrypoints.size() << "\n";
    }


    if (!nativeMethods.empty()) {
        std::cout << "WARNING: " << nativeMethods.size()
                  << " native method(s) found — they must be bound in generated DLL:\n";
        for (const auto& nm : nativeMethods)
            std::cout << "  " << nm.ownerClass << "." << nm.methodName << nm.descriptor << "\n";
    }


    CodeGenConfig cfg;
    cfg.enableUnload       = true;
    cfg.enableLogging      = true;



    cfg.runtimeClassDir = runtimeClassDir;
    cfg.runtimeClasses  = loadRuntimeClasses(runtimeClassDir);
    if (cfg.runtimeClasses.empty()) {
        std::cout << "G8 WARNING: No pre-compiled runtime classes found.\n";
        std::cout << "  Build them with: scripts/build_runtime.ps1\n";
        std::cout << "  Expected dir: " << runtimeClassDir << "\n";
    }
    cfg.fabricEntrypoints  = entrypoints;
    cfg.mixinConfigInfos   = mixinInfos;
    cfg.resources          = resources;
    cfg.refmaps            = refmaps;
    cfg.accessWideners     = accessWideners;
    cfg.nativeMethods      = nativeMethods;

    for (const auto& mi : mixinInfos) cfg.mixinConfigs.push_back(mi.configPath);

    CodeGen gen;
    std::string mainClass = jar.getMainClass();
    std::string cppCode   = gen.generate(classes, mainClass, result.platform, cfg);


    result.cppPath = (fs::path(outputDir) / "mod_payload.cpp").string();
    {
        std::ofstream out(result.cppPath);
        if (!out) { result.error = "Cannot write " + result.cppPath; return result; }
        out << cppCode;
    }
    if (verbose) std::cout << "Generated: " << result.cppPath << "\n";


    std::string cmakePath = (fs::path(outputDir) / "CMakeLists.txt").string();
    {
        std::ofstream out(cmakePath);
        out << "cmake_minimum_required(VERSION 3.20)\n";
        out << "project(ModPayload)\n";
        out << "set(CMAKE_CXX_STANDARD 20)\n";
        out << "set(CMAKE_MSVC_RUNTIME_LIBRARY \"MultiThreaded\")\n";
        out << "add_library(mod_payload SHARED mod_payload.cpp)\n";
        out << "target_include_directories(mod_payload PRIVATE\n";
        if (!jdkPath.empty()) {
            std::string cmakeJdkPath = jdkPath;
            std::replace(cmakeJdkPath.begin(), cmakeJdkPath.end(), '\\', '/');
            out << "    \"" << cmakeJdkPath << "/include\"\n"
                << "    \"" << cmakeJdkPath << "/include/win32\"\n";
        }
        out << ")\n";
        out << "set_target_properties(mod_payload PROPERTIES\n";
        out << "    OUTPUT_NAME \"mod-payload\"\n";
        out << "    SUFFIX \".dll\"\n";
        out << ")\n";
        out << "target_link_options(mod_payload PRIVATE\n";
        out << "    /SUBSYSTEM:WINDOWS\n";
        out << "    /DYNAMICBASE /HIGHENTROPYVA /NXCOMPAT\n";
        out << ")\n";

        out << "target_link_options(mod_payload PRIVATE /EXPORT:ManualMain)\n";
    }


    std::string resIndexPath = (fs::path(outputDir) / "resource-index.json").string();
    {
        std::ofstream out(resIndexPath);
        out << "[\n";
        for (size_t i = 0; i < resources.size(); i++) {
            const auto& r = resources[i];
            out << "  {\"path\": \"" << jsonEscape(r.name)
                << "\", \"size\": " << r.data.size()
                << ", \"isClass\": " << (r.isClass ? "true" : "false")
                << "}";
            if (i + 1 < resources.size()) out << ",";
            out << "\n";
        }

        for (size_t i = 0; i < refmaps.size(); i++) {
            out << "  ,{\"path\": \"" << jsonEscape(refmaps[i].resourcePath)
                << "\", \"size\": " << refmaps[i].rawBytes.size()
                << ", \"type\": \"refmap\"}\n";
        }

        for (size_t i = 0; i < accessWideners.size(); i++) {
            out << "  ,{\"path\": \"" << jsonEscape(accessWideners[i].resourcePath)
                << "\", \"size\": " << accessWideners[i].rawBytes.size()
                << ", \"namespace\": \"" << jsonEscape(accessWideners[i].namespace_)
                << "\", \"type\": \"accesswidener\"}\n";
        }
        out << "]\n";
    }


    std::string mixinIndexPath = (fs::path(outputDir) / "mixin-index.json").string();
    {
        std::ofstream out(mixinIndexPath);
        out << "[\n";
        for (size_t i = 0; i < mixinInfos.size(); i++) {
            const auto& mi = mixinInfos[i];
            out << "  {\n";
            out << "    \"configPath\": \"" << jsonEscape(mi.configPath) << "\",\n";
            out << "    \"package\": \"" << jsonEscape(mi.packageName) << "\",\n";
            out << "    \"required\": " << (mi.required ? "true" : "false") << ",\n";
            out << "    \"minVersion\": \"" << jsonEscape(mi.minVersion) << "\",\n";
            out << "    \"compatibilityLevel\": \"" << jsonEscape(mi.compatibilityLevel) << "\",\n";
            out << "    \"refmap\": \"" << jsonEscape(mi.refmap) << "\",\n";
            out << "    \"plugin\": \"" << jsonEscape(mi.plugin) << "\",\n";
            out << "    \"fromFabricModJson\": " << (mi.fromFabricModJson ? "true" : "false") << ",\n";
            out << "    \"injectors\": {\"defaultRequire\": " << mi.injectors.defaultRequire << "},\n";
            out << "    \"overwrites\": {\"requireAnnotations\": "
                << (mi.overwrites.requireAnnotations ? "true" : "false") << "},\n";
            out << "    \"commonMixins\": [";
            for (size_t j = 0; j < mi.commonMixins.size(); j++) {
                if (j) out << ", ";
                out << "\"" << jsonEscape(mi.commonMixins[j]) << "\"";
            }
            out << "],\n";
            out << "    \"clientMixins\": [";
            for (size_t j = 0; j < mi.clientMixins.size(); j++) {
                if (j) out << ", ";
                out << "\"" << jsonEscape(mi.clientMixins[j]) << "\"";
            }
            out << "],\n";
            out << "    \"serverMixins\": [";
            for (size_t j = 0; j < mi.serverMixins.size(); j++) {
                if (j) out << ", ";
                out << "\"" << jsonEscape(mi.serverMixins[j]) << "\"";
            }
            out << "]\n";
            out << "  }";
            if (i + 1 < mixinInfos.size()) out << ",";
            out << "\n";
        }
        out << "]\n";
    }




    auto writeLoaderAbi = [&](const std::string& payloadHash, const std::string& carrierHash) {
        std::string abiPath = (fs::path(outputDir) / "loader-abi.json").string();
        std::ofstream out(abiPath);
        out << "{\n";
        out << "  \"version\": 1,\n";
        out << "  \"buildDate\": \"" << result.buildDate << "\",\n";
        out << "  \"platform\": \"" << jsonEscape(result.platform) << "\",\n";
        out << "  \"mcVersion\": \"" << jsonEscape(result.mcVersion) << "\",\n";
        out << "  \"payloadSha256\": \"" << payloadHash << "\",\n";
        out << "  \"carrierSha256\": \"" << carrierHash << "\",\n";
        out << "  \"loaderParamsSize\": 112,\n";
        out << "  \"loaderParamsVersion\": 1,\n";
        out << "  \"connectThunkOffset\": " << 0x500 << ",\n";
        out << "  \"winHttpConnectThunkOffset\": " << 0x580 << ",\n";
        out << "  \"assetServerPort\": 8080,\n";
        out << "  \"entrypoints\": [";
        for (size_t i = 0; i < result.entrypoints.size(); i++) {
            if (i) out << ", ";
            out << "\"" << jsonEscape(result.entrypoints[i]) << "\"";
        }
        out << "],\n";
        out << "  \"mixinConfigs\": [";
        for (size_t i = 0; i < result.mixinConfigs.size(); i++) {
            if (i) out << ", ";
            out << "\"" << jsonEscape(result.mixinConfigs[i]) << "\"";
        }
        out << "],\n";
        out << "  \"fallbackMixinConfigs\": [";
        for (size_t i = 0; i < result.fallbackMixinConfigs.size(); i++) {
            if (i) out << ", ";
            out << "\"" << jsonEscape(result.fallbackMixinConfigs[i]) << "\"";
        }
        out << "],\n";
        out << "  \"nativeMethodCount\": " << result.nativeMethodCount << "\n";
        out << "}\n";
        return abiPath;
    };


    writeLoaderAbi("", "");


    auto writeReport = [&](const std::string& payloadHash, const std::string& carrierHash) {
        std::string reportPath = (fs::path(outputDir) / "build-report.md").string();
        std::ofstream out(reportPath);
        out << "# Build Report\n\n";
        out << "**Date:** " << result.buildDate << "\n\n";
        out << "## Input\n\n";
        out << "- JAR: `" << jarPath << "`\n";
        out << "- Platform: " << result.platform << " / MC " << mcVersion << "\n\n";
        out << "## Classes\n\n";
        out << "- Total: " << result.classCount << "\n\n";
        out << "## Mixin Configs\n\n";
        for (const auto& c : result.mixinConfigs)
            out << "- `" << c << "` (from fabric.mod.json)\n";
        for (const auto& c : result.fallbackMixinConfigs)
            out << "- `" << c << "` ⚠️ non-parity fallback\n";
        out << "\n## Refmaps\n\n";
        for (const auto& r : refmaps)
            out << "- `" << r.resourcePath << "` (" << r.rawBytes.size() << " bytes)\n";
        out << "\n## AccessWideners\n\n";
        for (const auto& a : accessWideners)
            out << "- `" << a.resourcePath << "` namespace=`" << a.namespace_ << "`\n";
        out << "\n## Native Methods\n\n";
        if (nativeMethods.empty()) {
            out << "_None._\n";
        } else {
            out << "⚠️ **" << nativeMethods.size() << " native method(s) require binding:**\n\n";
            for (const auto& nm : nativeMethods)
                out << "- `" << nm.ownerClass << "." << nm.methodName << nm.descriptor << "`\n";
        }
        out << "\n## Entrypoints\n\n";
        for (const auto& ep : result.entrypoints)
            out << "- `" << ep << "`\n";
        out << "\n## Output Hashes\n\n";
        out << "- payload sha256: `" << (payloadHash.empty() ? "(not yet built)" : payloadHash) << "`\n";
        out << "- carrier sha256: `" << (carrierHash.empty() ? "(not yet built)" : carrierHash) << "`\n";
        out << "\n## Parity Status\n\n";
        if (result.fallbackMixinConfigs.empty())
            out << "✅ All mixin configs from fabric.mod.json (primary path)\n";
        else
            out << "⚠️ Some mixin configs from file scan (fallback — non-parity mode)\n";
        if (nativeMethods.empty())
            out << "✅ No unbound native methods\n";
        else
            out << "⚠️ " << nativeMethods.size() << " native method(s) need explicit binding\n";
        return reportPath;
    };

    writeReport("", "");

    result.success = true;
    if (verbose) {
        std::cout << "Manifests written:\n";
        std::cout << "  resource-index.json\n";
        std::cout << "  mixin-index.json\n";
        std::cout << "  loader-abi.json (hashes pending DLL build)\n";
        std::cout << "  build-report.md\n";
    }

    return result;
}


bool Builder::run(const BuildArgs& args) {
    auto result = build(args.jarPath, args.outputDir, args.jdkPath, args.runtimeClassDir, args.verbose);
    if (!result.success) {
        std::cerr << "Build failed: " << result.error << "\n";
        return false;
    }

    std::cout << "Build complete.\n";
    std::cout << "  Classes:        " << result.classCount << "\n";
    std::cout << "  Resources:      " << result.resourceCount << "\n";
    std::cout << "  MixinConfigs:   " << result.mixinConfigCount << "\n";
    std::cout << "  Refmaps:        " << result.refmapCount << "\n";
    std::cout << "  AccessWideners: " << result.accessWidenerCount << "\n";
    std::cout << "  NativeMethods:  " << result.nativeMethodCount << "\n";
    std::cout << "  Entrypoints:    " << result.entrypoints.size() << "\n";
    std::cout << "  CPP:            " << result.cppPath << "\n";
    return true;
}
