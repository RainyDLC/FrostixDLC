package rtx.kimiko.api.modules.impl.Visuals.emotions;

/**
 * Общий контракт анимации эмоции.
 * Реализуют как встроенные эмоции ({@link Emotion}), так и
 * пользовательские, созданные в редакторе ({@link CustomEmotion}).
 */
public interface EmotionAnim {
    /** Отображаемое имя эмоции. */
    String displayName();

    /** Длительность в секундах. */
    float duration();

    /** Заполняет позу для момента времени {@code time} (секунды). */
    void apply(EmotionPose pose, float time);

    /** true для эмоций, созданных пользователем в редакторе. */
    default boolean isCustom() {
        return false;
    }
}
