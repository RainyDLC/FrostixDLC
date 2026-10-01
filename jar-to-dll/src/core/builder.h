#pragma once
#include <string>
#include <vector>

struct BuildArgs {
    std::string jarPath;
    std::string outputDir;
    std::string jdkPath;




    std::string runtimeClassDir;
    bool verbose = false;
};

class Builder {
public:
    bool run(const BuildArgs& args);
};
