# Ambience and KillEffect

## Black hole port fix

Minecraft 26.2 builds its rendering projection with **reversed Z** and the device's
clip-depth convention. The port unprojected z=1 and z=-1 and subtracted them as
though this were an ordinary OpenGL projection. On the OpenGL clip range, both the ray marcher and CPU visibility test
consequently pointed behind the camera. On [0,1] backends, z=-1 was outside the
valid clip range.

All three GPU reconstruction sites (march, history resolve, composite) and the CPU
visibility test now unproject a finite z=0.5 point from the rotation-only view
projection. This gives the same forward ray for normal/reversed Z and [-1,1]/[0,1]
clip ranges. The unused depth sampler is removed: this is a sky pass before world
geometry, not a post-world overlay. Shader compilation validity is checked and
shader resources are shipped for pipeline/resource reloads.

## KillEffect

Enable **Render → KillEffect**, choose **Демоническая сила** or **Вихрь**.
**Animation Speed** changes duration; **Effect Size** changes VFX size, not player
size. **Preview → Play effect** plays the selected cinematic three blocks in front
of the local player. The module must be enabled for preview.

- **Демоническая сила:** skin snapshot lifts with outstretched arms; a glowing,
  engraved aperture opens; the body plunges, one arm reaches the rim, pauses,
  slides inward and down, and the aperture closes. Geometry is clipped to the
  ground plane rather than modifying blocks.
- **Вихрь:** three textured helical dust clouds and orbiting particles descend over the skin
  snapshot. The body fades only after the dust reaches it; the funnel then expands
  and disperses.

Nearby player deaths trigger the effects (not just kills credited to the local
player). Detection requires the client to observe a dead player: removal without a
death state does not manufacture a kill. The local player's real death is excluded.
The corpse's skin and slim/wide model are captured, including skin outer layers;
armor, equipment and capes are not part of this cosmetic snapshot. The original
corpse is suppressed only for captured deaths; no entity is removed or teleported,
and no packets or combat logic are changed.

Effects are local, depth-tested, deterministic and independent of FPS. They are
limited to eight simultaneous snapshots and 48 blocks. World changes, disconnects,
respawns and disabling the module clear death latches. Animation data does not hold
entity references after their removal.

## Assets and checks

`atlas.png.b64` is a lossless original 512×512 RGBA PNG, text-encoded for binary-safe
GitHub MCP commits. It contains four 256×256 tiles: molten aperture/engraved sigil,
fractal dust, ember and soft glow. It is decoded once into a GPU texture. This is a
shipped texture asset, not a runtime procedural substitute. Missing/corrupt assets
are logged and vanilla corpses remain visible. Regenerate with:

```sh
python scripts/generate_killeffect_atlas.py  # Python + Pillow + NumPy
cd RainyDLC
./gradlew compileClientJava vfxRegression vfxGeometryRegression
```

The regression covers forward CPU rays for both clip ranges and both Z conventions,
reproduces the old reversed-Z failure, checks GPU ray sites, and samples all timeline
phases for bounded/finite output. It does not replace in-game visual verification.

For the final distributable, follow `AGENTS.md` and run `./build-client.ps1` on
Windows with JDK 25, Visual Studio 2026 and its CMake tools. A Gradle compile-only
check is not a `LegitBuilder.jar` release build.
