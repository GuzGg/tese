package pt.um.ucl.positioning.C03a.uwb.communications;

import pt.um.ucl.positioning.C03a.uwb.devices.Tag;
import pt.um.ucl.positioning.C03a.uwb.measurements.Measurement;
import pt.um.ucl.positioning.C03a.uwb.database.MeasurementsDatabaseLogger;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * A {@link Runnable} task that processes a single {@link Tag}'s measurement data.
 * <p>
 * This task is designed to be run by the {@link OutputThread}'s executor service.
 * It performs two main actions based on the application configuration:
 * <ol>
 * <li>Persists the tag's measurement data to the database via the {@link MeasurementsDatabaseLogger}.</li>
 * <li>Sends the measurement data as a JSON payload to the remote Position Estimator endpoint via HTTP POST.</li>
 * </ol>
 * These actions are performed asynchronously and in parallel for different tags.
 * 
 * @author Gustavo Oliveira
 * @version 0.2
 */
public class OutputTask implements Runnable {
    /** The tag containing the measurement data to process. */
    private final Tag tag;
    /** The endpoint URL for the Position Estimator. */
    private final String endpointUrl;
    /** The database logger instance. */
    private final MeasurementsDatabaseLogger dbLogger;
    /** Flag to enable/disable database export. */
    private final boolean exportToDbQ;
    /** Flag to enable/disable Position Estimator export. */
    private final boolean exportToPeQ;
    /** Flag to enable/disable Logs. */
    private final boolean enableLogs;
    /** The authentication token for the Position Estimator. */
    private final String token;

    /**
     * Constructs a new output task.
     *
     * @param tag The tag with its completed measurement data.
     * @param endpointUrl The URL of the Position Estimator service.
     * @param dbLogger The shared {@link MeasurementsDatabaseLogger} instance.
     * @param exportToDbQ {@code true} to enable database logging.
     * @param exportToPeQ {@code true} to enable posting to the Position Estimator.
     * @param token The authentication token for the Position Estimator.
     */
    public OutputTask(Tag tag, String endpointUrl, MeasurementsDatabaseLogger dbLogger, boolean exportToDbQ, boolean exportToPeQ, boolean enableLogs, String token) {
        this.tag = tag;
        this.endpointUrl = endpointUrl;
        this.dbLogger = dbLogger;
        this.exportToDbQ = exportToDbQ;
        this.exportToPeQ = exportToPeQ;
        this.enableLogs = enableLogs;
        this.token = token;
    }

    /**
     * The main execution logic for the task.
     * <p>
     * It handles persisting the tag's last measurement to the database
     * and/or sending it to the Position Estimator service.
     */
    @Override
    public void run() {
        if (tag.getMeasurements().isEmpty()) {
        	if(this.enableLogs) System.err.println("Error: Tag " + tag.getDeviceName() + " has no measurements to output.");
            return;
        }
        // Get the last (and only) measurement added to this cloned tag
        Measurement measurement = tag.getMeasurements().get(tag.getMeasurements().size() - 1);
        int measurementId = -1;
        
        // --- 1. Database Export ---
        if(this.exportToDbQ) {
            try {
            	if(this.enableLogs) System.out.println("OUTPUT: Persisting data for Tag " + tag.getDeviceName());
                // Save the full measurement and get its new ID
                measurementId = dbLogger.saveDataToA(tag, measurement); 

                if (measurementId > 0) {
                    measurement.setMeasurmentId(measurementId); // Set ID for PE export
                } else {
                	if(this.enableLogs) System.err.println("Failed to get valid ID for tag: " + tag.getDeviceName());
                    return; // Stop if DB save failed
                }
            } catch (Exception dbException) {
            	if(this.enableLogs) System.err.println("DB Error for tag " + tag.getDeviceName() + ": " + dbException.getMessage());
                dbException.printStackTrace();
                return; // Stop if DB save failed
            }
        }

        // --- 2. Position Estimator Export ---
        if(this.exportToPeQ) {
            try {
                // Create the JSON payload from the measurement
                JSONObject payloadJson = measurement.toJson();
                payloadJson.put("estimateAccessToken" , this.token);
                String jsonString = payloadJson.toString();
                
                byte[] postData = jsonString.getBytes(StandardCharsets.UTF_8);

                // Configure and send HTTP POST request
                URL url = new URI(endpointUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Content-Length", String.valueOf(postData.length));
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(postData);
                }

    	        int code = connection.getResponseCode();
    	        if(this.enableLogs) System.out.println("Tag: " + tag.getDeviceName() + " | Sending JSON:\n" + payloadJson.toString(4));
    	        if(this.enableLogs) System.out.println("Tag: " + tag.getDeviceName() + " | Estimator HTTP Response Code: " + code + " by worker " + Thread.currentThread().getName());

            } catch (Exception httpException) {
            	if(this.enableLogs) System.err.println("HTTP Error for tag " + tag.getDeviceName() + ": " + httpException.getMessage());
                httpException.printStackTrace();
            }
        }
    }
}