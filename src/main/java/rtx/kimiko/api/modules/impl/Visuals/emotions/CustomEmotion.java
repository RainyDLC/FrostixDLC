package rtx.kimiko.api.modules.impl.Visuals.emotions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Пользовательская эмоция, созданная в редакторе.
 * Анимация задаётся кейфреймами (ромбики на таймлайне): каждый кейфрейм
 * хранит полную позу персонажа, между ключами поза интерполируется.
 */
public final class CustomEmotion implements EmotionAnim {
    /** Все анимируемые поля EmotionPose в фиксированном порядке. */
    public static final String[] FIELD_NAMES = {
        "headRotX", "headRotY", "headRotZ",
        "bodyRotX", "bodyRotY", "bodyRotZ",
        "rightArmRotX", "rightArmRotY", "rightArmRotZ",
        "leftArmRotX", "leftArmRotY", "leftArmRotZ",
        "rightLegRotX", "rightLegRotY", "rightLegRotZ",
        "leftLegRotX", "leftLegRotY", "leftLegRotZ",
        "headOffX", "headOffY", "headOffZ",
        "bodyOffX", "bodyOffY", "bodyOffZ",
        "rightArmOffX", "rightArmOffY", "rightArmOffZ",
        "leftArmOffX", "leftArmOffY", "leftArmOffZ",
        "rightLegOffX", "rightLegOffY", "rightLegOffZ",
        "leftLegOffX", "leftLegOffY", "leftLegOffZ",
        "upperOffY", "upperOffZ"
    };
    public static final int FIELD_COUNT = FIELD_NAMES.length;

    /** Части тела редактора: индексы полей [rotX, rotY, rotZ, offX, offY, offZ]. */
    public static final int PART_HEAD = 0;
    public static final int PART_BODY = 1;
    public static final int PART_RIGHT_ARM = 2;
    public static final int PART_LEFT_ARM = 3;
    public static final int PART_RIGHT_LEG = 4;
    public static final int PART_LEFT_LEG = 5;
    public static final int PART_UPPER = 6;
    public static final int PART_COUNT = 7;
    public static final int[][] PART_FIELD_INDICES = {
        {0, 1, 2, 18, 19, 20},     // голова
        {3, 4, 5, 21, 22, 23},     // тело
        {6, 7, 8, 24, 25, 26},     // правая рука
        {9, 10, 11, 27, 28, 29},   // левая рука
        {12, 13, 14, 30, 31, 32},  // правая нога
        {15, 16, 17, 33, 34, 35},  // левая нога
        {36, 37},                  // общее (upperOffY, upperOffZ)
    };
    public static final String[] PART_NAMES = {
        "Голова", "Тело", "Пр. рука", "Лев. рука", "Пр. нога", "Лев. нога", "Общее"
    };

    public static final float MIN_DURATION = 0.5f;
    public static final float MAX_DURATION = 12.0f;

    private static final Field[] POSE_FIELDS = buildPoseFields();

    private String name;
    private float duration;
    private final List<Keyframe> keyframes = new ArrayList<>();

    public CustomEmotion(String name, float duration) {
        this.name = name == null || name.isBlank() ? "Моя эмоция" : name;
        this.duration = clamp(duration, MIN_DURATION, MAX_DURATION);
    }

    /** Один ключевой кадр: момент времени и полная поза. */
    public static final class Keyframe {
        public float time;
        public final float[] values = new float[FIELD_COUNT];

        public Keyframe(float time, float[] values) {
            this.time = time;
            if (values != null) {
                System.arraycopy(values, 0, this.values, 0, Math.min(values.length, FIELD_COUNT));
            }
        }
    }

    @Override
    public String displayName() {
        return this.name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    @Override
    public float duration() {
        return this.duration;
    }

    public void setDuration(float duration) {
        this.duration = clamp(duration, MIN_DURATION, MAX_DURATION);
        this.keyframes.removeIf(k -> k.time > this.duration);
    }

    @Override
    public boolean isCustom() {
        return true;
    }

    /** Кейфреймы, всегда отсортированы по времени. */
    public List<Keyframe> keyframes() {
        return Collections.unmodifiableList(this.keyframes);
    }

    /** Добавить ключ или заменить существующий рядом с {@code time}. */
    public Keyframe upsertKeyframe(float time, float[] values) {
        float t = clamp(time, 0.0f, this.duration);
        Keyframe near = keyframeNear(t, 0.02f);
        if (near != null) {
            near.time = t;
            System.arraycopy(values, 0, near.values, 0, FIELD_COUNT);
            sortKeys();
            return near;
        }
        Keyframe key = new Keyframe(t, values);
        this.keyframes.add(key);
        sortKeys();
        return key;
    }

    public boolean removeKeyframe(Keyframe key) {
        return this.keyframes.remove(key);
    }

    /** Удалить ключ рядом с моментом времени. */
    public boolean removeKeyframeNear(float time, float eps) {
        Keyframe near = keyframeNear(time, eps);
        return near != null && this.keyframes.remove(near);
    }

    public Keyframe keyframeNear(float time, float eps) {
        Keyframe best = null;
        float bestDist = eps;
        for (Keyframe k : this.keyframes) {
            float d = Math.abs(k.time - time);
            if (d <= bestDist) {
                bestDist = d;
                best = k;
            }
        }
        return best;
    }

    /** Поза в момент времени: интерполяция между соседними ключами. */
    public void sampleInto(EmotionPose pose, float time) {
        if (this.keyframes.isEmpty()) {
            return;
        }
        float t = clamp(time, 0.0f, this.duration);
        Keyframe first = this.keyframes.get(0);
        Keyframe last = this.keyframes.get(this.keyframes.size() - 1);
        if (t <= first.time) {
            restore(pose, first.values);
            return;
        }
        if (t >= last.time) {
            restore(pose, last.values);
            return;
        }
        int i = 0;
        while (i < this.keyframes.size() - 2 && this.keyframes.get(i + 1).time < t) {
            i++;
        }
        Keyframe a = this.keyframes.get(i);
        Keyframe b = this.keyframes.get(i + 1);
        float span = b.time - a.time;
        float f = span <= 1e-6f ? 0.0f : (t - a.time) / span;
        f = f * f * (3.0f - 2.0f * f); // smoothstep — мягкое движение между ключами
        float[] mixed = new float[FIELD_COUNT];
        for (int k = 0; k < FIELD_COUNT; k++) {
            mixed[k] = a.values[k] + (b.values[k] - a.values[k]) * f;
        }
        restore(pose, mixed);
    }

    @Override
    public void apply(EmotionPose pose, float time) {
        sampleInto(pose, time);
    }

    /** Глубокая копия для безопасного редактирования. */
    public CustomEmotion copy() {
        CustomEmotion copy = new CustomEmotion(this.name, this.duration);
        for (Keyframe k : this.keyframes) {
            copy.keyframes.add(new Keyframe(k.time, k.values));
        }
        return copy;
    }

    // ---------- поза <-> массив ----------

    private static Field[] buildPoseFields() {
        Field[] fields = new Field[FIELD_COUNT];
        for (int i = 0; i < FIELD_COUNT; i++) {
            try {
                Field f = EmotionPose.class.getField(FIELD_NAMES[i]);
                if (f.getType() != float.class) {
                    throw new IllegalStateException("Not a float field: " + FIELD_NAMES[i]);
                }
                fields[i] = f;
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("Missing EmotionPose field: " + FIELD_NAMES[i], e);
            }
        }
        return fields;
    }

    public static float[] snapshot(EmotionPose pose) {
        float[] values = new float[FIELD_COUNT];
        try {
            for (int i = 0; i < FIELD_COUNT; i++) {
                values[i] = POSE_FIELDS[i].getFloat(pose);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return values;
    }

    public static void restore(EmotionPose pose, float[] values) {
        try {
            for (int i = 0; i < FIELD_COUNT; i++) {
                POSE_FIELDS[i].setFloat(pose, values[i]);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Значение одного поля позы по индексу из FIELD_NAMES. */
    public static float getValue(EmotionPose pose, int fieldIndex) {
        try {
            return POSE_FIELDS[fieldIndex].getFloat(pose);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Записать значение одного поля позы по индексу из FIELD_NAMES. */
    public static void setValue(EmotionPose pose, int fieldIndex, float value) {
        try {
            POSE_FIELDS[fieldIndex].setFloat(pose, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- JSON ----------

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("name", this.name);
        root.addProperty("duration", this.duration);
        JsonArray keys = new JsonArray();
        for (Keyframe k : this.keyframes) {
            JsonObject o = new JsonObject();
            o.addProperty("t", k.time);
            JsonArray v = new JsonArray();
            for (float f : k.values) {
                v.add(f);
            }
            o.add("v", v);
            keys.add(o);
        }
        root.add("keys", keys);
        return root.toString();
    }

    public static CustomEmotion fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String name = root.has("name") ? root.get("name").getAsString() : "Моя эмоция";
        float duration = root.has("duration") ? root.get("duration").getAsFloat() : 3.0f;
        CustomEmotion emotion = new CustomEmotion(name, duration);
        if (root.has("keys")) {
            for (JsonElement e : root.getAsJsonArray("keys")) {
                JsonObject o = e.getAsJsonObject();
                float t = o.has("t") ? o.get("t").getAsFloat() : 0.0f;
                float[] values = new float[FIELD_COUNT];
                if (o.has("v")) {
                    int i = 0;
                    for (JsonElement ve : o.getAsJsonArray("v")) {
                        if (i >= FIELD_COUNT) break;
                        values[i++] = ve.getAsFloat();
                    }
                }
                emotion.keyframes.add(new Keyframe(t, values));
            }
            emotion.sortKeys();
        }
        return emotion;
    }

    private void sortKeys() {
        this.keyframes.sort((a, b) -> Float.compare(a.time, b.time));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
