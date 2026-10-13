# Ambience and KillEffect

## Black hole runtime fixes

The imported renderer had two separate problems:

1. Minecraft 26.2 uses reversed Z. Unprojecting z=1 minus z=-1 produced backward
   rays on the OpenGL clip range. CPU visibility and all three GPU reconstruction
   sites now unproject a finite z=0.5 point from the rotation-only view projection.
2. Both frequently updated uniform buffers were created with `USAGE_UNIFORM` only.
   Minecraft's actual `CommandEncoder.writeToBuffer` rejects these buffers with
   **Buffer needs USAGE_COPY_DST to be a destination for a copy**. The renderer
   caught that exception, permanently disabled itself until a module reset, and
   Ambience still cancelled the vanilla sky draw. Both the 224-byte sky buffer and
   16-byte bloom buffer now include `USAGE_COPY_DST`. Vanilla sky cancellation now
   happens only after the replacement renderer reports success.

The noise texture also now uses ARGB channel order with `NativeImage.setPixel`,
keeping the red/green shifted noise correlation intact. Shader resources remain
available for pipeline/resource reloads; shader compilation validity is checked.

## Real deaths, not just Preview

Enable **Render → KillEffect**, choose **Демоническая сила** or **Вихрь**.
**Animation Speed** changes duration; **Effect Size** changes VFX size, not player
size. **Preview → Play effect** plays the selected effect three blocks in front of
the local player. Preview requires the module to be enabled.

Remote players do **not** necessarily get health=0 on the observing client:
vanilla `LivingEntity.handleEntityEvent(3)` explicitly skips setting player health.
The old health polling therefore missed real kills even though preview worked.
KillEffect now observes the server's **entity status 3** at the client-thread packet
handler, after its thread guard and before vanilla handles the entity. A complete
skin/position snapshot is captured immediately, before a following removal packet.

The death latch survives stale remote health and rejects duplicate tick/status
notifications. Removal/unload clears only corpse suppression, not the independent
animation; respawning entities may trigger the effect again, even with reused IDs.
Health/death-time polling remains a fallback. Nearby confirmed player deaths trigger
these local cosmetics regardless of which player was credited with the kill. The
local player's real death is excluded. If a custom server sends neither a death
status nor a dead state, ordinary removal is not falsely classified as a kill.

## Cinematics

- **Демоническая сила:** skin snapshot lifts with outstretched arms, then plunges
  into an asymmetric crimson/charcoal stain with burgundy and violet molten veins.
  There is no circular red rim, black disk or copied reference shape. The hand is
  placed on the observer-facing edge, with a rectangular Minecraft-style upper arm and
  forearm. There are no extra fingers, thumb or separate palm. The arm reaches from
  its actual falling pose, plants its end on the lip and holds until 2.85 seconds.
  It then breaks free and drops in 140 ms without smoothstep/ease-out; the stain
  contracts after 3.65 seconds. Total
  duration is 4.6 seconds at speed 1. Ground-plane clipping hides the body without
  changing blocks.
- **Вихрь:** three overlapping helical dust clouds, moving light/dark bands and
  orbiting particles descend over the skin snapshot. The body fades only after
  the dust covers it, then the cloud expands, breaks up and disperses. No opaque
  cone mesh or hard-edged shell.

Skin and slim/wide model are captured, including outer skin layers; armor, held
items and capes are not included. No entity is removed/teleported by this module,
no packets are rewritten/cancelled/sent, and combat logic is unchanged.

Effects are world-depth tested, deterministic, independent of FPS, limited to eight
simultaneous snapshots and 48 blocks. Disconnects/world changes/disable clear state;
snapshots do not retain entity references.

## Assets and checks

`atlas.png.b64` is an original 512×512 RGBA PNG stored losslessly as text for
binary-safe GitHub MCP commits. Four tiles: irregular demonic stain, fractal dust,
ember, soft glow. It is decoded once into a GPU texture. Missing/corrupt assets are
logged and vanilla corpses remain visible. Regenerate with:

```sh
python scripts/generate_killeffect_atlas.py  # Python + Pillow + NumPy
cd RainyDLC
./gradlew compileClientJava vfxRegression vfxGeometryRegression blackHoleBufferRegression
```

- `vfxRegression`: both clip ranges/Z conventions, GPU ray sites, complete animation
  timelines and packet-confirmed death latch/reused-ID behavior.
- `vfxGeometryRegression`: actual deferred callbacks, finite vertices, complete
  quads, corpse clipping and reach/hold/sharp-release geometry samples.
- `blackHoleBufferRegression`: reproduces the old exception on the **real Minecraft
  CommandEncoder**, then verifies both fixed buffer sizes reach the backend.

Optional actual OpenGL shader rendering on headless Linux:

```sh
python -m pip install moderngl numpy pillow
python scripts/check_blackhole_gl.py
```

This compiles and draws all four shipped GLSL stages with an EGL driver and checks
that an accretion image is produced. CPU tests, offline previews and a real-driver
shader render do not replace a Minecraft/server play-test.

For the final distributable, follow `AGENTS.md` and run `./build-client.ps1` on
Windows with JDK 25, Visual Studio 2026 and its CMake tools. A Gradle compile-only
check is not a `LegitBuilder.jar` release build. Source changes require rebuilding
the client; restarting with an old JAR will not load these fixes.
