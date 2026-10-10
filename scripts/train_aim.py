#!/usr/bin/env python3
"""
Обучение нейронки AimAssist на Python (numpy).

Читает датасет, записанный модом:
    config/frostix/aimassist/<name>.json
тренирует MLP 6 -> 12 -> 2 (tanh hidden, linear out) и пишет веса
    config/frostix/aimassist/<name>.weights.json
в формате, который один в один понимает Java-класс AimBrain.

Использование:
    python scripts/train_aim.py <название> [--epochs 300] [--lr 0.01]

Датасет можно указать и прямым путём к .json файлу.
"""

import argparse
import json
import math
import os
import sys

try:
    import numpy as np
except ImportError:
    sys.exit("Нужен numpy: pip install numpy")

INPUT_SIZE = 6
HIDDEN_SIZE = 12
OUTPUT_SIZE = 2


def find_dataset(name: str) -> str:
    if os.path.isfile(name):
        return name
    candidates = [
        os.path.join(os.path.expanduser("~"), ".minecraft", "config", "frostix", "aimassist", name + ".json"),
        os.path.join("config", "frostix", "aimassist", name + ".json"),
        os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "config", "frostix", "aimassist", name + ".json")),
    ]
    # %APPDATA%/.minecraft для Windows
    appdata = os.environ.get("APPDATA")
    if appdata:
        candidates.insert(0, os.path.join(appdata, ".minecraft", "config", "frostix", "aimassist", name + ".json"))
    for c in candidates:
        if os.path.isfile(c):
            return c
    sys.exit(f"Датасет не найден: {name}\nПроверял: " + "\n".join(candidates))


def load_dataset(path: str):
    with open(path, encoding="utf-8") as f:
        root = json.load(f)
    x = np.array(root["inputs"], dtype=np.float64)
    y = np.array(root["outputs"], dtype=np.float64)
    assert x.shape[1] == INPUT_SIZE and y.shape[1] == OUTPUT_SIZE, "Неверный формат датасета"
    return x, y


def main() -> None:
    ap = argparse.ArgumentParser(description="Обучение нейронки AimAssist")
    ap.add_argument("name", help="Название обучения (или путь к .json)")
    ap.add_argument("--epochs", type=int, default=300)
    ap.add_argument("--lr", type=float, default=0.01)
    ap.add_argument("--batch", type=int, default=32)
    args = ap.parse_args()

    dataset_path = find_dataset(args.name)
    print(f"Датасет: {dataset_path}")
    x, y = load_dataset(dataset_path)
    n = len(x)
    if n < 10:
        sys.exit(f"Слишком мало сэмплов: {n} (нужно хотя бы 10)")
    print(f"Сэмплов: {n}")

    # Нормализация (та же, что в AimBrain)
    x_mean, x_std = x.mean(axis=0), x.std(axis=0)
    y_mean, y_std = y.mean(axis=0), y.std(axis=0)
    x_std[x_std < 1e-6] = 1.0
    y_std[y_std < 1e-6] = 1.0
    xn = (x - x_mean) / x_std
    yn = (y - y_mean) / y_std

    rng = np.random.default_rng(42)
    w1 = rng.normal(0, math.sqrt(2 / (INPUT_SIZE + HIDDEN_SIZE)), (HIDDEN_SIZE, INPUT_SIZE))
    b1 = np.zeros(HIDDEN_SIZE)
    w2 = rng.normal(0, math.sqrt(2 / (HIDDEN_SIZE + OUTPUT_SIZE)), (OUTPUT_SIZE, HIDDEN_SIZE))
    b2 = np.zeros(OUTPUT_SIZE)

    for epoch in range(1, args.epochs + 1):
        perm = rng.permutation(n)
        for start in range(0, n, args.batch):
            idx = perm[start:start + args.batch]
            xb, yb = xn[idx], yn[idx]
            m = len(idx)

            pre = xb @ w1.T + b1          # (m, H)
            h = np.tanh(pre)              # (m, H)
            pred = h @ w2.T + b2          # (m, O)

            d_out = 2.0 * (pred - yb) / m         # dL/dpred
            g_w2 = d_out.T @ h
            g_b2 = d_out.sum(axis=0)
            d_h = (d_out @ w2) * (1.0 - h ** 2)   # tanh'
            g_w1 = d_h.T @ xb
            g_b1 = d_h.sum(axis=0)

            w2 -= args.lr * g_w2
            b2 -= args.lr * g_b2
            w1 -= args.lr * g_w1
            b1 -= args.lr * g_b1

        if epoch % 20 == 0 or epoch == args.epochs:
            h = np.tanh(xn @ w1.T + b1)
            pred = h @ w2.T + b2
            loss = float(np.mean((pred - yn) ** 2))
            print(f"Эпоха {epoch}/{args.epochs}  loss={loss:.6f}")

    out_path = dataset_path[:-5] + ".weights.json"
    h = np.tanh(xn @ w1.T + b1)
    final_loss = float(np.mean(((h @ w2.T + b2) - yn) ** 2))
    print(f"Финальный loss: {final_loss:.6f} на {n} сэмплах")
    # Авто-параметры из записи (та же формула, что в AimBrain.computeAutoParams):
    # макс. скорость — 95-й перцентиль скорости доворотов, плавность — из ровности движений.
    mags = np.hypot(y[:, 0], y[:, 1])  # °/тик
    auto_speed_dps = min(360.0, max(30.0, float(np.percentile(mags, 95)) * 20.0))
    mean_mag = float(mags.mean())
    jerk = float(np.mean(np.abs(np.diff(mags)))) if n > 1 else 0.0
    steadiness = mean_mag / (mean_mag + 3.0 * jerk + 1e-9)
    auto_smooth_alpha = min(0.9, max(0.15, steadiness))
    print(f"Авто: скорость {auto_speed_dps:.0f}°/с, плавность {auto_smooth_alpha:.2f}")
    payload = {
        "input_size": INPUT_SIZE,
        "hidden_size": HIDDEN_SIZE,
        "output_size": OUTPUT_SIZE,
        "x_mean": x_mean.tolist(),
        "x_std": x_std.tolist(),
        "y_mean": y_mean.tolist(),
        "y_std": y_std.tolist(),
        "w1": w1.tolist(),
        "b1": b1.tolist(),
        "w2": w2.tolist(),
        "b2": b2.tolist(),
        "final_loss": final_loss,
        "samples": n,
        "auto_speed_dps": auto_speed_dps,
        "auto_smooth_alpha": auto_smooth_alpha,
    }
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(payload, f)
    print(f"Веса сохранены: {out_path}")
    print("Перезайди в мир (или перезапусти клиент) — AimAssist подхватит профиль.")


if __name__ == "__main__":
    main()
