package pt.um.ucl.positioning.C03a.uwb.simulator;

/**
 * Manages the single UWB simulation environment.
 */
public class Simulator {
    private String baseUrl;

    public Simulator(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void startSimulation(String anchorName, int initialTags) {
        System.out.println("Starting simulation for virtual anchor: " + anchorName
            + " with " + initialTags + " tag(s).");

        VirtualAnchor anchor = new VirtualAnchor(
            anchorName,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            initialTags
        );

        SimulatorClient client = new SimulatorClient(anchor, this.baseUrl);
        client.startClient();
    }

    /**
     * Main entry point for the simulator.
     *
     * @param args Command-line arguments.
     * <ul>
     * <li>{@code args[0]} (Optional): Anchor name. Defaults to "Anchor 1".</li>
     * <li>{@code args[1]} (Optional): Base URL. Defaults to "http://localhost:8080/C03a/".</li>
     * <li>{@code args[2]} (Optional): Number of initial tags. Defaults to 1.</li>
     * </ul>
     */
    public static void main(String[] args) {
        String anchorName  = args.length > 0 ? args[0] : "Anchor 1";
        String url         = args.length > 1 ? args[1] : "http://localhost:8080/C03a/";
        int    initialTags = args.length > 2 ? Integer.parseInt(args[2]) : 1;

        Simulator simulator = new Simulator(url);
        simulator.startSimulation(anchorName, initialTags);
    }
}