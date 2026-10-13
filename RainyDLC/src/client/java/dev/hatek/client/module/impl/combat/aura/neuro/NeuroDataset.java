package dev.hatek.client.module.impl.combat.aura.neuro;

import java.util.ArrayList;
import java.util.List;

/**
 * Датасет обучения нейро-ротаций: пары (признаки цели -&gt; доворот игрока)
 * плюс точки попаданий игрока — куда его прицел реально бьёт по хитбоксу
 * цели в момент клика (относительные координаты бокса).
 */
public final class NeuroDataset {
    private final String name;
    private final List<NeuroSample> samples = new ArrayList<>();
    private final List<float[]> hitPoints = new ArrayList<>();

    public NeuroDataset(String name) {
        this.name = name;
    }

    public String name() {
        return this.name;
    }

    public synchronized void add(NeuroSample sample) {
        this.samples.add(sample);
    }

    public synchronized int size() {
        return this.samples.size();
    }

    public synchronized List<NeuroSample> copy() {
        return new ArrayList<>(this.samples);
    }

    /** Точка попадания: [rx, ry, rz], rx/rz в [-1,1], ry в [0,1]. */
    public synchronized void addHitPoint(float rx, float ry, float rz) {
        if (this.hitPoints.size() < 20000) {
            this.hitPoints.add(new float[]{rx, ry, rz});
        }
    }

    public synchronized List<float[]> hitPoints() {
        return new ArrayList<>(this.hitPoints);
    }

    public synchronized void clear() {
        this.samples.clear();
        this.hitPoints.clear();
    }
}
