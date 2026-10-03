"""
Aura Neuro Model Trainer
Trains a GRU-MDN (Gated Recurrent Unit with Mixture Density Network)
on Minecraft PvP mouse movement telemetry data collected by NeuroDataRecorder.
"""

import os
import sys
import json
import math
import argparse
import numpy as np

try:
    import torch
    import torch.nn as nn
    import torch.optim as optim
    from torch.utils.data import Dataset, DataLoader
except ImportError:
    print("[Trainer] Error: PyTorch is not installed. Please run: pip install torch numpy")
    sys.exit(1)


def asinh_norm(x):
    return np.arcsinh(x / 5.0) / 3.0


class TelemetryDataset(Dataset):
    def __init__(self, csv_paths, seq_len=30, features_dim=17):
        self.seq_len = seq_len
        self.features_dim = features_dim
        self.sequences = []
        self.targets = []

        for p in csv_paths:
            self._load_file(p)

        print(f"[Trainer] Loaded {len(self.sequences)} training sequences from {len(csv_paths)} files.")

    def _load_file(self, path):
        if not os.path.isfile(path):
            return

        data = []
        with open(path, "r", encoding="utf-8") as f:
            header = f.readline().strip().split(",")
            col_map = {name: idx for idx, name in enumerate(header)}

            req_cols = ["yaw", "pitch", "dyaw", "dpitch", "has", "rx", "ry", "rz", "dist", "vis", "on", "cd", "ground", "hurt"]
            if not all(c in col_map for c in req_cols):
                print(f"[Trainer] Warning: {path} has invalid columns, skipping.")
                return

            for line in f:
                parts = line.strip().split(",")
                if len(parts) != len(header):
                    continue
                try:
                    row = [float(x) for x in parts]
                    data.append(row)
                except ValueError:
                    continue

        if len(data) < self.seq_len + 5:
            return

        data = np.array(data, dtype=np.float32)

        yaw_idx = col_map["yaw"]
        pitch_idx = col_map["pitch"]
        dyaw_idx = col_map["dyaw"]
        dpitch_idx = col_map["dpitch"]
        has_idx = col_map["has"]
        rx_idx = col_map["rx"]
        ry_idx = col_map["ry"]
        rz_idx = col_map["rz"]
        dist_idx = col_map["dist"]
        on_idx = col_map["on"]
        cd_idx = col_map["cd"]
        ground_idx = col_map["ground"]
        hurt_idx = col_map["hurt"]

        # Calculate target error angles
        total_rows = len(data)
        feats = np.zeros((total_rows, self.features_dim), dtype=np.float32)

        prev_ey = 0.0
        prev_ep = 0.0
        prev_dy = 0.0
        prev_dp = 0.0

        for i in range(total_rows):
            cur_yaw = data[i, yaw_idx]
            cur_pitch = data[i, pitch_idx]
            cur_dy = data[i, dyaw_idx]
            cur_dp = data[i, dpitch_idx]
            has = data[i, has_idx] > 0.5

            if has:
                rx = data[i, rx_idx]
                ry = data[i, ry_idx]
                rz = data[i, rz_idx]
                dist_xz = math.sqrt(rx * rx + rz * rz)
                target_yaw = math.degrees(math.atan2(rz, rx)) - 90.0
                target_pitch = -math.degrees(math.atan2(ry, dist_xz))

                err_y = ((target_yaw - cur_yaw + 180.0) % 360.0) - 180.0
                err_p = target_pitch - cur_pitch
            else:
                err_y = 0.0
                err_p = 0.0

            d_err_y = ((err_y - prev_ey + 180.0) % 360.0) - 180.0
            d_err_p = err_p - prev_ep

            dist = data[i, dist_idx]
            on_target = data[i, on_idx]
            cd = data[i, cd_idx]
            ground = data[i, ground_idx]

            feats[i, 0] = asinh_norm(err_y)
            feats[i, 1] = asinh_norm(err_p)
            feats[i, 2] = asinh_norm(d_err_y)
            feats[i, 3] = asinh_norm(d_err_p)
            feats[i, 4] = asinh_norm(cur_dy)
            feats[i, 5] = asinh_norm(cur_dp)
            feats[i, 6] = asinh_norm(cur_dy)
            feats[i, 7] = asinh_norm(cur_dp)
            feats[i, 8] = asinh_norm(prev_dy)
            feats[i, 9] = asinh_norm(prev_dp)
            feats[i, 10] = asinh_norm(err_y / 5.0)
            feats[i, 11] = asinh_norm(err_p / 5.0)
            feats[i, 12] = math.log(max(dist, 0.05) + 0.5) / 2.0
            feats[i, 13] = 0.0
            feats[i, 14] = 1.0 if on_target > 0.5 else 0.0
            feats[i, 15] = cd
            feats[i, 16] = 1.0 if ground > 0.5 else 0.0

            prev_ey = err_y
            prev_ep = err_p
            prev_dy = cur_dy
            prev_dp = cur_dp

        # Collect targets (the actual dyaw, dpitch that human moved)
        targets = data[:, [dyaw_idx, dpitch_idx]]

        for i in range(0, total_rows - self.seq_len, self.seq_len // 2):
            self.sequences.append(feats[i : i + self.seq_len])
            self.targets.append(targets[i : i + self.seq_len])

    def __len__(self):
        return len(self.sequences)

    def __getitem__(self, idx):
        return torch.tensor(self.sequences[idx], dtype=torch.float32), torch.tensor(self.targets[idx], dtype=torch.float32)


class NeuroGRUModel(nn.Module):
    def __init__(self, in_features=17, hidden_size=32, mixtures=3):
        super().__init__()
        self.in_features = in_features
        self.hidden_size = hidden_size
        self.mixtures = mixtures

        # PyTorch GRU has weight_ih [3*hidden, in] and weight_hh [3*hidden, hidden]
        self.gru = nn.GRU(in_features, hidden_size, batch_first=True)

        # Output dimension: 1 (threshold) + mixtures * 6
        # 6 params per mixture: pi, mu_yaw, mu_pitch, log_sigma_yaw, log_sigma_pitch, correlation
        self.out_dim = 1 + mixtures * 6
        self.out = nn.Linear(hidden_size, self.out_dim)

    def forward(self, x, h=None):
        gru_out, h_n = self.gru(x, h)
        pred = self.out(gru_out)
        return pred, h_n


def mdn_loss(pred, target, mixtures=3):
    """
    Negative log-likelihood loss for 2D Gaussian Mixture
    """
    # pred: [B, T, 1 + mix*6]
    # target: [B, T, 2]
    B, T, _ = pred.shape
    y = target.unsqueeze(2) # [B, T, 1, 2]

    # Reshape mixture params
    mix_params = pred[:, :, 1:].view(B, T, mixtures, 6)
    logits_pi = mix_params[:, :, :, 0]
    log_pi = torch.log_softmax(logits_pi, dim=-1) # [B, T, mix]

    mu_x = mix_params[:, :, :, 1]
    mu_y = mix_params[:, :, :, 2]
    mu = torch.stack([mu_x, mu_y], dim=-1) # [B, T, mix, 2]

    # Stdev with softplus for numerical stability
    sigma_x = torch.exp(torch.clamp(mix_params[:, :, :, 3], -4.0, 4.0)) + 1e-4
    sigma_y = torch.exp(torch.clamp(mix_params[:, :, :, 4], -4.0, 4.0)) + 1e-4

    rho = torch.tanh(mix_params[:, :, :, 5]) * 0.95

    # 2D Bivariate Gaussian Log-Likelihood
    diff = y - mu
    dx = diff[:, :, :, 0]
    dy = diff[:, :, :, 1]

    z = (dx / sigma_x) ** 2 + (dy / sigma_y) ** 2 - 2 * rho * (dx / sigma_x) * (dy / sigma_y)
    one_minus_rho2 = torch.clamp(1.0 - rho ** 2, min=1e-5)
    log_norm = math.log(2.0 * math.pi) + torch.log(sigma_x) + torch.log(sigma_y) + 0.5 * torch.log(one_minus_rho2)

    log_prob = -0.5 * (z / one_minus_rho2) - log_norm
    total_log_prob = torch.logsumexp(log_pi + log_prob, dim=-1)

    return -torch.mean(total_log_prob)


def export_to_json(model, out_path):
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)

    # In PyTorch GRU:
    # weight_ih_l0: [3*hidden, in_features]
    # weight_hh_l0: [3*hidden, hidden_size]
    # bias_ih_l0: [3*hidden]
    # bias_hh_l0: [3*hidden]
    # In PyTorch, GRU gates order is: reset (r), update/input (z), new/candidate (n)
    # This directly maps to wi, wh, bi, bh!

    wi = model.gru.weight_ih_l0.detach().cpu().numpy().tolist()
    wh = model.gru.weight_hh_l0.detach().cpu().numpy().tolist()
    bi = model.gru.bias_ih_l0.detach().cpu().numpy().tolist()
    bh = model.gru.bias_hh_l0.detach().cpu().numpy().tolist()

    w = model.out.weight.detach().cpu().numpy().tolist()
    b = model.out.bias.detach().cpu().numpy().tolist()

    data = {
        "kind": "gru",
        "features": model.in_features,
        "hidden": model.hidden_size,
        "mix": model.mixtures,
        "squash": "sinh",
        "units": "deg",
        "limits": [179.0, 90.0],
        "knob": [[1.0, 1, 1.0], [0.7, 2, 0.6], [0.7, 4, 0.45], [0.7, 8, 0.25]],
        "freezeCut": 40,
        "error": [0.001305, 0.130478, 0.185287, 0.228073, 0.265126, 0.3, 0.35, 0.4, 0.45, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0],
        "style": {
            "slack": 0.45,
            "temperature": 0.7,
            "speed": 1.1,
            "memory": 0.0,
            "memory2": 0.0,
            "hold": 1,
            "dequant": 1
        },
        "gru": {
            "wi": wi,
            "wh": wh,
            "bi": bi,
            "bh": bh
        },
        "out": {
            "w": w,
            "b": b
        }
    }

    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f)

    print(f"[Trainer] Model weights successfully exported to: {out_path}")


def main():
    parser = argparse.ArgumentParser(description="Train Neuro Aura GRU model")
    parser.add_argument("--data", type=str, required=True, help="Path to CSV dataset file or data directory")
    parser.add_argument("--out", type=str, required=True, help="Path to output JSON model file")
    parser.add_argument("--epochs", type=int, default=30, help="Number of training epochs")
    parser.add_argument("--batch-size", type=int, default=64, help="Batch size")
    parser.add_argument("--lr", type=float, default=1e-3, help="Learning rate")
    parser.add_argument("--hidden", type=int, default=32, help="Hidden size of GRU")
    parser.add_argument("--mix", type=int, default=3, help="Number of Gaussian mixtures")
    parser.add_argument("--session", type=str, default="train", help="Session label")

    args = parser.parse_args()

    csv_files = []
    if os.path.isdir(args.data):
        for root, _, files in os.walk(args.data):
            for file in files:
                if file.endswith(".csv"):
                    csv_files.append(os.path.join(root, file))
    elif os.path.isfile(args.data):
        csv_files.append(args.data)
    else:
        print(f"[Trainer] Error: Data path '{args.data}' does not exist.")
        sys.exit(1)

    if not csv_files:
        print(f"[Trainer] Error: No CSV files found in '{args.data}'.")
        sys.exit(1)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"[Trainer] Using device: {device}")

    dataset = TelemetryDataset(csv_files, seq_len=30, features_dim=17)
    if len(dataset) == 0:
        print("[Trainer] Error: Dataset is empty. Make sure you recorded enough PvP ticks.")
        sys.exit(1)

    dataloader = DataLoader(dataset, batch_size=args.batch_size, shuffle=True, drop_last=True)

    model = NeuroGRUModel(in_features=17, hidden_size=args.hidden, mixtures=args.mix).to(device)
    optimizer = optim.AdamW(model.parameters(), lr=args.lr, weight_decay=1e-4)
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

    print(f"[Trainer] Starting training for {args.epochs} epochs...")

    for epoch in range(1, args.epochs + 1):
        model.train()
        total_loss = 0.0
        batches = 0

        for batch_x, batch_y in dataloader:
            batch_x = batch_x.to(device)
            batch_y = batch_y.to(device)

            optimizer.zero_grad()
            pred, _ = model(batch_x)
            loss = mdn_loss(pred, batch_y, mixtures=args.mix)

            if not torch.isnan(loss) and not torch.isinf(loss):
                loss.backward()
                nn.utils.clip_grad_norm_(model.parameters(), max_norm=5.0)
                optimizer.step()
                total_loss += loss.item()
                batches += 1

        scheduler.step()
        avg_loss = total_loss / max(1, batches)
        print(f"[Trainer] Epoch {epoch:02d}/{args.epochs:02d} - Loss: {avg_loss:.4f} (lr: {scheduler.get_last_lr()[0]:.6f})")

    export_to_json(model, args.out)
    print("[Trainer] Training complete! You can now load the model in client via '.neuro load <model>'.")


if __name__ == "__main__":
    main()