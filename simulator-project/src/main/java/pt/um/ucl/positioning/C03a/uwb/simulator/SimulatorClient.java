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
 * A client that manages the simulation lifecycle for a single {@link VirtualAnchor}.
 */
public class SimulatorClient { // Removed 'extends Thread'
	private VirtualAnchor anchor;
	private String baseUrl;

	private static final String PATH_BOOT = "/anchorRegistration";
	private static final String PATH_MEASURE = "/measurementReport";
	private static final String PATH_SCAN = "/scanReport";
	
	public SimulatorClient(VirtualAnchor anchor, String url) {
		this.anchor = anchor;
		this.baseUrl = url;
	}
	
	public void startClient() { // Renamed from run()
		JSONObject action = this.sendRegistrationRequest();
		
		if (action == null) {
			System.err.println("Initial registration failed. Stopping client.");
			return;
		}

		while (action != null) {
			try {
				String actionToExecute = action.getString("actionToExecute");
				String endpointPath = "measure".equalsIgnoreCase(actionToExecute) ? PATH_MEASURE : PATH_SCAN;
				URL requestUrl = new URI(this.baseUrl + endpointPath).toURL();
				
				JSONObject currentReport = this.anchor.VirtualBehaviour(action);
				action = this.sendActionRequest(currentReport, requestUrl);
				
				// Wait 1 second before next action
				Thread.sleep(1000);

			} catch (MalformedURLException | URISyntaxException e) {
				System.err.println("URL error. Stopping client.");
				e.printStackTrace();
				action = null;
			} catch (InterruptedException e) {
				System.err.println("Thread sleep interrupted.");
				Thread.currentThread().interrupt();
				action = null;
			} catch (Exception e) {
				System.err.println("An error occurred during communication. Stopping client.");
				e.printStackTrace();
				action = null;
			}
		}
		
		System.out.println("Anchor " + anchor.getDeviceName() + " simulation finished.");
	}
	
	private JSONObject sendRegistrationRequest() {
        // [This method remains exactly the same as your original code]
        // ... (Include your existing try-catch block for HttpURLConnection here) ...
		try {
			URL url = new URI(this.baseUrl + PATH_BOOT).toURL();
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
			connection.setRequestProperty("Accept", "application/json");
			connection.setDoOutput(true);
			
			JSONObject payload = new JSONObject();
			payload.put("anchorID", anchor.getDeviceName());
			byte[] postData = payload.toString().getBytes(StandardCharsets.UTF_8);

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
		} catch (Exception e) {
			System.err.println("Registration failed for anchor " + anchor.getDeviceName() + ": " + e.getMessage());
			return null;
		}
	}
	
	private JSONObject sendActionRequest(JSONObject reply, URL url) throws Exception {
        // [This method remains exactly the same as your original code]
        // ... (Include your existing HttpURLConnection code here) ...
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		connection.setRequestProperty("Accept", "application/json");
		connection.setDoOutput(true);
		
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