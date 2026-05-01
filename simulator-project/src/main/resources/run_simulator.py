import subprocess
import time
import sys
import matplotlib.pyplot as plt
import re
import glob
import os

# --- 1. CONFIGURATION ---
anchor_names = ["Anchor 1", "Anchor 2", "Anchor 3", "Anchor 4"]
base_command = ["java", "-cp", "bin:lib/*", "pt.um.ucl.positioning.C03a.uwb.simulator.Simulator"]
processes = []

# --- 2. YOUR VISUALIZATION FUNCTION ---
def generate_visualization(log_folder_path):
    # [Insert your exact code from source 4 here]
    # ...
    # Make sure to keep the plt.show() at the end so the window stays open!
    pass 

# --- 3. SIMULATOR RUNNER ---
def start_anchors():
    print(f"Starting {len(anchor_names)} virtual anchors...\n")
    
    for name in anchor_names:
        command = base_command + [name]
        print(f"Launching: {name}")
        process = subprocess.Popen(command)
        processes.append(process)
        time.sleep(0.5)

    print("\n✅ All anchors are running in the background.")
    print("Press Ctrl+C to stop simulation and view results.\n")

    try:
        while True:
            time.sleep(1)
            
    except KeyboardInterrupt:
        print("\n\nInterrupt received! Stopping all virtual anchors...")
        for p in processes:
            p.terminate() 
            
        for p in processes:
            p.wait()
            
        print("All processes terminated.")
        
        # --- NEW: TRIGGER VISUALIZATION ON EXIT ---
        print("\n📊 Generating log visualization...")
        # Assumes the .txt log files are saved in the current directory (".")
        generate_visualization(".") 

if __name__ == "__main__":
    start_anchors()