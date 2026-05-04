import os
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches

# =============================================================================
# CONFIGURATION
# =============================================================================
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SYNCHRONIZER_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "synchronizer"))
CONFIG_PATH = os.path.join(SYNCHRONIZER_DIR, "src", "main", "webapp", "WEB-INF", "config.properties")

# Defaults
SCAN_TIME = 200
SAFETY_BUFFER = 50
LOG_DIRECTORY = "C:/UWB_Logs"

# Dynamically load the configuration from the server config
try:
    with open(CONFIG_PATH, "r") as f:
        for line in f:
            line = line.strip()
            if line.startswith("am.scanTime="):
                SCAN_TIME = int(line.split("=")[1].strip())
            elif line.startswith("am.safetyBuffer="):
                SAFETY_BUFFER = int(line.split("=")[1].strip())
            elif line.startswith("log.directory="):
                # Using split("=", 1) in case the path has an equals sign in it somewhere
                LOG_DIRECTORY = line.split("=", 1)[1].strip()
    print(f"Loaded config: scanTime={SCAN_TIME}ms, safetyBuffer={SAFETY_BUFFER}ms, logDir={LOG_DIRECTORY}")
except Exception as e: 
    print(f"Could not load config ({e}). Using defaults.")

# Create the paths OS-agnostic style
SCHED_LOG = os.path.join(LOG_DIRECTORY, "server_scheduled.txt")
EXEC_LOG  = os.path.join(LOG_DIRECTORY, "anchor_executed.txt")

# =============================================================================
# DATA PARSING
# =============================================================================
def parse_logs():
    scheduled = []
    executed = []
    
    if os.path.exists(SCHED_LOG):
        with open(SCHED_LOG, 'r') as f:
            for line in f:
                parts = line.strip().split(',')
                if len(parts) == 4 and parts[0] == 'SCHED':
                    scheduled.append({"anchor": parts[1], "tag": parts[2], "ts": int(parts[3])})
    else:
        print(f"Could not find {SCHED_LOG}")
                    
    if os.path.exists(EXEC_LOG):
        with open(EXEC_LOG, 'r') as f:
            for line in f:
                parts = line.strip().split(',')
                if len(parts) == 4 and parts[0] == 'EXEC':
                    executed.append({"anchor": parts[1], "tag": parts[2], "ts": int(parts[3])})
    else:
        print(f"Could not find {EXEC_LOG}")
                    
    return scheduled, executed

# =============================================================================
# VISUALIZATION
# =============================================================================
def plot_drift():
    sched_data, exec_data = parse_logs()
    if not sched_data:
        print("No schedule data found. Make sure you ran the server with log.executionComparison=true")
        return

    # Match executions to schedules (closest timestamp for same anchor/tag)
    matches = [] 
    for s in sched_data:
        possible_execs = [e for e in exec_data if e["anchor"] == s["anchor"] and e["tag"] == s["tag"]]
        if possible_execs:
            closest = min(possible_execs, key=lambda e: abs(e["ts"] - s["ts"]))
            delta = closest["ts"] - s["ts"]
            matches.append((s["anchor"], s["tag"], s["ts"], closest["ts"], delta))
            exec_data.remove(closest) 

    if not matches:
        print("No overlapping execution data found to compare. Anchors might be failing to report.")
        return

    anchors = sorted(list(set([m[0] for m in matches])))
    t_start = min([m[2] for m in matches])
    
    fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(14, 10), facecolor="#0d1117", gridspec_kw={'height_ratios': [2, 1]})
    fig.canvas.manager.set_window_title('UWB Execution Drift Analysis')

    # --- TOP PLOT: Timeline Overlay ---
    ax1.set_facecolor("#0d1117")
    n = len(anchors)
    for idx, a_id in enumerate(anchors):
        y = n - idx
        ax1.axhline(y=y, color="#21262d", linewidth=1, zorder=1)
        
        anchor_matches = [m for m in matches if m[0] == a_id]
        for m in anchor_matches:
            x_sched = m[2] - t_start
            x_exec = m[3] - t_start
            
            rect = plt.Rectangle((x_sched, y - 0.35), SCAN_TIME, 0.70, fill=False, edgecolor="#3fb950", linewidth=1.5, linestyle='--', zorder=2)
            ax1.add_patch(rect)
            
            color = "#FF3B30" if abs(m[4]) > SAFETY_BUFFER else "#58a6ff"
            ax1.vlines(x=x_exec, ymin=y-0.35, ymax=y+0.35, color=color, linewidth=3, zorder=3)

    ax1.set_yticks([n - i for i in range(n)])
    ax1.set_yticklabels(anchors, color="#c9d1d9")
    ax1.set_title("Timeline Overlay (Dashed = Server Schedule | Solid Line = Anchor Actual Execution)", color="#c9d1d9")
    ax1.tick_params(colors="#8b949e")

    x_ticks_ms = ax1.get_xticks()
    ax1.set_xticklabels([f"{v/1000:.1f}s" for v in x_ticks_ms], color="#8b949e", fontsize=8)

    # --- BOTTOM PLOT: Jitter Tracking ---
    ax2.set_facecolor("#0d1117")
    ax2.axhline(y=0, color="#3fb950", linewidth=1, linestyle='--') 
    ax2.axhline(y=SAFETY_BUFFER, color="#FF3B30", linewidth=1, linestyle=':') 
    ax2.axhline(y=-SAFETY_BUFFER, color="#FF3B30", linewidth=1, linestyle=':') 

    colors = ['#58a6ff', '#ff7b72', '#d2a8ff', '#e3b341', '#3fb950']
    for idx, a_id in enumerate(anchors):
        anchor_matches = [m for m in matches if m[0] == a_id]
        anchor_matches.sort(key=lambda m: m[2]) 
        
        times = [(m[2] - t_start) / 1000.0 for m in anchor_matches]
        deltas = [m[4] for m in anchor_matches]
        
        ax2.plot(times, deltas, marker='o', markersize=4, linestyle='-', linewidth=1, label=a_id, color=colors[idx % len(colors)])

    ax2.set_title(f"Clock Drift / Execution Jitter (Safe Buffer: ±{SAFETY_BUFFER}ms)", color="#c9d1d9")
    ax2.set_ylabel("Drift Delta (ms)", color="#8b949e")
    ax2.set_xlabel("Time (seconds)", color="#8b949e")
    ax2.tick_params(colors="#8b949e")
    ax2.legend(facecolor="#161b22", edgecolor="#30363d", labelcolor="#c9d1d9")

    for ax in [ax1, ax2]:
        for spine in ["top", "right"]: ax.spines[spine].set_visible(False)
        for spine in ["left", "bottom"]: ax.spines[spine].set_color("#30363d")

    plt.tight_layout()
    plt.show()

if __name__ == "__main__":
    plot_drift()