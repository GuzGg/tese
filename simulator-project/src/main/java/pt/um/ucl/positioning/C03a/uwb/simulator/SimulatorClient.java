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
import java.util.logging.Logger;
 
import org.json.JSONObject;
 
/**
 * Manages the simulation lifecycle for a single {@link VirtualAnchor}.
 * <p>
 * Registers the anchor with the server, then enters a loop: receive a command,
 * execute the simulated behaviour (including waiting for scheduled slots), and
 * send the report back. Timing is driven entirely by the server's {@code whenToExecute}
 * fields — there is no arbitrary fixed sleep here.
 *
 * @author Gustavo Oliveira
 * @version 0.7
 */
public class SimulatorClient {
 
    private static final Logger logger = Logger.getLogger(SimulatorClient.class.getName());
 
    private final VirtualAnchor anchor;
    private final String baseUrl;
 
    private static final String PATH_BOOT    = "anchorRegistration";
    private static final String PATH_MEASURE = "measurementReport";
    private static final String PATH_SCAN    = "scanReport";
 
    public SimulatorClient(VirtualAnchor anchor, String baseUrl) {
        this.anchor = anchor;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }
 
    /**
     * Starts the anchor simulation loop.
     * <p>
     * Registers with the server, then continuously:
     * <ol>
     *   <li>Receives a command (slowScan, fastScan, or measure).</li>
     *   <li>Passes it to {@link VirtualAnchor#VirtualBehaviour} which handles the
     *       timing internally (sleeping until each tag's scheduled slot).</li>
     *   <li>Sends the completed report back and waits for the next command.</li>
     * </ol>
     */
    public void startClient() {
        JSONObject action = sendRegistrationRequest();
 
        if (action == null) {
            logger.severe("Initial registration failed for " + anchor.getDeviceName() + ". Stopping.");
            return;
        }
 
        logger.info(anchor.getDeviceName() + " registered. Entering action loop.");
 
        while (action != null) {
            try {
                String actionToExecute = action.getString("actionToExecute");
                String endpointPath = "measure".equalsIgnoreCase(actionToExecute) ? PATH_MEASURE : PATH_SCAN;
                URL requestUrl = new URI(this.baseUrl + endpointPath).toURL();
 
                // VirtualBehaviour handles all timing internally (sleeps until scheduled slots).
                // No fixed sleep here — the server's schedule drives the pace.
                JSONObject report = this.anchor.VirtualBehaviour(action);
                action = sendActionRequest(report, requestUrl);
 
            } catch (MalformedURLException | URISyntaxException e) {
                logger.severe("URL error for " + anchor.getDeviceName() + ": " + e.getMessage());
                action = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning(anchor.getDeviceName() + " interrupted. Stopping.");
                action = null;
            } catch (Exception e) {
                logger.severe("Error during communication for " + anchor.getDeviceName() + ": " + e.getMessage());
                e.printStackTrace();
                action = null;
            }
        }
 
        logger.info(anchor.getDeviceName() + " simulation finished.");
    }
 
    // --- Private HTTP helpers ---
 
    private JSONObject sendRegistrationRequest() {
        try {
            URL url = new URI(this.baseUrl + PATH_BOOT).toURL();
            JSONObject payload = new JSONObject();
            payload.put("anchorID", anchor.getDeviceName());
            return post(url, payload);
        } catch (Exception e) {
            logger.severe("Registration failed for " + anchor.getDeviceName() + ": " + e.getMessage());
            return null;
        }
    }
 
    private JSONObject sendActionRequest(JSONObject report, URL url) throws Exception {
        return post(url, report);
    }
 
    /**
     * Sends a JSON POST request and returns the parsed response.
     *
     * @param url     The target URL.
     * @param payload The JSON body to send.
     * @return The parsed JSON response, or {@code null} if the server returned a non-200 status.
     * @throws Exception on network or parsing errors.
     */
    private JSONObject post(URL url, JSONObject payload) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setDoOutput(true);
 
        byte[] postData = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(postData);
        }
 
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line.trim());
                return new JSONObject(sb.toString());
            }
        } else {
            logger.warning(anchor.getDeviceName() + " — server returned HTTP " + responseCode + " from " + url);
            return null;
        }
    }
}