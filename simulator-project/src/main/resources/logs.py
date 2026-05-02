import matplotlib.pyplot as plt
import re
import glob
import os

def generate_visualization(log_folder_path):
    anchor_data = {}
    all_timestamps = []
    pattern = re.compile(r"Anchor\s+(\d+).*?tag\s+(\d+)\s+in\s+(\d+)")

    # 1. Load and Parse
    log_files = glob.glob(os.path.join(log_folder_path, "*.txt"))
    if not log_files:
        print(f"Error: No logs found in {log_folder_path}")
        return

    for file_path in log_files:
        with open(file_path, 'r') as f:
            for line in f:
                match = pattern.search(line)
                if match:
                    a_id, t_id, ts = map(int, match.groups())
                    if a_id not in anchor_data: anchor_data[a_id] = []
                    anchor_data[a_id].append({'ts': ts, 'tag': t_id})
                    all_timestamps.append(ts)

    if not all_timestamps: return
    
    # 2. Setup Dimensions
    sorted_anchors = sorted(anchor_data.keys())
    t_start = min(all_timestamps)
    all_timestamps.sort()
    
    # We define a "slot" as the duration of one measurement
    # You can manually set this to your 'time slot' value from your formula
    slot_duration = (all_timestamps[-1] - t_start) / (len(all_timestamps) or 1) * 0.5

    fig, ax = plt.subplots(figsize=(14, 2 + len(sorted_anchors)), facecolor='#000000')
    ax.set_facecolor('#000000')

    # 3. Collision Logic & Plotting
    # We'll track every occupied interval to find overlaps
    # Format: (start, end, anchor_id, tag_id)
    occupied_slots = []
    for a_id in sorted_anchors:
        for entry in anchor_data[a_id]:
            occupied_slots.append((entry['ts'], entry['ts'] + slot_duration, a_id, entry['tag']))

    for idx, a_id in enumerate(sorted_anchors):
        y_pos = len(sorted_anchors) - idx
        ax.axhline(y=y_pos, color='#333333', linewidth=1, zorder=1)
        
        for entry in anchor_data[a_id]:
            norm_ts = entry['ts'] - t_start
            
            # Check for collisions with OTHER anchors at the same time
            is_collision = False
            for start, end, other_a, other_t in occupied_slots:
                if other_a != a_id: # Only care if a different anchor is talking
                    # Check if intervals overlap
                    if max(entry['ts'], start) < min(entry['ts'] + slot_duration, end):
                        is_collision = True
                        break
            
            # Styling based on collision
            box_color = '#FF3B30' if is_collision else '#1e1e1e' # Red if collision
            edge_color = '#FF9500' if is_collision else 'white'
            
            rect = plt.Rectangle((norm_ts, y_pos - 0.25), slot_duration, 0.5, 
                                 edgecolor=edge_color, facecolor=box_color, 
                                 linewidth=1.5 if is_collision else 1, zorder=3)
            ax.add_patch(rect)
            
            ax.text(norm_ts + (slot_duration/2), y_pos, f'T{entry["tag"]}', 
                    color='white', ha='center', va='center', fontweight='bold', fontsize=8, zorder=4)

    # 4. Final Polish
    ax.set_yticks([len(sorted_anchors) - i for i in range(len(sorted_anchors))])
    ax.set_yticklabels([f'Anchor {a}' for a in sorted_anchors], color='white')
    ax.tick_params(axis='x', colors='#888888')
    for s in ['top', 'right', 'left']: ax.spines[s].set_visible(False)
    ax.spines['bottom'].set_color('#444444')

    plt.title("Timing Allocation (Red indicates Slot Collision)", color='white', pad=20)
    plt.tight_layout()
    plt.show()