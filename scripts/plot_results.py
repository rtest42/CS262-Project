"""Plot one or more Monitor CSVs on the same axes.

Usage:
    python scripts/plot_results.py results/push_sum_*.csv results/pairwise_*.csv
    python scripts/plot_results.py                      # plots every CSV in results/
    python scripts/plot_results.py a.csv b.csv --out results/plots --show

Needs: pip install pandas matplotlib
"""
import argparse
import glob
from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd

PANELS = [
    # (x column, y column, log y axis, title, x label, y label)
    ("tSec",     "rmsError",          True,  "Error vs time",            "time (s)",     "RMS error vs original mean"),
    ("messages", "rmsError",          True,  "Error vs messages sent",   "messages",     "RMS error vs original mean"),
    ("tSec",     "rmsErrorSurvivors", True,  "Error vs survivors' mean", "time (s)",     "RMS error vs survivors' mean"),
    ("tSec",     "bias",              False, "Bias vs time",             "time (s)",     "mean estimate - original mean"),
]


def load(path):
    df = pd.read_csv(path)
    return df.replace([np.inf, -np.inf], np.nan)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csvs", nargs="*", help="CSV files (default: results/*.csv)")
    ap.add_argument("--out", default="results/plots", help="directory for the PNG")
    ap.add_argument("--show", action="store_true", help="also open the figure window")
    args = ap.parse_args()

    files = args.csvs or sorted(glob.glob("results/*.csv"))
    if not files:
        raise SystemExit("No CSV files found. Run an experiment first.")

    runs = {Path(f).stem: load(f) for f in files}

    fig, axes = plt.subplots(2, 2, figsize=(13, 9))
    for ax, (xc, yc, logy, title, xl, yl) in zip(axes.flat, PANELS):
        for label, df in runs.items():
            y = df[yc].where(df[yc] > 0) if logy else df[yc]    # log axes cannot show zeros
            ax.plot(df[xc], y, label=label, linewidth=1.4)
        if logy:
            ax.set_yscale("log")
        ax.set_title(title)
        ax.set_xlabel(xl)
        ax.set_ylabel(yl)
        ax.grid(True, which="both", alpha=0.3)
    axes[0, 0].legend(fontsize=7)
    fig.tight_layout()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / "comparison.png"
    fig.savefig(out_file, dpi=150)
    print(f"saved {out_file}")

    # End-of-run summary, including the conservation check (totalS / totalW over alive nodes)
    cols = ["alive", "messages", "rmsError", "rmsErrorSurvivors", "bias", "totalS", "totalW"]
    summary = pd.DataFrame({name: df.iloc[-1][cols] for name, df in runs.items()}).T
    with pd.option_context("display.float_format", "{:.4g}".format, "display.width", 160):
        print(summary)

    if args.show:
        plt.show()


if __name__ == "__main__":
    main()
