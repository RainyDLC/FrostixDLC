# Project instructions

## Build the Rainy.fun client

- For any change to `rainy.fun`, build the final deliverable through LegitBuilder by running `rainy.fun/build-client.ps1` (from the repository root: `./rainy.fun/build-client.ps1`).
- This script first builds the Fabric client JAR, then passes it to the repository's root `build.ps1` to produce `LegitBuilder.jar`.
- Do not treat a standalone Gradle build of `rainy.fun` as the final build. Use it only when a task explicitly asks for an intermediate Fabric JAR or a compile-only check; still run the LegitBuilder workflow for the final deliverable when possible.
- If required JDK, CMake, or Visual Studio build tools are unavailable, report the specific blocker. Do not silently substitute a Gradle-only build.
- The final artifacts are `LegitBuilder.jar` and `build/release/LegitBuilder.jar` at the repository root.
