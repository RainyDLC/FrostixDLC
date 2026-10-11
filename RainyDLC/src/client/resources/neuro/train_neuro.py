#!/usr/bin/env python3
"""
Обучение нейро-ротаций AttackAura на Python (numpy) — тот же алгоритм,
что и встроенный Java-класс AimNet (формат aimnet-v2).

Читает датасет, записанный модом:
    config/frostix/neuroaura/<name>.json      (формат aimdataset-v2)
тренирует MLP 6 -> 24 -> 24 -> 2 (tanh hidden, linear out, Adam)
и пишет веса:
    config/frostix/neuroaura/<name>.weights.json (формат aimnet-v2)

Использование:
    python scripts/train_neuro.py <название> [--epochs 250] [--lr 0.003]

Датасет можно указать и прямым путём к .json файлу.

Всё выполняется локально на твоём ПК: скрипт читает файл датасета
и пишет файл весов. В сеть ничего не уходит, в процесс игры ничего
не инжектится — античиту тут не за что зацепиться.
"""

import argparse
import json
import os
import sys

try:
    import numpy as np
except ImportError:
    sys.exit("Нужен numpy: pip install numpy")

IN, H1, H2, OUT = 6, 24, 24, 2


def load_dataset(path):
    with open(path, "r", encoding="utf-8") as f:
        root = json.load(f)
    X = np.array(root["inputs"], dtype=np.float64)
    Y = np.array(root["outputs"], dtype=np.float64)
    assert X.shape[1] == IN and Y.shape[1] == OUT, "не тот формат датасета"
    # отсев мусора: нечисла и безумные довороты
    mask = np.isfinite(X).all(axis=1) & np.isfinite(Y).all(axis=1)
    mask &= (np.abs(Y) <= 45.0).all(axis=1)
    return X[mask], Y[mask]


def init_params(rng):
    def xavier(fan_in, fan_out, shape):
        s = np.sqrt(2.0 / (fan_in + fan_out))
        return rng.normal(0.0, s, size=shape)

    return {
        "w1": xavier(IN, H1, (H1, IN)), "b1": np.zeros(H1),
        "w2": xavier(H1, H2, (H2, H1)), "b2": np.zeros(H2),
        "w3": xavier(H2, OUT, (OUT, H2)), "b3": np.zeros(OUT),
    }


def forward(p, X):
    z1 = X @ p["w1"].T + p["b1"]
    a1 = np.tanh(z1)
    z2 = a1 @ p["w2"].T + p["b2"]
    a2 = np.tanh(z2)
    out = a2 @ p["w3"].T + p["b3"]
    return out, (X, a1, a2)


def compute_calib(X, Y):
    """Калибровка моторики игрока (та же, что AimNet.computeCalib в Java).

    Остаточная ошибка: насколько игрок оставляет прицел мимо точки после
    своего доворота, когда «на цели». Паузы: серии тиков почти без доворота
    в слежении. Возвращает dict для секции "calib" весов.
    """
    calib = {
        "resid_yaw_p50": 0.5, "resid_yaw_p90": 1.4,
        "resid_pitch_p50": 0.4, "resid_pitch_p90": 1.1,
        "pause_p50_ticks": 3.0, "pause_p90_ticks": 8.0,
        "pause_per_100": 7.0,
    }
    yaw_err, pitch_err = X[:, 0], X[:, 1]
    d_yaw, d_pitch = Y[:, 0], Y[:, 1]
    settled = (np.abs(yaw_err) < 8.0) & (np.abs(pitch_err) < 8.0)
    n_settled = int(settled.sum())
    if n_settled < 50:
        return calib
    res_yaw = np.abs(yaw_err[settled] - d_yaw[settled])
    res_pitch = np.abs(pitch_err[settled] - d_pitch[settled])
    calib["resid_yaw_p50"] = float(np.percentile(res_yaw, 50))
    calib["resid_yaw_p90"] = float(np.percentile(res_yaw, 90))
    calib["resid_pitch_p50"] = float(np.percentile(res_pitch, 50))
    calib["resid_pitch_p90"] = float(np.percentile(res_pitch, 90))
    # Паузы: серии подряд идущих тиков почти без доворота (во временном порядке)
    still = (np.hypot(d_yaw, d_pitch) < 0.25) & settled
    runs, cur = [], 0
    for v in still:
        if v:
            cur += 1
        elif cur:
            runs.append(cur)
            cur = 0
    if cur:
        runs.append(cur)
    if runs:
        calib["pause_p50_ticks"] = float(np.percentile(runs, 50))
        calib["pause_p90_ticks"] = float(np.percentile(runs, 90))
    calib["pause_per_100"] = float(len(runs) * 100.0 / n_settled)
    return calib


def train(X, Y, epochs=250, lr=0.003, batch=64, seed=7):
    # Калибровка под игрока — до аугментации, во временном порядке
    calib = compute_calib(X, Y)
    # Горизонтальная симметрия
    X_sym = X.copy()
    X_sym[:, 0] = -X_sym[:, 0]  # -yawErr
    X_sym[:, 3] = -X_sym[:, 3]  # -tgtYawVel
    Y_sym = Y.copy()
    Y_sym[:, 0] = -Y_sym[:, 0]  # -dYaw
    X = np.concatenate([X, X_sym], axis=0)
    Y = np.concatenate([Y, Y_sym], axis=0)

    rng = np.random.default_rng(seed)
    n = len(X)
    # z-нормализация по train
    x_mean, x_std = X.mean(axis=0), X.std(axis=0)
    y_mean, y_std = Y.mean(axis=0), Y.std(axis=0)

    # Обнуляем средние по симметричным осям (устраняет дрифт)
    x_mean[0] = 0.0
    x_mean[3] = 0.0
    y_mean[0] = 0.0
    y_mean[1] = 0.0

    x_std[x_std < 1e-4] = 1.0
    y_std[y_std < 1e-4] = 1.0
    Xn = (X - x_mean) / x_std
    Yn = (Y - y_mean) / y_std

    # train/val 90/10
    idx = rng.permutation(n)
    nv = min(n // 10, 1500) if n >= 400 else 0
    tr, va = idx[: n - nv], idx[n - nv:]

    p = init_params(rng)
    m = {k: np.zeros_like(v) for k, v in p.items()}
    v = {k: np.zeros_like(v) for k, v in p.items()}
    b1, b2, eps = 0.9, 0.999, 1e-8
    step = 0

    # Косинусный спад learning rate — как в Java-версии
    lr_min, lr_max = 0.0004, 0.0035

    for e in range(epochs):
        cur_lr = lr_min + 0.5 * (lr_max - lr_min) * (1.0 + np.cos(np.pi * e / epochs))
        rng.shuffle(tr)
        for s in range(0, len(tr), batch):
            b = tr[s: s + batch]
            out, (Xb, a1, a2) = forward(p, Xn[b])
            # backprop, MSE
            dO = 2.0 * (out - Yn[b]) / OUT
            gw3 = dO.T @ a2
            gb3 = dO.sum(axis=0)
            da2 = dO @ p["w3"] * (1.0 - a2 ** 2)
            gw2 = da2.T @ a1
            gb2 = da2.sum(axis=0)
            da1 = da2 @ p["w2"] * (1.0 - a1 ** 2)
            gw1 = da1.T @ Xb
            gb1 = da1.sum(axis=0)
            grads = {"w1": gw1, "b1": gb1, "w2": gw2, "b2": gb2,
                     "w3": gw3, "b3": gb3}
            step += 1
            bc1, bc2 = 1 - b1 ** step, 1 - b2 ** step
            for k in p:
                m[k] = b1 * m[k] + (1 - b1) * grads[k]
                v[k] = b2 * v[k] + (1 - b2) * grads[k] ** 2
                p[k] -= cur_lr * (m[k] / bc1) / (np.sqrt(v[k] / bc2) + eps)

        out_tr, _ = forward(p, Xn[tr])
        train_loss = float(np.mean((out_tr - Yn[tr]) ** 2))
        if nv:
            out_va, _ = forward(p, Xn[va])
            val_loss = float(np.mean((out_va - Yn[va]) ** 2))
        else:
            val_loss = float("nan")
        print(f"epoch {e + 1}/{epochs}  train={train_loss:.4f}  val={val_loss:.4f}",
              flush=True)

    mags = np.linalg.norm(Y, axis=1)
    auto_max = float(np.clip(np.percentile(mags, 95) * 20.0, 60.0, 720.0))
    meta = {
        "trained_samples": n,
        "final_train_loss": train_loss,
        "final_val_loss": val_loss,
        "auto_max_deg_per_sec": auto_max,
    }
    norms = {"x_mean": x_mean, "x_std": x_std, "y_mean": y_mean, "y_std": y_std}
    return p, norms, meta, calib


def save_weights(path, p, norms, meta, calib):
    root = {
        "format": "aimnet-v2",
        "in": IN, "h1": H1, "h2": H2, "out": OUT,
        "x_mean": norms["x_mean"].tolist(),
        "x_std": norms["x_std"].tolist(),
        "y_mean": norms["y_mean"].tolist(),
        "y_std": norms["y_std"].tolist(),
        "w1": p["w1"].ravel().tolist(),
        "b1": p["b1"].tolist(),
        "w2": p["w2"].ravel().tolist(),
        "b2": p["b2"].tolist(),
        "w3": p["w3"].ravel().tolist(),
        "b3": p["b3"].tolist(),
        "meta": meta,
        "calib": calib,
    }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(root, f)


def main():
    ap = argparse.ArgumentParser(description="Обучение нейро-ротаций AttackAura (aimnet-v2)")
    ap.add_argument("name", help="название обучения или путь к датасету .json")
    ap.add_argument("--epochs", type=int, default=250)
    ap.add_argument("--lr", type=float, default=0.003)
    args = ap.parse_args()

    if args.name.endswith(".json") and os.path.isfile(args.name):
        ds_path = args.name
    else:
        ds_path = os.path.join("config", "frostix", "neuroaura", args.name + ".json")
        if not os.path.isfile(ds_path):
            alt = os.path.join(os.getcwd(), ds_path)
            ds_path = alt if os.path.isfile(alt) else ds_path
    if not os.path.isfile(ds_path):
        sys.exit(f"Датасет не найден: {ds_path}")

    X, Y = load_dataset(ds_path)
    print(f"сэмплов: {len(X)}")
    if len(X) < 120:
        sys.exit("Мало данных: нужно минимум 120 сэмплов")
    if len(X) > 12000:
        sel = np.linspace(0, len(X) - 1, 12000).astype(int)
        X, Y = X[sel], Y[sel]
        print("урезано до 12000 сэмплов")

    p, norms, meta, calib = train(X, Y, epochs=args.epochs, lr=args.lr)
    out_path = ds_path[: -5] + ".weights.json"
    save_weights(out_path, p, norms, meta, calib)
    print(f"веса записаны: {out_path}")
    print(f"итог: train loss={meta['final_train_loss']:.4f} "
          f"val loss={meta['final_val_loss']:.4f} "
          f"max speed={meta['auto_max_deg_per_sec']:.0f}°/s")


if __name__ == "__main__":
    main()
