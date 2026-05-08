import subprocess
import time
import os
import re
import glob
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches

# =============================================================================
# CONFIGURATION
# =============================================================================
ANCHOR_NAMES   = ["Anchor 1", "Anchor 2", "Anchor 3", "Anchor 4"]
INITIAL_TAGS   = 2
SERVER_URL     = "http://localhost:8080/C03a/"

SCRIPT_DIR   = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../../.."))
LOG_DIR      = PROJECT_ROOT   

JAVA_EXE     = r"C:\Users\gus23\.p2\pool\plugins\org.eclipse.justj.openjdk.hotspot.jre.full.win32.x86_64_21.0.6.v20250130-0529\jre\bin\java.exe"
BASE_CMD     = [JAVA_EXE, "-cp", "bin;lib/*", "pt.um.ucl.positioning.C03a.uwb.simulator.Simulator"]

# =============================================================================
# VISUALIZATION
# =============================================================================
def generate_visualization(log_folder_path):
    # --- Parse Logs ---
    anchor_data: dict[int, list[dict]] = {}

    log_files = glob.glob(os.path.join(log_folder_path, "*_logs.txt"))
    if not log_files:
        print(f"[viz] No *_logs.txt log files found in: {log_folder_path}")
        return

    # Parsing the CSV-style EXPECTED log lines
    for file_path in log_files:
        with open(file_path, "r") as f:
            for line in f:
                parts = line.strip().split(',')
                # Format: EXPECTED,{roundId},{anchorName},{tagId},Wait:{wait}ms,Target:{targetTimestamp}
                if len(parts) >= 6 and parts[0] == 'EXPECTED':
                    try:
                        a_id_str = parts[2].replace("Anchor", "").strip()
                        a_id = int(a_id_str)
                        t_id = int(parts[3])
                        ts   = int(parts[5].replace("Target:", "").strip())
                        
                        anchor_data.setdefault(a_id, []).append({"ts": ts, "tag": t_id})
                    except ValueError as e:
                        print(f"[viz] Skipped malformed line: {line.strip()} -> {e}")
                        continue

    if not anchor_data:
        print("[viz] Log files found but no valid 'EXPECTED' lines — check the log format.")
        return

    for events in anchor_data.values():
        events.sort(key=lambda e: e["ts"])

    # --- Dynamically Load Java Config ---
    # ---> HIGH-SPEED DEFAULTS <---
    scan_time = 10     
    safety_buffer = 10  

    # Hunt for the config file in standard Eclipse web project locations
    possible_paths = [
        os.path.join(PROJECT_ROOT, "WEB-INF", "config.properties"),
        os.path.join(PROJECT_ROOT, "WebContent", "WEB-INF", "config.properties"),
        os.path.join(PROJECT_ROOT, "src", "main", "webapp", "WEB-INF", "config.properties"),
        os.path.abspath(os.path.join(SCRIPT_DIR, "..", "synchronizer", "src", "main", "webapp", "WEB-INF", "config.properties"))
    ]

    config_found = False
    for path in possible_paths:
        if os.path.exists(path):
            try:
                with open(path, "r") as f:
                    for line in f:
                        line = line.strip()
                        if line.startswith("am.scanTime="):
                            scan_time = int(line.split("=")[1].strip())
                        elif line.startswith("am.safetyBuffer="):
                            safety_buffer = int(line.split("=")[1].strip())
                print(f"[viz] Loaded config from {path}: scanTime={scan_time}ms, safetyBuffer={safety_buffer}ms")
                config_found = True
                break
            except Exception as e:
                pass

    if not config_found:
        print(f"[viz] Could not find config.properties. Using defaults: scanTime={scan_time}ms, safetyBuffer={safety_buffer}ms")

    # The actual physical time the hardware is scanning/transmitting
    box_width = scan_time
    # The total grid slot size including buffers
    padded_slot = scan_time + (2 * safety_buffer)

    # --- Layout ---
    sorted_anchors = sorted(anchor_data.keys())
    all_ts = [e["ts"] for evts in anchor_data.values() for e in evts]
    t_start = min(all_ts)
    t_end   = max(all_ts) + padded_slot

    fig, ax = plt.subplots(
        figsize=(max(14, (t_end - t_start) / padded_slot * 0.4), 2 + len(sorted_anchors)),
        facecolor="#0d1117"
    )
    ax.set_facecolor("#0d1117")

    # Build flat list of all occupied intervals for true collision detection
    occupied = [
        (e["ts"], e["ts"] + box_width, a_id, e["tag"])
        for a_id, evts in anchor_data.items()
        for e in evts
    ]

    # --- Draw ---
    n = len(sorted_anchors)
    for idx, a_id in enumerate(sorted_anchors):
        y = n - idx                                    
        ax.axhline(y=y, color="#21262d", linewidth=1, zorder=1)

        for entry in anchor_data[a_id]:
            x = entry["ts"] - t_start                  

            # Collision = any OTHER anchor is scanning during our specific box_width window
            collision = any(
                other_a != a_id
                and max(entry["ts"], s) < min(entry["ts"] + box_width, end)
                for s, end, other_a, _ in occupied
            )

            face  = "#FF3B30" if collision else "#238636"
            edge  = "#FF9500" if collision else "#3fb950"
            lw    = 1.8        if collision else 1.0

            rect = plt.Rectangle(
                (x, y - 0.35), box_width, 0.70,
                facecolor=face, edgecolor=edge, linewidth=lw, zorder=3
            )
            ax.add_patch(rect)
            ax.text(
                x + box_width / 2, y,
                f"T{entry['tag']}",
                color="white", ha="center", va="center",
                fontsize=7, fontweight="bold", zorder=4
            )

    # --- Axes & labels ---
    ax.set_xlim(-padded_slot * 0.5, t_end - t_start + padded_slot)
    ax.set_ylim(0.3, n + 0.7)
    ax.set_yticks([n - i for i in range(n)])
    ax.set_yticklabels([f"Anchor {a}" for a in sorted_anchors], color="#c9d1d9", fontsize=10)
    ax.tick_params(axis="x", colors="#8b949e")

    x_ticks_ms = ax.get_xticks()
    ax.set_xticklabels([f"{v/1000:.1f}s" for v in x_ticks_ms], color="#8b949e", fontsize=8)
    ax.set_xlabel("Time (relative to first measurement)", color="#8b949e", fontsize=9)

    for spine in ["top", "right", "left"]:
        ax.spines[spine].set_visible(False)
    ax.spines["bottom"].set_color("#30363d")

    green_patch = mpatches.Patch(color="#238636", label="Clean slot")
    red_patch   = mpatches.Patch(color="#FF3B30", label="Collision")
    ax.legend(handles=[green_patch, red_patch], loc="upper right",
              facecolor="#161b22", edgecolor="#30363d", labelcolor="#c9d1d9", fontsize=9)

    total   = len(all_ts)
    collisions = sum(
        1 for a_id, evts in anchor_data.items()
        for e in evts
        if any(
            other_a != a_id
            and max(e["ts"], s) < min(e["ts"] + box_width, end)
            for s, end, other_a, _ in occupied
        )
    )
    plt.title(
        f"UWB Timing Allocation  |  {total} measurements  |  "
        f"{collisions} collision(s) ({100*collisions/total:.1f}%)",
        color="#c9d1d9", pad=16, fontsize=11
    )
    plt.tight_layout()
    plt.show()

# =============================================================================
# SIMULATOR RUNNER
# =============================================================================
def start_anchors():
    processes = []

    print(f"Project root : {PROJECT_ROOT}")
    print(f"Log directory: {LOG_DIR}")
    print(f"Launching {len(ANCHOR_NAMES)} virtual anchor(s) with {INITIAL_TAGS} tag(s) each...\n")

    # Flush old logs so they don't pollute the new graph
    for f in glob.glob(os.path.join(LOG_DIR, "*_logs.txt")):
        os.remove(f)

    for name in ANCHOR_NAMES:
        cmd = BASE_CMD + [name, SERVER_URL, str(INITIAL_TAGS)]
        print(f"  + {name}")
        proc = subprocess.Popen(cmd, cwd=PROJECT_ROOT)
        processes.append(proc)
        time.sleep(0.5)   

    print("\nAll anchors running. Press Ctrl+C to stop and view results.\n")

    try:
        while True:
            time.sleep(1)

    except KeyboardInterrupt:
        print("\nStopping all virtual anchors...")
        for p in processes:
            p.terminate()
        for p in processes:
            p.wait()
        print("All processes terminated.")

    print("\nGenerating timing visualization...")
    generate_visualization(LOG_DIR)

if __name__ == "__main__":
    start_anchors()