package pt.um.ucl.positioning.C03a.uwb.simulator;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the UWB simulation environment.
 * <p>
 * This class is responsible for creating and starting multiple
 * {@link SimulatorThread} instances, each managing a {@link VirtualAnchor}.
 * It also provides a main method to run the simulation from the command line.
 * 
 * @author Gustavo Oliveira
 * @version 0.1
 */
public class Simulator {
	/** List of all active simulator threads, each representing one anchor. */
	private List<SimulatorThread> simulatorThreads;
	/** The base URL of the server endpoint. */
	private String baseUrl;
	
	/**
	 * Constructs a new Simulator.
	 *
	 * @param baseUrl The base URL of the server to which the virtual anchors will connect.
	 */
	public Simulator(String baseUrl) {
		// Note: The base URL is currently hardcoded, overriding the parameter.
		this.baseUrl = "http://localhost:8080/C03a/";
		this.simulatorThreads = new ArrayList<>();
	}
	

	/**
	 * Starts the simulation by creating and starting a specified number of anchor threads.
	 *
	 * @param numberOfAnchors The number of virtual anchors to create and simulate.
	 */
	public void startSimulation(int numberOfAnchors) {
		System.out.println("Starting simulation with " + numberOfAnchors + " virtual anchors.");
		for (int i = 1; i <= numberOfAnchors; i++) {
			VirtualAnchor anchor = new VirtualAnchor("anchor" + i, System.currentTimeMillis(), System.currentTimeMillis());
			SimulatorThread thread = new SimulatorThread(anchor, this.baseUrl);
			simulatorThreads.add(thread);
			thread.start();
		}
	}
	
	/**
	 * Waits for all started simulator threads to complete their execution.
	 * This is a blocking call.
	 */
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
	
	/**
	 * Main entry point for the simulator.
	 * <p>
	 * Initializes and runs the simulation.
	 *
	 * @param args Command-line arguments.
	 * <ul>
	 * <li>{@code args[0]} (Optional): The base URL of the server. Defaults to "http://localhost:8080".</li>
	 * <li>{@code args[1]} (Optional): The number of anchors to simulate. Defaults to 4.</li>
	 * </ul>
	 */
	public static void main(String[] args) {
		String defaultUrl = "http://localhost:8080";
		int defaultAnchors = 4;
		
		String url = args.length > 0 ? args[0] : defaultUrl;
		int numberOfAnchors = args.length > 1 ? Integer.parseInt(args[1]) : defaultAnchors;
		
		Simulator simulator = new Simulator(url);
		simulator.startSimulation(numberOfAnchors);
		simulator.joinAllThreads();
	}
}