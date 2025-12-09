package pt.um.ucl.positioning.C03a.uwb.simulator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONObject;

/**
 * A thread that manages the simulation lifecycle for a single {@link VirtualAnchor}.
 * * @author Gustavo Oliveira
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
	
	public SimulatorThread(VirtualAnchor anchor, String url) {
		this.anchor = anchor;
		this.baseUrl = url;
	}
	
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
	 * Sends the initial registration request to the server using application/json.
	 */
	private JSONObject sendRegistrationRequest() {
		try {
			URL url = new URI(this.baseUrl + PATH_BOOT).toURL();
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			
			// CHANGE 1: Set Content-Type to application/json
			connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			connection.setRequestProperty("Accept", "application/json");
			connection.setDoOutput(true);
			
			JSONObject payload = new JSONObject();
			payload.put("anchorID", anchor.getDeviceName());
			
			// CHANGE 2: Send raw JSON string bytes (removed URLEncoder and 'jsondata=' prefix)
			byte[] postData = payload.toString().getBytes(StandardCharsets.UTF_8);

			try (OutputStream os = connection.getOutputStream()) {
				os.write(postData);
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
	 * Sends an action report (scan or measure) to the server using application/json.
	 */
	private JSONObject sendActionRequest(JSONObject reply, URL url) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		
		// CHANGE 1: Set Content-Type to application/json
		connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		connection.setRequestProperty("Accept", "application/json");
		connection.setDoOutput(true);
		
		// CHANGE 2: Send raw JSON string bytes (removed URLEncoder and 'jsondata=' prefix)
		byte[] postData = reply.toString().getBytes(StandardCharsets.UTF_8);
		
		try (OutputStream os = connection.getOutputStream()) {
			os.write(postData);
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