# Chams: procedural materials

Chams uses two custom GLSL 330 stages and five material presets. It still affects player models and, optionally, the first-person arm. The HUD and ClickGUI styling are unchanged.

## Presets

| Shader | Appearance |
| --- | --- |
| Aurora (default) | Flowing, layered curtains between the primary and secondary colors |
| Prism | Angle-dependent iridescence with moving caustic bands |
| Hologram | Anti-aliased scanlines, a fine lattice and a traveling scan wave |
| Nebula | Three-octave, domain-warped clouds and gently twinkling stars |
| Classic | Conventional skin tint without a procedural pattern or rim light |

The rim light brightens the material near grazing viewing angles; it is **not** a post-process bloom or an expanded outline. There is no additional framebuffer, texture download or ray-marching pass.

## Settings

- **Depth**: Through walls / Visible only. The latter takes the comparison operation from Minecraft's entity-translucent pipeline (including its current reverse-depth convention). Neither mode writes depth.
- **Color**: primary material color; its alpha controls overall transparency.
- **Secondary color**: secondary RGB for the procedural effects. Its alpha is deliberately unused; use Color alpha for opacity.
- **Effect strength**: blend from a quieter tinted base to the procedural pattern.
- **Rim light**: intensity of view-dependent surface rim lighting (non-Classic presets).
- **Skin detail**: retain the original skin's RGB detail while always preserving its UV coordinates and transparent cutouts.
- **Pattern scale**: density of the procedural pattern.
- **Animation speed**: smoothly accumulated animation speed; zero freezes both the pattern and pulse.
- **Pulse / Pulse speed**: gentle brightness modulation. The existing setting names are retained for saved configurations.
- **First person arm**: apply the same shader/color/settings to the hand; no separate hard-coded hand tint.

Only two pipelines exist, one per depth mode. Switching presets or changing sliders changes uniforms, not the number of compiled programs. Render types remain cached per skin and depth mode. Back-face culling avoids double-sided translucent skin noise. Vanilla entity tint/translucency, hurt overlays and distance fog are retained; Chams no longer overwrites the model tint. Fully hidden entities that the vanilla renderer does not expose as body-visible/translucent are not forced visible by this hook.

## Shader delivery and lifecycle

`ChamsShaderSources` contains self-contained vertex/fragment sources, with no `#moj_import` and no dependency on Minecraft discovering loose shader files in a late-injected JAR. `ChamsShaderCacheMixin` intercepts only `hatek:core/chams_material` at `ShaderManager$CompilationCache.getShaderSource`.

This lower-level lookup matters: resource reload precompiles registered pipelines against a **temporary** CompilationCache, bypassing `ShaderManager.getShader`. Both the ordinary draw lookup and that reload path therefore receive the embedded sources. Vanilla shader IDs are unaffected. GPU program creation, caching and disposal remain managed by Minecraft; actual reload and late-injection behavior still require an in-game check.

The engine already sends `DynamicTransforms.TextureMat` with an entity draw. Our render setup provides a dedicated TextureTransform supplier, and our vertex shader uses UV0 unchanged, so this matrix is safely reused as a parameter payload for this pipeline only:

| GLSL column | Contents |
| --- | --- |
| TextureMat[0] | Primary RGBA |
| TextureMat[1] | Secondary RGB, effect strength |
| TextureMat[2] | Animation time, preset index, pattern scale, skin detail |
| TextureMat[3] | Rim intensity, pulse multiplier, two reserved zeros |

The CPU packing class is `ChamsUniforms`. Do not add `APPLY_TEXTURE_MATRIX` or apply TextureMat to UVs in this pipeline.

## Checks

### Pipeline initialization regression

From `RainyDLC`, run `./gradlew chamsPipelineRegression` (`gradlew.bat chamsPipelineRegression` on Windows). This runs 11 checks against the **real Minecraft API**, including creation of both Chams render types, their vertex/fragment IDs, caching and depth behavior. It does not create an OpenGL context or validate an in-game draw.

Minecraft 26.2's `RenderPipeline.Builder.withVertexShader(String)` and `withFragmentShader(String)` treat the argument as a path in the default `minecraft` namespace. Passing `hatek:core/chams_material` to those overloads produces the invalid ID `minecraft:hatek:core/chams_material` and crashes during static initialization. `ChamsRenderType` therefore parses the full ID explicitly and uses the **Identifier overload** for both stages. The regression also reproduces and rejects the erroneous String usage, so a Java-only compile or GLSL-only preview cannot hide this failure again.

Standalone parameter test (JOML 1.10.8 is already a Minecraft/Gradle dependency):

```sh
javac -cp "$JOML_JAR" -d build/chams-tests RainyDLC/src/client/java/dev/hatek/client/render/ChamsUniforms.java tests/ChamsUniformsRegression.java
java -cp "build/chams-tests:$JOML_JAR" dev.hatek.client.render.ChamsUniformsRegression
```

On Windows use `;` instead of `:` in the Java classpath. This test covers 25 checks, including actual column-major GPU array offsets, color opacity, preset indices and invalid parameter bounds.

Shader source contract test/dump (no Minecraft dependency):

```sh
javac -d build/chams-tests RainyDLC/src/client/java/dev/hatek/client/render/ChamsShaderSources.java tests/ChamsShaderSourcesRegression.java
java -cp build/chams-tests dev.hatek.client.render.ChamsShaderSourcesRegression build/chams-shaders
```

This performs 12 source-contract checks and optionally writes the exact embedded stages to `chams.vert` and `chams.frag`. If glslangValidator is installed, validate those desktop GLSL stages with `glslangValidator -l build/chams-shaders/chams.vert build/chams-shaders/chams.frag`.

Development checks performed in Linux:

- Both Java regression tests passed (37 checks total).
- Intermediate Fabric `build -x test` passed with JDK 25.
- The actual shader source pair, with only the version/precision header adapted to GLSL ES 3.00, compiled, linked and drew all five presets on a neutral cuboid test model in WebGL2/SwiftShader. Pixel comparison at two times confirmed animation in the four procedural presets and a static Classic preset with pulse disabled. This is **not** an in-game Minecraft test or a desktop OpenGL driver compatibility guarantee.
- The material comparison was visually inspected on that test model.

The final deliverable remains `LegitBuilder.jar`, built on Windows through `./build-client.ps1`. The Linux environment lacks PowerShell, Windows and MSVC, so that workflow could not run; a Fabric-only JAR is not substituted as the final deliverable.

### Required in-game verification

1. Test every preset on default/slim skins, custom skins with transparent outer layers, the first-person arm and several players at once.
2. Compare Through walls and Visible only with a solid wall; confirm terrain depth rendering is unaffected.
3. Adjust both colors, primary alpha, strength, scale, skin detail and rim while playing. Test animation speed zero and pulse on/off.
4. Check damage overlays, invisibility/translucency, fog and player movement at short/long distances.
5. Disable Chams: verify vanilla models and the arm return immediately. Re-enable it and reload resources (F3+T) with Chams both enabled and disabled.
6. Test initial Fabric launch and LegitBuilder late injection, rejoin a world/server, and monitor shader compilation/Mixin errors and FPS.
