package uwb.simulator;

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

public class SimulatorThread extends Thread {
	private VirtualAnchor anchor;
	private String baseUrl;

	private static final String PATH_BOOT = "/anchorRegistration";
	private static final String PATH_MEASURE = "/measurementReport";
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
				
				// Add a delay to simulate a realistic device
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
	
	private JSONObject sendRegistrationRequest() {
		try {
			URL url = new URI(this.baseUrl + PATH_BOOT).toURL();
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			
			// Change Content-Type to x-www-form-urlencoded
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connection.setDoOutput(true);
			
			// Build the payload as a URL-encoded string with a key 'jsondata'
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
	
	private JSONObject sendActionRequest(JSONObject reply, URL url) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		
		// Change Content-Type to x-www-form-urlencoded
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		connection.setDoOutput(true);
		
		// Build the payload as a URL-encoded string with a key 'jsondata'
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