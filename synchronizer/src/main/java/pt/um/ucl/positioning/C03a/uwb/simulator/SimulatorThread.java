package pt.um.ucl.positioning.C03a.uwb.simulator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

/**
 * A thread that manages the simulation lifecycle for a single {@link VirtualAnchor}.
 * <p>
 * This class handles all network communication with the central server, including:
 * <ul>
 * <li>Registering the anchor with the server ({@link #sendRegistrationRequest()}).</li>
 * <li>Entering a loop to send reports (scan/measure) and receive new actions.</li>
 * <li>Calling the anchor's {@link VirtualAnchor#VirtualBehaviour(JSONObject)} to get simulated data.</li>
 * </ul>
 * 
 * @author Gustavo Oliveira
 * @version 0.1
 */
public class SimulatorThread extends Thread {
	private VirtualAnchor anchor;
	private String baseUrl;

	/** API endpoint path for anchor registration. */
	private static final String PATH_BOOT = "/anchorRegistration";
	/** API endpoint path for measurement reports. */
	private static final String PATH_MEASURE = "/measurementReport";
	/** API endpoint path for scan reports. */
	private static final String PATH_SCAN = "/scanReport";
	
	/**
	 * Constructs a new simulator thread.
	 *
	 * @param anchor The {@link VirtualAnchor} instance this thread will manage.
	 * @param url The base URL of the server.
	 */
	public SimulatorThread(VirtualAnchor anchor, String url) {
		this.anchor = anchor;
		this.baseUrl = url;
	}
	
	/**
	 * The main execution loop for the anchor thread.
	 * <p>
	 * It first attempts to register the anchor. If successful, it enters a
	 * continuous loop where it:
	 * <ol>
	 * <li>Delegates to the {@link VirtualAnchor#VirtualBehaviour(JSONObject)} to get a report.</li>
	 * <li>Sends this report to the appropriate server endpoint.</li>
	 * <li>Receives the next action from the server.</li>
	 * <li>Waits for 1 second.</li>
	 * </ol>
	 * The loop terminates if the server returns a null action or an error occurs.
	 */
	public void run() {
		
		JSONObject action = this.sendRegistrationRequest();
		
		if (action == null) {
			System.err.println("Initial registration failed. Stopping thread.");
			return;
		}

		while (action != null) {
			
			try {
				String actionToExecute = action.getString("actionToExecute");
				String endpointPath = "measure".equalsIgnoreCase(actionToExecute) ? PATH_MEASURE : PATH_SCAN;
				URL requestUrl = new URI(this.baseUrl + endpointPath).toURL();
				JSONObject currentReport = this.anchor.VirtualBehaviour(action);
				System.out.println(actionToExecute);
				System.out.println(currentReport);
				action = this.sendActionRequest(currentReport, requestUrl);
				
				// Wait 1 second before next action
				Thread.sleep(1000);

			} catch (MalformedURLException | URISyntaxException e) {
				System.err.println("URL error. Stopping thread.");
				e.printStackTrace();
			} catch (Exception e) {
				System.err.println("An error occurred during communication. Stopping thread.");
				e.printStackTrace();
				action = null;
			}
		}
		
		System.out.println("Anchor " + anchor.getDeviceName() + " simulation finished.");
	}
	
	/**
	 * Sends the initial registration request to the server.
	 * <p>
	 * This method sends a POST request to the {@link #PATH_BOOT} endpoint
	 * with the anchor's ID.
	 *
	 * @return The first {@link JSONObject} action command from the server upon
	 * successful registration, or {@code null} if registration fails.
	 */
	private JSONObject sendRegistrationRequest() {
		try {
			URL url = new URI(this.baseUrl + PATH_BOOT).toURL();
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connection.setDoOutput(true);
			
			JSONObject payload = new JSONObject();
			payload.put("anchorID", anchor.getDeviceName());
			String encodedPayload = "jsondata=" + URLEncoder.encode(payload.toString(), StandardCharsets.UTF_8.toString());

			try (OutputStream os = connection.getOutputStream()) {
				os.write(encodedPayload.getBytes(StandardCharsets.UTF_8));
			}
			
			int responseCode = connection.getResponseCode();
			System.out.println("Registration response for " + anchor.getDeviceName() + ": " + responseCode);
			
			if (responseCode == HttpURLConnection.HTTP_OK) {
				try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
					StringBuilder response = new StringBuilder();
					String responseLine;
					while ((responseLine = br.readLine()) != null) {
						response.append(responseLine.trim());
					}
					return new JSONObject(response.toString());
				}
			} else {
				System.err.println("Service returned non-OK status code: " + responseCode);
				return null;
			}
	
		} catch (Exception e) {
			System.err.println("Registration failed for anchor " + anchor.getDeviceName() + ": " + e.getMessage());
			return null;
		}
	}
	
	/**
	 * Sends an action report (scan or measure) to the server and receives the next action.
	 * <p>
	 * This method sends a POST request to the specified URL (either
	 * {@link #PATH_MEASURE} or {@link #PATH_SCAN}) with the provided JSON payload.
	 *
	 * @param reply The {@link JSONObject} payload (the report) to send to the server.
	 * @param url The fully constructed {@link URL} of the server endpoint to send the report to.
	 * @return The next {@link JSONObject} action command from the server,
	 * or {@code null} if the request fails or returns a non-OK status.
	 * @throws Exception if an error occurs during the HTTP communication.
	 */
	private JSONObject sendActionRequest(JSONObject reply, URL url) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		connection.setDoOutput(true);
		
		String encodedPayload = "jsondata=" + URLEncoder.encode(reply.toString(), StandardCharsets.UTF_8.toString());
		
		try (OutputStream os = connection.getOutputStream()) {
			os.write(encodedPayload.getBytes(StandardCharsets.UTF_8));
		}

		int responseCode = connection.getResponseCode();

		if (responseCode == HttpURLConnection.HTTP_OK) {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
				StringBuilder response = new StringBuilder();
				String responseLine;
				while ((responseLine = br.readLine()) != null) {
					response.append(responseLine.trim());
				}
				
				return new JSONObject(response.toString());
			}
		} else {
			System.err.println("Service returned non-OK status code: " + responseCode);
			return null;
		}
	}
}