package dev.hatek.client.module.impl.combat.aimassist;

import java.util.ArrayList;
import java.util.List;

/**
 * Датасет обучения наводки: только пары (признаки цели -&gt; доворот игрока).
 * Сами удары (клики) не записываются и не изучаются — только наводка.
 */
public final class AimDataset {
    private final String name;
    private final List<AimSample> samples = new ArrayList<>();

    public AimDataset(String name) {
        this.name = name;
    }

    public String name() {
        return this.name;
    }

    public synchronized void add(AimSample sample) {
        this.samples.add(sample);
    }

    public synchronized int size() {
        return this.samples.size();
    }

    public synchronized List<AimSample> copy() {
        return new ArrayList<>(this.samples);
    }

    public synchronized void clear() {
        this.samples.clear();
    }
}
