package fun.newrar.module.impl.render;

import fun.newrar.manager.event_impl.EventRender3D;
import fun.newrar.utils.annotation.IMinecraft;
import fun.newrar.utils.colors.ColorUtil;
import fun.newrar.utils.render.LightningPath;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LightningRenderer implements IMinecraft {
    private static final int MAX_BOLTS_CAP = 64;
    private static final long HIT_FLASH_DURATION_MS = 360L;

    private static final Identifier GLOW_TEX = Identifier.of("client", "textures/particles/glow.png");
    private static final Identifier SPARKLE_TEX = Identifier.of("client", "textures/particles/sparkle.png");

    public boolean redOnHit = true;
    public boolean skyStrike = true;
    public boolean groundRing = true;
    public boolean sparksEnabled = true;

    public int maxBolts = 16;
    public long spawnIntervalMs = 42L;
    public float thickness = 1.2F;
    public String colorMode = "Электрический";
    public int customColor = 0x23B4FF;

    private final Random random = new Random();
    private final Bolt[] bolts = new Bolt[MAX_BOLTS_CAP];
    private int boltCount = 0;
    private final List<Spark> sparks = new ArrayList<>();

    private long lastSpawn = 0L;
    private long lastSkyStrike = 0L;
    private long skyStrikeInterval = 1600L;
    private long lastHitTime = -HIT_FLASH_DURATION_MS;
    private int lastHurtTime = 0;
    private long lastFrameTime = 0L;

    private LivingEntity lastTrackedTarget;

    private boolean shockwaveActive = false;
    private long shockwaveStartTime = 0L;

    public void clear() {
        boltCount = 0;
        sparks.clear();
        shockwaveActive = false;
        lastTrackedTarget = null;
    }

    public void notifyHit() {
        lastHitTime = System.currentTimeMillis();
    }

    public void render(EventRender3D e, VertexConsumerProvider.Immediate immediate, LivingEntity target, float anim) {
        if (target == null || anim <= 0.005f || mc.world == null || mc.player == null) {
            clear();
            return;
        }

        if (target != lastTrackedTarget) {
            clear();
            lastTrackedTarget = target;
            lastHurtTime = target.hurtTime;
        }

        long now = System.currentTimeMillis();
        float dt = lastFrameTime == 0L ? 0.016f : (float) ((now - lastFrameTime) / 1000.0);
        lastFrameTime = now;
        dt = MathHelper.clamp(dt, 0.001f, 0.05f);

        if (target.hurtTime > lastHurtTime) {
            notifyHit();
            if (skyStrike && now - lastSkyStrike > 320L) {
                spawnSkyStrike(target.getWidth(), target.getHeight(), now);
                lastSkyStrike = now;
            }
        }
        lastHurtTime = target.hurtTime;

        double width = target.getWidth();
        double height = target.getHeight();

        // Spawn sky strike periodically
        if (skyStrike && now - lastSkyStrike > skyStrikeInterval) {
            spawnSkyStrike(width, height, now);
            lastSkyStrike = now;
            skyStrikeInterval = 1300L + random.nextInt(1100);
        }

        // Spawn body / orbit / ground bolts
        if (now - lastSpawn > spawnIntervalMs && boltCount < Math.min(maxBolts, MAX_BOLTS_CAP)) {
            int toSpawn = 1 + (random.nextFloat() < 0.45f ? 1 : 0);
            for (int s = 0; s < toSpawn && boltCount < Math.min(maxBolts, MAX_BOLTS_CAP); s++) {
                float roll = random.nextFloat();
                if (groundRing && roll < 0.28f) {
                    bolts[boltCount++] = spawnGroundArc(width, now);
                } else if (roll < 0.65f) {
                    bolts[boltCount++] = spawnOrbitArc(width, height, now);
                } else {
                    bolts[boltCount++] = spawnBodyArc(width, height, now);
                }
            }
            lastSpawn = now;
        }

        // Hit flash progression
        float hitT = 0f;
        if (redOnHit) {
            long sinceHit = now - lastHitTime;
            if (sinceHit < HIT_FLASH_DURATION_MS) {
                hitT = 1.0f - (sinceHit / (float) HIT_FLASH_DURATION_MS);
            }
        }

        // Update bolt alphas and remove dead bolts
        for (int i = 0; i < boltCount; i++) {
            Bolt bolt = bolts[i];
            long age = now - bolt.spawnTime;
            if (age > bolt.lifetimeMs) {
                bolts[i] = bolts[--boltCount];
                i--;
                continue;
            }

            float life = age / (float) bolt.lifetimeMs;
            float env = (float) Math.sin(life * Math.PI);
            float flicker = 0.72f + random.nextFloat() * 0.28f;
            bolt.drawAlpha = anim * env * flicker;
        }

        // Update sparks physics
        if (sparksEnabled) {
            for (int i = sparks.size() - 1; i >= 0; i--) {
                Spark sp = sparks.get(i);
                float ageSec = (now - sp.born) / 1000.0f;
                if (ageSec > sp.life) {
                    sparks.remove(i);
                    continue;
                }

                sp.vy -= 9.8 * dt;
                sp.px += sp.vx * dt;
                sp.py += sp.vy * dt;
                sp.pz += sp.vz * dt;

                if (sp.py < 0.02) {
                    sp.py = 0.02;
                    sp.vy = -sp.vy * 0.4;
                    sp.vx *= 0.65;
                    sp.vz *= 0.65;
                }
            }
        }

        Vec3d basePos = target.getLerpedPos(e.getTickDelta());
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Quaternionf cameraRotation = mc.gameRenderer.getCamera().getRotation();
        Vector3f camRight = cameraRotation.transform(new Vector3f(1, 0, 0));
        Vector3f camUp = cameraRotation.transform(new Vector3f(0, 1, 0));
        Vec3d camRel = cameraPos.subtract(basePos);

        MatrixStack matrices = e.getMatrixStack();
        matrices.push();
        matrices.translate(basePos.x - cameraPos.x, basePos.y - cameraPos.y, basePos.z - cameraPos.z);
        Matrix4f m = matrices.peek().getPositionMatrix();

        float groundRadius = (float) (width * 0.5 + 0.38);
        int outerColorRaw = getOuterRgb(0.25f, hitT);

        // =========================================================================
        // PASS 1: FILL QUADS (TargetEsp.RING_FILL_LAYER)
        // =========================================================================
        VertexConsumer colorBuf = immediate.getBuffer(TargetEsp.RING_FILL_LAYER);

        if (groundRing) {
            drawGroundDiscQuads(colorBuf, m, groundRadius * 1.35f,
                    ColorUtil.replAlpha(outerColorRaw, (int) (65 * anim)),
                    ColorUtil.replAlpha(outerColorRaw, 0));

            drawGroundRingQuads(colorBuf, m, groundRadius, 0.065f * thickness,
                    withAlpha(outerColorRaw, (int) (125 * anim * (0.8f + 0.2f * (float) Math.sin(now * 0.012)))));

            drawGroundRingQuads(colorBuf, m, groundRadius * 0.62f, 0.035f * thickness,
                    withAlpha(outerColorRaw, (int) (95 * anim * (0.8f + 0.2f * (float) Math.cos(now * 0.015)))));
        }

        if (shockwaveActive) {
            long swAge = now - shockwaveStartTime;
            if (swAge > 500L) {
                shockwaveActive = false;
            } else {
                float swProgress = swAge / 500.0f;
                float swRadius = (float) (width * 0.5 + 0.2 + swProgress * 1.8);
                float swAlpha = (1.0f - swProgress) * (1.0f - swProgress) * anim;
                int swColor = withAlpha(getOuterRgb(0.5f, hitT), (int) (swAlpha * 180));
                drawGroundRingQuads(colorBuf, m, swRadius, 0.14f * (1.0f - swProgress * 0.6f), swColor);
            }
        }

        // Lightning ribbons (outer colored corona + inner incandescent core)
        for (int i = 0; i < boltCount; i++) {
            Bolt bolt = bolts[i];
            if (bolt.drawAlpha <= 0.02f) continue;

            float boltThick = bolt.thickness * thickness;
            int outerCol = withAlpha(getOuterRgb((bolt.spawnTime % 1000L) / 1000.0f, hitT), (int) (bolt.drawAlpha * 185));
            int coreCol = withAlpha(getCoreRgb(outerCol, hitT), (int) (bolt.drawAlpha * 255));

            float outerWidth = (bolt.isSkyStrike ? 0.13f : 0.075f) * boltThick;
            float coreWidth = (bolt.isSkyStrike ? 0.046f : 0.024f) * boltThick;

            // Outer colored ribbon
            ribbonPolylineLocal(colorBuf, m, bolt.points, camRel, outerWidth, outerCol, outerCol);
            for (List<Vec3d> branch : bolt.branches) {
                ribbonPolylineLocal(colorBuf, m, branch, camRel, outerWidth * 0.65f, outerCol, outerCol);
            }

            // Inner white-hot core ribbon
            ribbonPolylineLocal(colorBuf, m, bolt.points, camRel, coreWidth, coreCol, coreCol);
            for (List<Vec3d> branch : bolt.branches) {
                ribbonPolylineLocal(colorBuf, m, branch, camRel, coreWidth * 0.62f, coreCol, coreCol);
            }
        }

        // =========================================================================
        // PASS 2: SHARP CORE LINES (TargetEsp.RING_LINE_LAYER)
        // =========================================================================
        VertexConsumer lineBuf = immediate.getBuffer(TargetEsp.RING_LINE_LAYER);
        for (int i = 0; i < boltCount; i++) {
            Bolt bolt = bolts[i];
            if (bolt.drawAlpha <= 0.02f) continue;
            int coreLineCol = withAlpha(getCoreRgb(0, hitT), (int) (bolt.drawAlpha * 235));
            drawLinesLocal(lineBuf, m, bolt.points, coreLineCol);
            for (List<Vec3d> branch : bolt.branches) {
                drawLinesLocal(lineBuf, m, branch, coreLineCol);
            }
        }

        // =========================================================================
        // PASS 3: GLOW SPRITES (TargetEsp.ROMB_ESP with GLOW_TEX)
        // =========================================================================
        VertexConsumer glowBuf = immediate.getBuffer(TargetEsp.ROMB_ESP.apply(GLOW_TEX));
        for (int i = 0; i < boltCount; i++) {
            Bolt bolt = bolts[i];
            if (bolt.drawAlpha <= 0.02f) continue;

            float boltThick = bolt.thickness * thickness;
            int outerGlowCol = withAlpha(getOuterRgb((bolt.spawnTime % 1000L) / 1000.0f, hitT), (int) (bolt.drawAlpha * 140));

            int ptCount = bolt.points.size();
            for (int p = 0; p < ptCount; p += 2) {
                Vec3d pt = bolt.points.get(p);
                float haloSize = (bolt.isSkyStrike ? 0.28f : 0.14f) * boltThick;
                drawLocalBillboard(glowBuf, m, pt.x, pt.y, pt.z, camRight, camUp, haloSize, outerGlowCol);
            }

            if (bolt.isSkyStrike && bolt.impact != null) {
                float impactHalo = 0.75f * bolt.drawAlpha * boltThick;
                drawLocalBillboard(glowBuf, m, bolt.impact.x, bolt.impact.y + 0.05, bolt.impact.z,
                        camRight, camUp, impactHalo, withAlpha(outerGlowCol, (int) (bolt.drawAlpha * 230)));
            }
        }

        // =========================================================================
        // PASS 4: SPARKS (TargetEsp.ROMB_ESP with SPARKLE_TEX)
        // =========================================================================
        if (sparksEnabled && !sparks.isEmpty()) {
            VertexConsumer sparkBuf = immediate.getBuffer(TargetEsp.ROMB_ESP.apply(SPARKLE_TEX));
            for (Spark sp : sparks) {
                float ageSec = (now - sp.born) / 1000.0f;
                float lifeRatio = 1.0f - (ageSec / sp.life);
                int sparkAlpha = (int) (lifeRatio * anim * 245);
                if (sparkAlpha <= 3) continue;

                int col = withAlpha(sp.color, sparkAlpha);
                float sz = sp.size * (0.6f + 0.4f * lifeRatio);
                drawLocalBillboard(sparkBuf, m, sp.px, sp.py, sp.pz, camRight, camUp, sz, col);
            }
        }

        matrices.pop();
    }

    private static void drawGroundDiscQuads(VertexConsumer buf, Matrix4f m, float radius, int colorCenter, int colorEdge) {
        int segs = 32;
        for (int i = 0; i < segs; i++) {
            float a0 = (float) (Math.PI * 2.0 * i / segs);
            float a1 = (float) (Math.PI * 2.0 * (i + 1) / segs);
            float x0 = (float) Math.cos(a0) * radius;
            float z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;

            buf.vertex(m, 0, 0.02f, 0).color(colorCenter);
            buf.vertex(m, x0, 0.02f, z0).color(colorEdge);
            buf.vertex(m, x1, 0.02f, z1).color(colorEdge);
            buf.vertex(m, x1, 0.02f, z1).color(colorEdge);
        }
    }

    private static void drawGroundRingQuads(VertexConsumer buf, Matrix4f m, float radius, float width, int color) {
        int segs = 32;
        float rOut = radius + width * 0.5f;
        float rIn = Math.max(0.01f, radius - width * 0.5f);

        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2.0 * i / segs;
            double a1 = Math.PI * 2.0 * (i + 1) / segs;

            float x0_out = (float) Math.cos(a0) * rOut;
            float z0_out = (float) Math.sin(a0) * rOut;
            float x1_out = (float) Math.cos(a1) * rOut;
            float z1_out = (float) Math.sin(a1) * rOut;

            float x0_in = (float) Math.cos(a0) * rIn;
            float z0_in = (float) Math.sin(a0) * rIn;
            float x1_in = (float) Math.cos(a1) * rIn;
            float z1_in = (float) Math.sin(a1) * rIn;

            buf.vertex(m, x0_out, 0.025f, z0_out).color(color);
            buf.vertex(m, x1_out, 0.025f, z1_out).color(color);
            buf.vertex(m, x1_in, 0.025f, z1_in).color(color);
            buf.vertex(m, x0_in, 0.025f, z0_in).color(color);
        }
    }

    private static void ribbonPolylineLocal(VertexConsumer buf, Matrix4f m,
                                            List<Vec3d> pts, Vec3d camRel, float halfW,
                                            int colStart, int colEnd) {
        int segs = pts.size() - 1;
        if (segs <= 0) return;

        for (int i = 0; i < segs; i++) {
            Vec3d a = pts.get(i);
            Vec3d b = pts.get(i + 1);

            double camToAx = a.x - camRel.x;
            double camToAy = a.y - camRel.y;
            double camToAz = a.z - camRel.z;

            double dx = b.x - a.x;
            double dy = b.y - a.y;
            double dz = b.z - a.z;

            double cx = camToAy * dz - camToAz * dy;
            double cy = camToAz * dx - camToAx * dz;
            double cz = camToAx * dy - camToAy * dx;
            double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (len < 1e-6) continue;

            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;

            float w0 = halfW * (1.0f - 0.22f * t0);
            float w1 = halfW * (1.0f - 0.22f * t1);

            float cx0 = (float) (cx / len * w0);
            float cy0 = (float) (cy / len * w0);
            float cz0 = (float) (cz / len * w0);

            float cx1 = (float) (cx / len * w1);
            float cy1 = (float) (cy / len * w1);
            float cz1 = (float) (cz / len * w1);

            int c0 = lerpRgb(colStart, colEnd, t0);
            int c1 = lerpRgb(colStart, colEnd, t1);

            buf.vertex(m, (float) a.x - cx0, (float) a.y - cy0, (float) a.z - cz0).color(c0);
            buf.vertex(m, (float) a.x + cx0, (float) a.y + cy0, (float) a.z + cz0).color(c0);
            buf.vertex(m, (float) b.x + cx1, (float) b.y + cy1, (float) b.z + cz1).color(c1);
            buf.vertex(m, (float) b.x - cx1, (float) b.y - cy1, (float) b.z - cz1).color(c1);
        }
    }

    private static void drawLinesLocal(VertexConsumer buf, Matrix4f m, List<Vec3d> pts, int color) {
        int segs = pts.size() - 1;
        for (int s = 0; s < segs; s++) {
            Vec3d a = pts.get(s);
            Vec3d b = pts.get(s + 1);
            buf.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(color);
            buf.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(color);
        }
    }

    private static void drawLocalBillboard(VertexConsumer buf, Matrix4f m, double lx, double ly, double lz,
                                           Vector3f right, Vector3f up, float half, int color) {
        float x = (float) lx, y = (float) ly, z = (float) lz;
        float rx = right.x * half, ry = right.y * half, rz = right.z * half;
        float ux = up.x * half, uy = up.y * half, uz = up.z * half;

        buf.vertex(m, x - rx - ux, y - ry - uy, z - rz - uz).color(color).texture(0f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m, x + rx - ux, y + ry - uy, z + rz - uz).color(color).texture(1f, 1f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m, x + rx + ux, y + ry + uy, z + rz + uz).color(color).texture(1f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buf.vertex(m, x - rx + ux, y - ry + uy, z - rz + uz).color(color).texture(0f, 0f).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

    private void triggerShockwave() {
        shockwaveActive = true;
        shockwaveStartTime = System.currentTimeMillis();
    }

    private void spawnSkyStrike(double width, double height, long now) {
        Vec3d strikeImpact = new Vec3d((random.nextDouble() - 0.5) * 0.25, height * 0.9, (random.nextDouble() - 0.5) * 0.25);
        double skyHeight = 9.0 + random.nextDouble() * 5.0;
        Vec3d skyStart = new Vec3d((random.nextDouble() - 0.5) * 2.0, height + skyHeight, (random.nextDouble() - 0.5) * 2.0);

        Bolt bolt = new Bolt();
        bolt.isSkyStrike = true;
        bolt.spawnTime = now;
        bolt.lifetimeMs = 240 + random.nextInt(80);
        bolt.thickness = 1.6F;
        bolt.impact = strikeImpact;
        bolt.points = LightningPath.generate(skyStart, strikeImpact, 4, 0.75 * thickness, 0.62, random);

        int branchCount = 2 + random.nextInt(2);
        for (int b = 0; b < branchCount; b++) {
            if (bolt.points.size() < 6) break;
            int idx = 2 + random.nextInt(bolt.points.size() - 4);
            Vec3d from = bolt.points.get(idx);
            double blen = 2.0 + random.nextDouble() * 3.0;
            double ba = random.nextDouble() * Math.PI * 2.0;
            Vec3d bend = new Vec3d(
                    from.x + Math.cos(ba) * blen,
                    from.y - blen * (0.55 + random.nextDouble() * 0.45),
                    from.z + Math.sin(ba) * blen
            );
            bolt.branches.add(LightningPath.generate(from, bend, 3, blen * 0.26, 0.58, random));
        }

        if (boltCount < MAX_BOLTS_CAP) {
            bolts[boltCount++] = bolt;
        }

        triggerShockwave();
        if (sparksEnabled) {
            int sparkCount = 14 + random.nextInt(8);
            for (int s = 0; s < sparkCount; s++) {
                double a = random.nextDouble() * Math.PI * 2.0;
                double spd = 2.5 + random.nextDouble() * 5.5;
                double vx = Math.cos(a) * spd;
                double vz = Math.sin(a) * spd;
                double vy = 2.5 + random.nextDouble() * 5.0;
                int col = getOuterRgb(random.nextFloat(), redOnHit && (now - lastHitTime < HIT_FLASH_DURATION_MS) ? 1f : 0f);
                sparks.add(new Spark(strikeImpact.x, strikeImpact.y, strikeImpact.z, vx, vy, vz, now,
                        0.5f + random.nextFloat() * 0.5f, 0.08f + random.nextFloat() * 0.08f, col));
            }
        }
    }

    private Bolt spawnBodyArc(double width, double height, long now) {
        double radius = width * 0.5 + 0.06;

        double a1 = random.nextDouble() * Math.PI * 2.0;
        double h1 = 0.1 + random.nextDouble() * (height - 0.2);
        Vec3d start = new Vec3d(Math.cos(a1) * (radius * (0.8 + random.nextDouble() * 0.4)), h1, Math.sin(a1) * (radius * (0.8 + random.nextDouble() * 0.4)));

        double a2 = a1 + (random.nextBoolean() ? 1 : -1) * (0.6 + random.nextDouble() * 1.5);
        double h2 = MathHelper.clamp(h1 + (random.nextDouble() - 0.5) * height * 0.8, 0.05, height);
        Vec3d end = new Vec3d(Math.cos(a2) * (radius * (0.8 + random.nextDouble() * 0.4)), h2, Math.sin(a2) * (radius * (0.8 + random.nextDouble() * 0.4)));

        Bolt bolt = new Bolt();
        bolt.spawnTime = now;
        bolt.lifetimeMs = 90 + random.nextInt(100);
        bolt.thickness = 0.9F + random.nextFloat() * 0.4F;
        bolt.points = LightningPath.generate(start, end, 3, 0.18 * thickness, 0.56, random);

        if (random.nextFloat() < 0.35f && bolt.points.size() > 3) {
            int forkIdx = 1 + random.nextInt(bolt.points.size() - 2);
            Vec3d fStart = bolt.points.get(forkIdx);
            Vec3d fEnd = fStart.add(
                    (random.nextDouble() - 0.5) * 0.5 * thickness,
                    (random.nextDouble() - 0.5) * 0.5 * thickness,
                    (random.nextDouble() - 0.5) * 0.5 * thickness
            );
            bolt.branches.add(LightningPath.generate(fStart, fEnd, 2, 0.12 * thickness, 0.55, random));
        }

        if (sparksEnabled && random.nextFloat() < 0.4f) {
            Vec3d spPos = bolt.points.get(random.nextInt(bolt.points.size()));
            sparks.add(new Spark(spPos.x, spPos.y, spPos.z,
                    (random.nextDouble() - 0.5) * 2.0, 1.2 + random.nextDouble() * 2.0, (random.nextDouble() - 0.5) * 2.0,
                    now, 0.35f + random.nextFloat() * 0.35f, 0.05f + random.nextFloat() * 0.06f,
                    getOuterRgb(random.nextFloat(), 0f)));
        }

        return bolt;
    }

    private Bolt spawnOrbitArc(double width, double height, long now) {
        double radius = width * 0.5 + 0.16;

        double aStart = random.nextDouble() * Math.PI * 2.0;
        double aEnd = aStart + (random.nextBoolean() ? 1 : -1) * (1.2 + random.nextDouble() * 1.6);
        double hStart = random.nextDouble() * (height * 0.6);
        double hEnd = MathHelper.clamp(hStart + 0.3 + random.nextDouble() * 0.7, 0.1, height);

        Vec3d start = new Vec3d(Math.cos(aStart) * radius, hStart, Math.sin(aStart) * radius);
        Vec3d end = new Vec3d(Math.cos(aEnd) * radius, hEnd, Math.sin(aEnd) * radius);

        Bolt bolt = new Bolt();
        bolt.spawnTime = now;
        bolt.lifetimeMs = 110 + random.nextInt(90);
        bolt.thickness = 1.0F;
        bolt.points = LightningPath.generate(start, end, 3, 0.22 * thickness, 0.58, random);

        return bolt;
    }

    private Bolt spawnGroundArc(double width, long now) {
        double groundRadius = (width * 0.5) + 0.38;
        double a1 = random.nextDouble() * Math.PI * 2.0;
        double a2 = a1 + (random.nextDouble() - 0.5) * 1.4;

        Vec3d start = new Vec3d(Math.cos(a1) * groundRadius, 0.02, Math.sin(a1) * groundRadius);
        Vec3d end = (random.nextFloat() < 0.45f)
                ? new Vec3d((random.nextDouble() - 0.5) * 0.2, 0.05, (random.nextDouble() - 0.5) * 0.2)
                : new Vec3d(Math.cos(a2) * groundRadius, 0.02, Math.sin(a2) * groundRadius);

        Bolt bolt = new Bolt();
        bolt.spawnTime = now;
        bolt.lifetimeMs = 80 + random.nextInt(80);
        bolt.thickness = 0.85F;
        bolt.points = LightningPath.generate(start, end, 2, 0.14 * thickness, 0.55, random);

        return bolt;
    }

    public int getOuterRgb(float progress, float hitT) {
        int base;
        switch (colorMode) {
            case "Тема":
                base = ColorUtil.fade((int) (progress * 360));
                break;
            case "Фиолетовый":
                base = 0xB537F2;
                break;
            case "Золотой":
                base = 0xFFA000;
                break;
            case "Красный":
                base = 0xFF2A2A;
                break;
            case "Свой":
                base = customColor;
                break;
            case "Электрический":
            default:
                base = 0x22B2FF;
                break;
        }

        if (redOnHit && hitT > 0.01f) {
            base = ColorUtil.overCol(base, 0xFF1C24, hitT);
        }
        return base;
    }

    public int getCoreRgb(int outerRgb, float hitT) {
        if (redOnHit && hitT > 0.4f) {
            return 0xFFDADA;
        }
        return 0xFFFFFF;
    }

    private static int lerpRgb(int rgbA, int rgbB, float t) {
        if (t <= 0f) return rgbA;
        if (t >= 1f) return rgbB;
        int aA = (rgbA >>> 24) & 0xFF, rA = (rgbA >> 16) & 0xFF, gA = (rgbA >> 8) & 0xFF, bA = rgbA & 0xFF;
        int aB = (rgbB >>> 24) & 0xFF, rB = (rgbB >> 16) & 0xFF, gB = (rgbB >> 8) & 0xFF, bB = rgbB & 0xFF;
        int a = (int) (aA + (aB - aA) * t);
        int r = (int) (rA + (rB - rA) * t);
        int g = (int) (gA + (gB - gA) * t);
        int b = (int) (bA + (bB - bA) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int rgb, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    private static class Bolt {
        boolean isSkyStrike = false;
        List<Vec3d> points = new ArrayList<>();
        List<List<Vec3d>> branches = new ArrayList<>();
        Vec3d impact;
        long spawnTime;
        long lifetimeMs;
        float thickness = 1.0F;
        float drawAlpha = 0f;
    }

    private static class Spark {
        double px, py, pz;
        double vx, vy, vz;
        final long born;
        final float life;
        final float size;
        final int color;

        Spark(double x, double y, double z, double vx, double vy, double vz, long born, float life, float size, int col) {
            this.px = x;
            this.py = y;
            this.pz = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.born = born;
            this.life = life;
            this.size = size;
            this.color = col;
        }
    }
}


