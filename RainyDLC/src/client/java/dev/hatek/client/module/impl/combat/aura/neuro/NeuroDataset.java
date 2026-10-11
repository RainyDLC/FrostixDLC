package dev.hatek.client.module.impl.combat.aura.neuro;

import java.util.ArrayList;
import java.util.List;

/**
 * Датасет обучения нейро-ротаций: только пары
 * (признаки цели -&gt; доворот игрока). Сами удары не записываются —
 * только наводка, как игрок обычно пвпешится.
 */
public final class NeuroDataset {
    private final String name;
    private final List<NeuroSample> samples = new ArrayList<>();

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

    public synchronized void clear() {
        this.samples.clear();
    }
}
