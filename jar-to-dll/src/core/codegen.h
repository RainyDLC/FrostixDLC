#pragma once
#include <string>
#include <vector>
#include <map>
#include <random>
#include "jar.h"

struct RuntimeClassEntry {
    std::string internalName;
    std::vector<uint8_t> bytecode;
};

struct CodeGenConfig {
    bool enableUnload  = true;
    bool enableLogging = true;
    std::vector<FabricEntrypoint>   fabricEntrypoints;
    std::vector<MixinConfigInfo>    mixinConfigInfos;
    std::vector<JarEntry>           resources;
    std::vector<RefmapInfo>         refmaps;
    std::vector<AccessWidenerInfo>  accessWideners;
    std::vector<NativeMethodInfo>   nativeMethods;
    std::vector<std::string>        mixinConfigs;




    std::vector<RuntimeClassEntry>  runtimeClasses;



    std::string runtimeClassDir;
};

class CodeGen {
public:
    CodeGen();
    std::string generate(const std::vector<JarEntry>& classes,
                         const std::string& entryPoint,
                         const std::string& platform,
                         const CodeGenConfig& config);

private:
    std::mt19937_64 rng;
    std::string rndName(int minLen = 8, int maxLen = 16);

    void emitResourceTable(std::stringstream& cpp,
                           const CodeGenConfig& config,
                           std::map<std::string, std::string>& rVar);
    void emitNativeBridge(std::stringstream& cpp);
    void emitMixinServiceWrapper(std::stringstream& cpp);
    void emitActiveMixinConnector(std::stringstream& cpp,
                                  const CodeGenConfig& config);
    void emitAccessWidenerBridge(std::stringstream& cpp,
                                 const CodeGenConfig& config);
    void emitRefmapIntegration(std::stringstream& cpp,
                               const CodeGenConfig& config);
    void emitClassByteCache(std::stringstream& cpp);
    void emitBootstrapSequence(std::stringstream& cpp,
                               const std::vector<JarEntry>& classes,
                               const std::vector<std::pair<std::string,std::string>>& entryClasses,
                               const CodeGenConfig& config,
                               const std::map<std::string, std::string>& cVar);
    void emitTraceCounters(std::stringstream& cpp);


    void emitRuntimeClassArrays(std::stringstream& cpp,
                                const CodeGenConfig& config,
                                std::map<std::string, std::string>& rtVar);
};
