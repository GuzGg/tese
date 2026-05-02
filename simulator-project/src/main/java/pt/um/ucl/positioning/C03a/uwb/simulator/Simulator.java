package pt.um.ucl.positioning.C03a.uwb.simulator;

/**
 * Manages the single UWB simulation environment.
 */
public class Simulator {
	private String baseUrl;
	
	public Simulator(String baseUrl) {
		this.baseUrl = baseUrl;
	}
	
	public void startSimulation(String anchorName) {
		System.out.println("Starting simulation for virtual anchor: " + anchorName);
		
		VirtualAnchor anchor = new VirtualAnchor(anchorName, System.currentTimeMillis(), System.currentTimeMillis());
		
		// Run synchronously instead of spinning up a Thread
		SimulatorClient client = new SimulatorClient(anchor, this.baseUrl);
		client.startClient(); 
	}
	
	/**
	 * Main entry point for the simulator.
	 *
	 * @param args Command-line arguments.
	 * <ul>
	 * <li>{@code args[0]} (Optional): The name of the anchor. Defaults to "Anchor 1".</li>
	 * <li>{@code args[1]} (Optional): The base URL of the server. Defaults to "http://localhost:8080/C03a/".</li>
	 * </ul>
	 */
	public static void main(String[] args) {
		String defaultAnchorName = "Anchor 1";
		String defaultUrl = "http://localhost:8080/C03a/";
		
		String anchorName = args.length > 0 ? args[0] : defaultAnchorName;
		String url = args.length > 1 ? args[1] : defaultUrl;
		
		Simulator simulator = new Simulator(url);
		simulator.startSimulation(anchorName);
	}
}