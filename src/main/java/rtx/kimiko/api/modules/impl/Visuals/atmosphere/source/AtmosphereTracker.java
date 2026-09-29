package rtx.kimiko.api.modules.impl.Visuals.atmosphere.source;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.AtmosphereConfig;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.sun.SunState;
import rtx.kimiko.api.modules.impl.Visuals.atmosphere.util.MathUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks all optical light sources in the scene (Sun, Moon, Lightning, Beacons, Portals, End Crystals, Explosions).
 * Performs optimized frustum projections, line-of-sight occlusion raycasts, and smoothed light intensity transitions.
 */
public final class AtmosphereTracker {

    private static final double CELESTIAL_RAY_LENGTH = 256.0;
    private static final float EDGE_FADE_START = 1.0f;
    private static final float EDGE_FADE_END = 1.35f;

    // Smoothed visibility levels for smooth fade-in / fade-out
    private float sunVisibility;
    private float sunLastX, sunLastY, sunLastAlignment;

    private float moonVisibility;
    private float moonLastX, moonLastY, moonLastAlignment;

    private float beaconVisibility;
    private float beaconLastX, beaconLastY, beaconLastAlignment;
    private int beaconLastColor = 0x64FFFF;

    private float portalVisibility;
    private float portalLastX, portalLastY, portalLastAlignment;
    private boolean portalIsEnd;

    private float crystalVisibility;
    private float crystalLastX, crystalLastY, crystalLastAlignment;

    // Fast-acting flash states
    private float lightningTimer;
    private Vec3d lastLightningPos;

    // Explosions queue
    private static record ActiveExplosion(Vec3d pos, long startTime) {}
    private final List<ActiveExplosion> explosions = new ArrayList<>();

    // Throttled world scanning caches
    private long lastScanTime;
    private static record CachedBeacon(BlockPos pos, int color) {}
    private final List<CachedBeacon> cachedBeacons = new ArrayList<>();

    private static record CachedPortal(BlockPos pos, boolean isEnd) {}
    private final List<CachedPortal> cachedPortals = new ArrayList<>();

    private long lastFrameNanos = System.nanoTime();

    public record TrackingResult(List<FlareSource> sources, float dirtIllumination, SunState sunState) {}

    public TrackingResult update(MinecraftClient mc, float tickDelta, int width, int height, AtmosphereConfig cfg) {
        ClientWorld world = mc.world;
        PlayerEntity player = mc.player;
        if (world == null || player == null) {
            return new TrackingResult(List.of(), 0f, SunState.HIDDEN);
        }

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();
        float fov = mc.options.getFov().getValue().floatValue();
        float dt = frameSeconds();
        boolean underwater = camera.getSubmersionType() != CameraSubmersionType.NONE;

        List<FlareSource> sources = new ArrayList<>();
        float totalDirt = 0f;

        // 1. SUN
        float skyAngle = camera.getEnvironmentAttributeInterpolator().get(
                net.minecraft.world.attribute.EnvironmentAttributes.SUN_ANGLE_VISUAL, tickDelta) * 0.017453292f;
        Vector3f sunDir = new Vector3f((float) -Math.sin(skyAngle), (float) Math.cos(skyAngle), 0f);
        Projection sunProj = projectDirection(camera, sunDir, fov, width, height);

        float sunTarget = 0f;
        if (cfg.sunFlare() && !underwater && sunProj.inFront()) {
            sunLastX = sunProj.x();
            sunLastY = sunProj.y();
            sunLastAlignment = sunProj.alignment();
            float horizon = MathUtil.smoothstep(-0.05f, 0.12f, sunDir.y);
            float weather = 1f - world.getRainGradient(tickDelta) * 0.95f;
            float blocked = cfg.occlusionCheck() && isDirectionOccluded(mc, camera, sunDir) ? 0f : 1f;
            sunTarget = sunProj.edgeFade() * horizon * weather * blocked;
        }
        sunVisibility = MathUtil.approach(sunVisibility, sunTarget, cfg.fadeSpeed(), dt);

        SunState sunState = new SunState(sunLastX, sunLastY, sunVisibility, sunLastAlignment);
        if (cfg.sunFlare() && sunVisibility > 0.001f) {
            sources.add(new FlareSource(
                    sunLastX, sunLastY, sunVisibility, sunLastAlignment,
                    0xFFF2D6, 0x9FC8FF, 1.0f, 1.0f, true, true, 1.0f));
            totalDirt += sunVisibility * (0.35f + 0.65f * sunLastAlignment);
        }

        // 2. MOON
        Vector3f moonDir = new Vector3f((float) Math.sin(skyAngle), (float) -Math.cos(skyAngle), 0f);
        Projection moonProj = projectDirection(camera, moonDir, fov, width, height);

        float moonTarget = 0f;
        int moonPhase = (int) (world.getTimeOfDay() / 24000L % 8L);
        float[] MOON_PHASE_FACTORS = { 1.0f, 0.8f, 0.55f, 0.3f, 0.08f, 0.3f, 0.55f, 0.8f };
        float phaseFactor = MOON_PHASE_FACTORS[Math.abs(moonPhase) % 8];

        if (cfg.moonFlare() && !underwater && moonProj.inFront()) {
            moonLastX = moonProj.x();
            moonLastY = moonProj.y();
            moonLastAlignment = moonProj.alignment();
            float horizon = MathUtil.smoothstep(-0.05f, 0.12f, moonDir.y);
            float weather = 1f - world.getRainGradient(tickDelta) * 0.95f;
            float blocked = cfg.occlusionCheck() && isDirectionOccluded(mc, camera, moonDir) ? 0f : 1f;
            moonTarget = moonProj.edgeFade() * horizon * weather * blocked * phaseFactor * 0.75f;
        }
        moonVisibility = MathUtil.approach(moonVisibility, moonTarget, cfg.fadeSpeed(), dt);

        if (cfg.moonFlare() && moonVisibility > 0.001f) {
            sources.add(new FlareSource(
                    moonLastX, moonLastY, moonVisibility, moonLastAlignment,
                    0xCBE3FB, 0x7EA5D9, 0.72f, 0.35f, false, true, 0.45f * phaseFactor));
            totalDirt += moonVisibility * 0.45f * phaseFactor * (0.35f + 0.65f * moonLastAlignment);
        }

        // 3. LIGHTNING
        if (cfg.lightningFlare()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof LightningEntity) {
                    lastLightningPos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());
                    lightningTimer = 0.40f;
                    break;
                }
            }
        }
        if (lightningTimer > 0f) {
            lightningTimer = Math.max(0f, lightningTimer - dt);
            float flash = Math.min(1.0f, lightningTimer / 0.15f);
            totalDirt += flash * 0.95f; // blinding overexposure illuminates lens dust

            if (lastLightningPos != null && !underwater) {
                Projection lProj = projectWorldPos(camera, lastLightningPos, fov, width, height);
                if (lProj.inFront()) {
                    sources.add(new FlareSource(
                            lProj.x(), lProj.y(), flash * lProj.edgeFade(), lProj.alignment(),
                            0xDCF2FF, 0x80D8FF, 1.5f, 0.75f, true, true, 1.0f));
                }
            }
        }

        // 4. PERIODIC WORLD SCAN (Beacons & Portals)
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastScanTime > 1000L) {
            lastScanTime = nowMs;
            scanWorldAroundPlayer(world, player, cfg);
        }

        // 5. BEACONS
        float beaconTarget = 0f;
        if (cfg.beaconFlare() && !underwater && !cachedBeacons.isEmpty()) {
            CachedBeacon nearestBeacon = null;
            double nearestDistSq = Double.MAX_VALUE;
            double px = player.getX(), py = player.getY(), pz = player.getZ();
            for (CachedBeacon b : cachedBeacons) {
                double dSq = b.pos().getSquaredDistance(px, py, pz);
                if (dSq < nearestDistSq && dSq < 64.0 * 64.0) {
                    nearestDistSq = dSq;
                    nearestBeacon = b;
                }
            }
            if (nearestBeacon != null) {
                Vec3d beaconBase = Vec3d.ofCenter(nearestBeacon.pos()).add(0, 1.0, 0);
                double dy = Math.max(0.0, Math.min(60.0, camPos.y - beaconBase.y));
                Vec3d targetBeamPoint = beaconBase.add(0, dy, 0);
                Projection bProj = projectWorldPos(camera, targetBeamPoint, fov, width, height);
                if (bProj.inFront()) {
                    beaconLastX = bProj.x();
                    beaconLastY = bProj.y();
                    beaconLastAlignment = bProj.alignment();
                    beaconLastColor = nearestBeacon.color();
                    double dist = Math.sqrt(nearestDistSq);
                    float distFactor = 1.0f - (float) (dist / 64.0);
                    float blocked = cfg.occlusionCheck() && isPointOccluded(mc, camera, targetBeamPoint) ? 0f : 1f;
                    beaconTarget = bProj.edgeFade() * distFactor * blocked * 0.85f;
                }
            }
        }
        beaconVisibility = MathUtil.approach(beaconVisibility, beaconTarget, cfg.fadeSpeed() * 1.2f, dt);
        if (cfg.beaconFlare() && beaconVisibility > 0.001f) {
            sources.add(new FlareSource(
                    beaconLastX, beaconLastY, beaconVisibility, beaconLastAlignment,
                    beaconLastColor, beaconLastColor, 0.85f, 0.25f, false, true, 0.4f));
            totalDirt += beaconVisibility * 0.4f * (0.35f + 0.65f * beaconLastAlignment);
        }

        // 6. PORTALS (Nether & End)
        float portalTarget = 0f;
        if (cfg.portalFlare() && !underwater && !cachedPortals.isEmpty()) {
            CachedPortal nearestPortal = null;
            double nearestDistSq = Double.MAX_VALUE;
            double px = player.getX(), py = player.getY(), pz = player.getZ();
            for (CachedPortal p : cachedPortals) {
                double dSq = p.pos().getSquaredDistance(px, py, pz);
                if (dSq < nearestDistSq && dSq < 28.0 * 28.0) {
                    nearestDistSq = dSq;
                    nearestPortal = p;
                }
            }
            if (nearestPortal != null) {
                Vec3d portalCenter = Vec3d.ofCenter(nearestPortal.pos());
                Projection pProj = projectWorldPos(camera, portalCenter, fov, width, height);
                if (pProj.inFront()) {
                    portalLastX = pProj.x();
                    portalLastY = pProj.y();
                    portalLastAlignment = pProj.alignment();
                    portalIsEnd = nearestPortal.isEnd();
                    double dist = Math.sqrt(nearestDistSq);
                    float distFactor = 1.0f - (float) (dist / 28.0);
                    float blocked = cfg.occlusionCheck() && isPointOccluded(mc, camera, portalCenter) ? 0f : 1f;
                    portalTarget = pProj.edgeFade() * distFactor * blocked * 0.8f;
                }
            }
        }
        portalVisibility = MathUtil.approach(portalVisibility, portalTarget, cfg.fadeSpeed() * 1.2f, dt);
        if (cfg.portalFlare() && portalVisibility > 0.001f) {
            int pGlow = portalIsEnd ? 0x3A86FF : 0x9D4EDD;
            int pStreak = portalIsEnd ? 0x8338EC : 0xC77DFF;
            sources.add(new FlareSource(
                    portalLastX, portalLastY, portalVisibility, portalLastAlignment,
                    pGlow, pStreak, 0.95f, 0.35f, portalIsEnd, true, 0.45f));
            totalDirt += portalVisibility * 0.45f * (0.35f + 0.65f * portalLastAlignment);
        }

        // 7. END CRYSTALS
        float crystalTarget = 0f;
        if (cfg.endCrystalFlare() && !underwater) {
            EndCrystalEntity nearestCrystal = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (Entity entity : world.getEntities()) {
                if (entity instanceof EndCrystalEntity crystal) {
                    double dSq = crystal.squaredDistanceTo(camPos);
                    if (dSq < nearestDistSq && dSq < 48.0 * 48.0) {
                        nearestDistSq = dSq;
                        nearestCrystal = crystal;
                    }
                }
            }
            if (nearestCrystal != null) {
                Vec3d crystalPoint = new Vec3d(nearestCrystal.getX(), nearestCrystal.getY() + 1.1, nearestCrystal.getZ());
                Projection cProj = projectWorldPos(camera, crystalPoint, fov, width, height);
                if (cProj.inFront()) {
                    crystalLastX = cProj.x();
                    crystalLastY = cProj.y();
                    crystalLastAlignment = cProj.alignment();
                    double dist = Math.sqrt(nearestDistSq);
                    float distFactor = 1.0f - (float) (dist / 48.0);
                    float pulse = 0.85f + 0.15f * (float) Math.sin(System.currentTimeMillis() * 0.006);
                    float blocked = cfg.occlusionCheck() && isPointOccluded(mc, camera, crystalPoint) ? 0f : 1f;
                    crystalTarget = cProj.edgeFade() * distFactor * pulse * blocked * 0.75f;
                }
            }
        }
        crystalVisibility = MathUtil.approach(crystalVisibility, crystalTarget, cfg.fadeSpeed() * 1.5f, dt);
        if (cfg.endCrystalFlare() && crystalVisibility > 0.001f) {
            sources.add(new FlareSource(
                    crystalLastX, crystalLastY, crystalVisibility, crystalLastAlignment,
                    0xFF2A85, 0xFF70A6, 0.8f, 0.3f, true, true, 0.4f));
            totalDirt += crystalVisibility * 0.4f * (0.35f + 0.65f * crystalLastAlignment);
        }

        // 8. EXPLOSIONS
        if (cfg.explosionFlare() && !explosions.isEmpty()) {
            explosions.removeIf(e -> nowMs - e.startTime() > 400L);
            for (ActiveExplosion blast : explosions) {
                float progress = (nowMs - blast.startTime()) / 400.0f;
                float intensity = (1.0f - progress) * (1.0f - progress);
                totalDirt += intensity * 0.8f;

                if (!underwater) {
                    Projection exProj = projectWorldPos(camera, blast.pos(), fov, width, height);
                    if (exProj.inFront()) {
                        sources.add(new FlareSource(
                                exProj.x(), exProj.y(), intensity * exProj.edgeFade(), exProj.alignment(),
                                0xFFB74D, 0xFFD54F, 1.25f, 0.5f, true, true, 0.85f));
                    }
                }
            }
        }

        return new TrackingResult(sources, MathUtil.clamp01(totalDirt), sunState);
    }

    public void onExplosion(Vec3d pos) {
        explosions.add(new ActiveExplosion(pos, System.currentTimeMillis()));
    }

    private void scanWorldAroundPlayer(ClientWorld world, PlayerEntity player, AtmosphereConfig cfg) {
        if (cfg.beaconFlare()) {
            cachedBeacons.clear();
            int pcx = player.getChunkPos().x;
            int pcz = player.getChunkPos().z;
            for (int cx = pcx - 2; cx <= pcx + 2; cx++) {
                for (int cz = pcz - 2; cz <= pcz + 2; cz++) {
                    WorldChunk chunk = world.getChunk(cx, cz);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be instanceof BeaconBlockEntity beacon) {
                            if (!beacon.getBeamSegments().isEmpty()) {
                                int color = beacon.getBeamSegments().get(0).getColor() & 0xFFFFFF;
                                cachedBeacons.add(new CachedBeacon(beacon.getPos(), color));
                            }
                        }
                    }
                }
            }
        }

        if (cfg.portalFlare()) {
            cachedPortals.clear();
            BlockPos pPos = player.getBlockPos();
            BlockPos.Mutable mut = new BlockPos.Mutable();
            int r = 16;
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dy = -8; dy <= 8; dy += 2) {
                    for (int dz = -r; dz <= r; dz += 2) {
                        mut.set(pPos.getX() + dx, pPos.getY() + dy, pPos.getZ() + dz);
                        BlockState state = world.getBlockState(mut);
                        if (state.isOf(Blocks.NETHER_PORTAL)) {
                            cachedPortals.add(new CachedPortal(mut.toImmutable(), false));
                            break;
                        } else if (state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.END_GATEWAY)) {
                            cachedPortals.add(new CachedPortal(mut.toImmutable(), true));
                            break;
                        }
                    }
                }
            }
        }
    }

    private record Projection(float x, float y, float alignment, float edgeFade, boolean inFront) {}

    private static Projection projectDirection(Camera camera, Vector3f dir, float fov, int width, int height) {
        Vector3f view = new Vector3f(dir).rotate(new Quaternionf(camera.getRotation()).conjugate());
        if (view.z >= 0f) {
            return new Projection(0f, 0f, 0f, 0f, false);
        }
        float tanHalfFov = (float) Math.tan(Math.toRadians(fov) * 0.5);
        float aspect = (float) width / height;
        float ndcX = view.x / (-view.z * tanHalfFov * aspect);
        float ndcY = view.y / (-view.z * tanHalfFov);

        float screenX = (ndcX * 0.5f + 0.5f) * width;
        float screenY = (1f - (ndcY * 0.5f + 0.5f)) * height;
        float alignment = 1f - MathUtil.clamp01((float) Math.hypot(ndcX, ndcY) / 1.414f);
        float edge = 1f - MathUtil.smoothstep(EDGE_FADE_START, EDGE_FADE_END, Math.max(Math.abs(ndcX), Math.abs(ndcY)));

        return new Projection(screenX, screenY, alignment, edge, true);
    }

    private static Projection projectWorldPos(Camera camera, Vec3d targetPos, float fov, int width, int height) {
        Vec3d delta = targetPos.subtract(camera.getCameraPos());
        double len = delta.length();
        if (len < 0.001) return new Projection(0, 0, 0, 0, false);
        Vector3f dir = new Vector3f((float) (delta.x / len), (float) (delta.y / len), (float) (delta.z / len));
        return projectDirection(camera, dir, fov, width, height);
    }

    private static boolean isDirectionOccluded(MinecraftClient mc, Camera camera, Vector3f dir) {
        Vec3d start = camera.getCameraPos();
        Vec3d end = start.add(dir.x * CELESTIAL_RAY_LENGTH, dir.y * CELESTIAL_RAY_LENGTH, dir.z * CELESTIAL_RAY_LENGTH);
        HitResult hit = mc.world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.ANY, mc.player));
        return hit.getType() != HitResult.Type.MISS;
    }

    private static boolean isPointOccluded(MinecraftClient mc, Camera camera, Vec3d targetPos) {
        Vec3d start = camera.getCameraPos();
        HitResult hit = mc.world.raycast(new RaycastContext(start, targetPos,
                RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return false;
        double hitDistSq = hit.getPos().squaredDistanceTo(start);
        double targetDistSq = targetPos.squaredDistanceTo(start);
        return hitDistSq < targetDistSq - 0.75;
    }

    private float frameSeconds() {
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;
        return Math.min(dt, 0.1f);
    }
}
