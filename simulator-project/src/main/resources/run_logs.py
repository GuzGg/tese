import os
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches

# =============================================================================
# CONFIGURATION
# =============================================================================
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../../.."))

# ---> HIGH-SPEED DEFAULTS <---
SCAN_TIME = 10
SAFETY_BUFFER = 10
LOG_DIRECTORY = "C:/UWB_Logs"

# Robust config hunting (same as simulator)
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
                        SCAN_TIME = int(line.split("=")[1].strip())
                    elif line.startswith("am.safetyBuffer="):
                        SAFETY_BUFFER = int(line.split("=")[1].strip())
                    elif line.startswith("log.directory="):
                        LOG_DIRECTORY = line.split("=", 1)[1].strip()
            print(f"Loaded config from {path}: scanTime={SCAN_TIME}ms, safetyBuffer={SAFETY_BUFFER}ms, logDir={LOG_DIRECTORY}")
            config_found = True
            break
        except Exception as e: 
            pass

if not config_found:
    print(f"Could not find config.properties. Using defaults: scanTime={SCAN_TIME}ms, safetyBuffer={SAFETY_BUFFER}ms, logDir={LOG_DIRECTORY}")

# Create the paths OS-agnostic style
SCHED_LOG = os.path.join(LOG_DIRECTORY, "server_scheduled.txt")
EXEC_LOG  = os.path.join(LOG_DIRECTORY, "anchor_executed.txt")
DUR_LOG   = os.path.join(LOG_DIRECTORY, "round_durations.txt")

# =============================================================================
# DATA PARSING
# =============================================================================
def parse_logs():
    scheduled = []
    executed = []
    durations = []
    
    if os.path.exists(SCHED_LOG):
        with open(SCHED_LOG, 'r') as f:
            for line in f:
                parts = line.strip().split(',')
                # NEW SCHEMA: SCHED, roundId, anchor, tag, ts
                if len(parts) == 5 and parts[0] == 'SCHED':
                    scheduled.append({
                        "roundId": int(parts[1]), "anchor": parts[2], 
                        "tag": parts[3], "ts": int(parts[4])
                    })
    else:
        print(f"Could not find {SCHED_LOG}")
                    
    if os.path.exists(EXEC_LOG):
        with open(EXEC_LOG, 'r') as f:
            for line in f:
                parts = line.strip().split(',')
                # NEW SCHEMA: EXEC, roundId, anchor, tag, ts
                if len(parts) == 5 and parts[0] == 'EXEC':
                    executed.append({
                        "roundId": int(parts[1]), "anchor": parts[2], 
                        "tag": parts[3], "ts": int(parts[4])
                    })
    else:
        print(f"Could not find {EXEC_LOG}")

    if os.path.exists(DUR_LOG):
        with open(DUR_LOG, 'r') as f:
            for line in f:
                parts = line.strip().split(',')
                # NEW SCHEMA: DURATION, anchor, actualDuration
                if len(parts) == 3 and parts[0] == 'DURATION':
                    durations.append({
                        "anchor": parts[1], "duration": int(parts[2])
                    })
                    
    return scheduled, executed, durations

# =============================================================================
# VISUALIZATION
# =============================================================================
def plot_drift():
    sched_data, exec_data, dur_data = parse_logs()
    
    if not sched_data:
        print("No schedule data found. Make sure you ran the server with log.executionComparison=true")
        return

    # 1. Match executions to schedules using EXACT roundId
    matches = []
    missed = []
    orphans = list(exec_data)

    for s in sched_data:
        # Strict Match: RoundID + Anchor + Tag
        matched_execs = [e for e in orphans if e["roundId"] == s["roundId"] and e["anchor"] == s["anchor"] and e["tag"] == s["tag"]]
        
        if matched_execs:
            closest = matched_execs[0]
            delta = closest["ts"] - s["ts"]
            matches.append({
                "roundId": s["roundId"], "anchor": s["anchor"], "tag": s["tag"],
                "sched_ts": s["ts"], "exec_ts": closest["ts"], "delta": delta
            })
            orphans.remove(closest) 
        else:
            missed.append(s)

    if not matches and not missed:
        print("No parsable execution or schedule data found.")
        return

    anchors = sorted(list(set([m["anchor"] for m in matches] + [m["anchor"] for m in missed])))
    t_start = min([m["sched_ts"] for m in matches] + ([m["ts"] for m in missed] if missed else []))
    
    # 3-Row Grid
    fig, (ax1, ax2, ax3) = plt.subplots(3, 1, figsize=(14, 12), facecolor="#0d1117", gridspec_kw={'height_ratios': [2, 1, 1]})
    fig.canvas.manager.set_window_title('UWB Execution Drift & Duration Analysis')

    # --- TOP PLOT: Timeline Overlay ---
    ax1.set_facecolor("#0d1117")
    n = len(anchors)
    for idx, a_id in enumerate(anchors):
        y = n - idx
        ax1.axhline(y=y, color="#21262d", linewidth=1, zorder=1)
        
        anchor_matches = [m for m in matches if m["anchor"] == a_id]
        for m in anchor_matches:
            x_sched = m["sched_ts"] - t_start
            x_exec = m["exec_ts"] - t_start
            
            # The green box width is now properly scaled to SCAN_TIME (10ms)
            rect = plt.Rectangle((x_sched, y - 0.35), SCAN_TIME, 0.70, fill=False, edgecolor="#3fb950", linewidth=1.5, linestyle='--', zorder=2)
            ax1.add_patch(rect)
            
            color = "#FF3B30" if abs(m["delta"]) > SAFETY_BUFFER else "#58a6ff"
            ax1.vlines(x=x_exec, ymin=y-0.35, ymax=y+0.35, color=color, linewidth=3, zorder=3)
        
        anchor_missed = [m for m in missed if m["anchor"] == a_id]
        for m in anchor_missed:
            x_sched = m["ts"] - t_start
            rect = plt.Rectangle((x_sched, y - 0.35), SCAN_TIME, 0.70, fill=True, color="#440000", edgecolor="#FF3B30", linewidth=1.5, linestyle=':', zorder=2)
            ax1.add_patch(rect)
            ax1.text(x_sched + SCAN_TIME/2, y, "MISSED", color="#FF3B30", fontsize=7, fontweight="bold", ha='center', va='center', rotation=45, zorder=4)

    ax1.set_yticks([n - i for i in range(n)])
    ax1.set_yticklabels(anchors, color="#c9d1d9")
    ax1.set_title(f"Timeline Overlay (Matched: {len(matches)} | Missed: {len(missed)} | Orphans: {len(orphans)})", color="#c9d1d9")
    ax1.tick_params(colors="#8b949e")

    x_ticks_ms = ax1.get_xticks()
    ax1.set_xticklabels([f"{v/1000:.1f}s" for v in x_ticks_ms], color="#8b949e", fontsize=8)

    # --- MIDDLE PLOT: Jitter Tracking ---
    ax2.set_facecolor("#0d1117")
    ax2.axhline(y=0, color="#3fb950", linewidth=1, linestyle='--') 
    ax2.axhline(y=SAFETY_BUFFER, color="#FF3B30", linewidth=1, linestyle=':') 
    ax2.axhline(y=-SAFETY_BUFFER, color="#FF3B30", linewidth=1, linestyle=':') 

    colors = ['#58a6ff', '#ff7b72', '#d2a8ff', '#e3b341', '#3fb950']
    for idx, a_id in enumerate(anchors):
        anchor_matches = [m for m in matches if m["anchor"] == a_id]
        anchor_matches.sort(key=lambda m: m["sched_ts"]) 
        
        times = [(m["sched_ts"] - t_start) / 1000.0 for m in anchor_matches]
        deltas = [m["delta"] for m in anchor_matches]
        
        ax2.plot(times, deltas, marker='o', markersize=4, linestyle='-', linewidth=1, label=a_id, color=colors[idx % len(colors)])

    ax2.set_title(f"Clock Drift / Execution Jitter (Strict Round ID Match)", color="#c9d1d9")
    ax2.set_ylabel("Drift Delta (ms)", color="#8b949e")
    ax2.tick_params(colors="#8b949e")
    ax2.legend(facecolor="#161b22", edgecolor="#30363d", labelcolor="#c9d1d9")
    
    # --- BOTTOM PLOT: Round Durations ---
    ax3.set_facecolor("#0d1117")
    if dur_data:
        for idx, a_id in enumerate(anchors):
            anchor_durs = [d["duration"] for d in dur_data if d["anchor"] == a_id]
            ax3.plot(range(1, len(anchor_durs) + 1), anchor_durs, marker='s', markersize=4, linestyle='-', linewidth=1.5, label=a_id, color=colors[idx % len(colors)])
            
        ax3.set_title("Actual Hardware Ranging Sequence Duration", color="#c9d1d9")
        ax3.set_ylabel("Duration (ms)", color="#8b949e")
        ax3.set_xlabel("Sequential Measurement Round", color="#8b949e")
        ax3.tick_params(colors="#8b949e")
        ax3.legend(facecolor="#161b22", edgecolor="#30363d", labelcolor="#c9d1d9")
    else:
        ax3.text(0.5, 0.5, "No Duration Data Found", color="#c9d1d9", ha='center', va='center', transform=ax3.transAxes)

    for ax in [ax1, ax2, ax3]:
        for spine in ["top", "right"]: ax.spines[spine].set_visible(False)
        for spine in ["left", "bottom"]: ax.spines[spine].set_color("#30363d")

    plt.tight_layout()
    plt.show()

if __name__ == "__main__":
    plot_drift()