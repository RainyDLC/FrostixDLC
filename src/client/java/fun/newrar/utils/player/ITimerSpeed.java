package fun.newrar.utils.player;

public interface ITimerSpeed {
    float getSpeed();
    void setSpeed(float speed);

    default void resetSpeed() {
        setSpeed(1.0F);
    }
}

