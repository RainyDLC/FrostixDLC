#include "core/builder.h"

#include <cstdlib>
#include <iostream>
#include <string>

static void printUsage() {
    std::cerr
        << "Usage: jar-to-dll --jar <mod.jar> --output <dir> "
        << "[--jdk <jdk path>] [--runtime-class-dir <dir>] [--verbose]\n";
}

int main(int argc, char** argv) {
    BuildArgs args;

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        auto requireValue = [&](const char* name) -> const char* {
            if (i + 1 >= argc) {
                std::cerr << "Missing value for " << name << "\n";
                printUsage();
                std::exit(2);
            }
            return argv[++i];
        };

        if (arg == "--jar") {
            args.jarPath = requireValue("--jar");
        } else if (arg == "--output") {
            args.outputDir = requireValue("--output");
        } else if (arg == "--jdk") {
            args.jdkPath = requireValue("--jdk");
        } else if (arg == "--runtime-class-dir") {
            args.runtimeClassDir = requireValue("--runtime-class-dir");
        } else if (arg == "--verbose") {
            args.verbose = true;
        } else if (arg == "--help" || arg == "-h") {
            printUsage();
            return 0;
        } else {
            std::cerr << "Unknown argument: " << arg << "\n";
            printUsage();
            return 2;
        }
    }

    if (args.jarPath.empty() || args.outputDir.empty()) {
        printUsage();
        return 2;
    }

    Builder builder;
    return builder.run(args) ? 0 : 1;
}
