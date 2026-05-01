import subprocess
import time
import sys

# 1. Define your list of anchor names here
anchor_names = [
    "Anchor 1",
    "Anchor 2",
    "Anchor 3",
    "Anchor 4"
]

# 2. Define how to execute your Java program
# IMPORTANT: Adjust this based on how your Java project is compiled.
# If using a compiled .jar file, it would look like: 
# base_command = ["java", "-jar", "your-simulator.jar"]
# If running from a directory of compiled .class files (e.g., a 'bin' folder):
base_command = [
    "java", 
    "-cp", "bin:lib/*", # Update your classpath as needed (use ';' instead of ':' on Windows)
    "pt.um.ucl.positioning.C03a.uwb.simulator.Simulator"
]

# Store the process objects so we can close them later
processes = []

def start_anchors():
    print(f"Starting {len(anchor_names)} virtual anchors...\n")
    
    for name in anchor_names:
        # Append the anchor name as the first argument (args[0] in your Java main method)
        command = base_command + [name]
        
        print(f"Launching: {name}")
        # Popen runs the command in the background without blocking Python
        process = subprocess.Popen(command)
        processes.append(process)
        
        # A small delay prevents all anchors from hammering the registration endpoint at the exact same millisecond
        time.sleep(0.5)

    print("\n✅ All anchors are running in the background.")
    print("Press Ctrl+C to stop all instances.\n")

    try:
        # Keep the Python script alive so it can catch the shutdown signal
        while True:
            time.sleep(1)
            
    except KeyboardInterrupt:
        # Graceful shutdown when user presses Ctrl+C
        print("\n\nInterrupt received! Stopping all virtual anchors...")
        for p in processes:
            p.terminate() 
            
        # Wait a moment for processes to close cleanly
        for p in processes:
            p.wait()
            
        print("All processes terminated. Goodbye!")

if __name__ == "__main__":
    start_anchors()