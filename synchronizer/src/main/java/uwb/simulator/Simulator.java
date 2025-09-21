package uwb.simulator;

import java.util.ArrayList;
import java.util.List;

public class Simulator {
	private List<SimulatorThread> simulatorThreads;
	private String baseUrl;
	
	public Simulator(String baseUrl) {
		this.baseUrl = baseUrl;
		this.simulatorThreads = new ArrayList<>();
	}
	

	public void startSimulation(int numberOfAnchors) {
		System.out.println("Starting simulation with " + numberOfAnchors + " virtual anchors.");
		for (int i = 1; i <= numberOfAnchors; i++) {
			VirtualAnchor anchor = new VirtualAnchor("anchor" + i, System.currentTimeMillis(), System.currentTimeMillis());
			SimulatorThread thread = new SimulatorThread(anchor, this.baseUrl);
			simulatorThreads.add(thread);
			thread.start();
		}
	}
	
	public void joinAllThreads() {
		for (SimulatorThread thread : simulatorThreads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				System.err.println("Main thread interrupted while waiting for a simulator thread.");
				e.printStackTrace();
			}
		}
		System.out.println("All simulation threads have finished.");
	}
	
	public static void main(String[] args) {
		String defaultUrl = "http://localhost:8080";
		int defaultAnchors = 3;
		
		String url = args.length > 0 ? args[0] : defaultUrl;
		int numberOfAnchors = args.length > 1 ? Integer.parseInt(args[1]) : defaultAnchors;
		
		Simulator simulator = new Simulator(url);
		simulator.startSimulation(numberOfAnchors);
		simulator.joinAllThreads();
	}
}
