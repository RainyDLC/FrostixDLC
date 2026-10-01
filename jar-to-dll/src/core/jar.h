#pragma once
#include <string>
#include <vector>
#include <fstream>
#include <cstdint>
#include <set>
#pragma pack(push, 1)

struct ZipLocal {
    uint32_t sig;
    uint16_t ver, flags, method, time, date;
    uint32_t crc, compSize, uncompSize;
    uint16_t nameLen, extraLen;
};
struct ZipCentral {
    uint32_t sig;
    uint16_t verMade, verNeed, flags, method, time, date;
    uint32_t crc, compSize, uncompSize;
    uint16_t nameLen, extraLen, commentLen, diskStart, intAttr;
    uint32_t extAttr, offset;
};
struct ZipEnd {
    uint32_t sig;
    uint16_t diskNum, diskStart, entriesOnDisk, totalEntries;
    uint32_t centralSize, centralOffset;
    uint16_t commentLen;
};

#pragma pack(pop)

struct ZipFile {
    std::string name;
    uint32_t compSize, uncompSize;
    uint16_t method;
    uint32_t offset;
};

struct JarEntry {
    std::string name;
    std::vector<uint8_t> data;
    bool isClass;
    int loadPriority = 0;
    std::string superClass;
    std::set<std::string> interfaces;
    bool isModInitializer = false;
    bool isClientModInitializer = false;
};

struct FabricEntrypoint {
    std::string className;
    std::string type;
    std::string adapter;
};


struct MixinInjectorConfig {
    int defaultRequire = 0;
};
struct MixinOverwriteConfig {
    bool requireAnnotations = false;
};
struct MixinConfigInfo {
    std::string configPath;
    std::string packageName;
    std::vector<std::string> commonMixins;
    std::vector<std::string> clientMixins;
    std::vector<std::string> serverMixins;
    std::string refmap;
    std::string plugin;
    bool required = false;
    std::string minVersion;
    std::string compatibilityLevel;
    MixinInjectorConfig injectors;
    MixinOverwriteConfig overwrites;
    std::vector<uint8_t> rawJsonBytes;
    int orderIndex = 0;
    bool fromFabricModJson = false;
    bool fromFileScan = false;
};


struct NativeMethodInfo {
    std::string ownerClass;
    std::string methodName;
    std::string descriptor;
};


struct RefmapInfo {
    std::string resourcePath;
    std::vector<uint8_t> rawBytes;
};


struct AccessWidenerInfo {
    std::string resourcePath;
    std::vector<uint8_t> rawBytes;
    std::string namespace_;
};

class Jar {
public:
    bool open(const std::string& path);
    void close();

    std::vector<JarEntry>    getClasses();
    std::string              getMainClass();
    std::string              getPlatform(std::string& version);
    std::string              getJdkVersion();
    std::vector<FabricEntrypoint> getFabricEntrypoints();
    bool isValidMod();
    bool hasMixins();


    std::vector<MixinConfigInfo>  getMixinConfigInfos();

    std::vector<std::string>      getMixinConfigs();

    std::vector<JarEntry>         getResources();
    std::vector<std::string>      getSkippedClasses();
    int  getClassCount();
    int  getMixinCount();


    std::vector<RefmapInfo>       getRefmaps();


    std::vector<AccessWidenerInfo> getAccessWideners();


    std::vector<NativeMethodInfo>  getNativeMethods();

private:
    std::ifstream file;
    std::vector<ZipFile> files;
    mutable std::vector<JarEntry> cache;
    mutable bool cached = false;
    std::vector<uint8_t> readEntry(const ZipFile& entry);
    std::vector<uint8_t> readByName(const std::string& name);
    bool hasFile(const std::string& name);
    void buildCache();
    bool shouldSkip(const std::string& name);
    void parseClassInfo(JarEntry& entry);
    void sortClassesByDependency();
    static std::vector<uint8_t> inflate(const uint8_t* data, size_t compSize, size_t uncompSize);


    MixinConfigInfo parseMixinConfigJson(const std::string& configPath,
                                          const std::vector<uint8_t>& data,
                                          int orderIndex,
                                          bool fromFabricModJson);

    void scanNativeMethods(const JarEntry& entry, std::vector<NativeMethodInfo>& out);

    std::string parseAccessWidenerNamespace(const std::vector<uint8_t>& data);
};
