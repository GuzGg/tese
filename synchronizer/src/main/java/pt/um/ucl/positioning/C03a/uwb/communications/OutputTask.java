package pt.um.ucl.positioning.C03a.uwb.communications;

import pt.um.ucl.positioning.C03a.uwb.devices.Tag;
import pt.um.ucl.positioning.C03a.uwb.measurements.Measurement;
import pt.um.ucl.positioning.C03a.uwb.config.Config;
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
 * @version 0.6
 */
public class OutputTask implements Runnable {
    /** The tag containing the measurement data to process. */
    private final Tag tag;
    /** The database logger instance. */
    private final MeasurementsDatabaseLogger dbLogger;
    /** Flag to enable/disable Logs. */
    private final boolean enableLogs;
    /** System configuration. */
    private final Config config;
    /** Servelet context. */
    private final C03a context;


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
    public OutputTask(C03a context, Tag tag, MeasurementsDatabaseLogger dbLogger, Config config) {
    	this.context = context;
        this.tag = tag;
        this.dbLogger = dbLogger;
        this.enableLogs = config.isEnableOutputLogs();
        this.config = config;
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
	    
	    Measurement measurement = tag.getMeasurements().get(tag.getMeasurements().size() - 1);
	    int measurementId = -1;
	    
	    if(this.config.isExportToDbQ()) {
	        try {
	            int retries = 0;
	            boolean success = false;
	
	            while (retries < config.getDbMaxRetries() && !success) {
	                try {
	                    measurementId = dbLogger.saveDataToA(tag, measurement);
	                    
	                    if (measurementId > 0) {
	                        success = true;
	                        measurement.setMeasurmentId(measurementId); 
	                    } else {
	                        throw new Exception("Invalid ID returned from DatabaseLogger");
	                    }
	                } catch (Exception e) {
	                    retries++;
	                    if (enableLogs) System.err.println("DB Failure (Attempt " + retries + "). Retrying...");
	                    
	                    if (retries >= config.getDbMaxRetries()) {
	                        this.context.signalFatalError("Failed to connect to DB after " + retries + " attempts.");
	                        return; 
	                    }
	                    
	                    try { Thread.sleep(config.getDbRetryDelay()); } catch (InterruptedException ignored) {}
	                }
	            }
	        } catch (Exception dbException) {
	            if(this.enableLogs) System.err.println("DB Error for tag " + tag.getDeviceName() + ": " + dbException.getMessage());
	            return;
	        }
	    }
	
	    if(this.config.isExportToPeQ()) {
	        try {
	            JSONObject payloadJson = measurement.toJson();
	            payloadJson.put("estimateAccessToken", this.config.getPeToken());
	            String jsonString = payloadJson.toString();
	            
	            byte[] postData = jsonString.getBytes(StandardCharsets.UTF_8);
	
	            URL url = new URI(config.getPeUrl()).toURL();
	            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	            connection.setRequestMethod("POST");
	            connection.setRequestProperty("Content-Type", "application/json");
	            connection.setRequestProperty("Content-Length", String.valueOf(postData.length));
	            connection.setDoOutput(true);
	
	            try (OutputStream os = connection.getOutputStream()) {
	                os.write(postData);
	            }
	
	            int code = connection.getResponseCode();
	            if(this.enableLogs) {
	                System.out.println("Tag: " + tag.getDeviceName() + " | Sending JSON:\n" + payloadJson.toString(4));
	                System.out.println("Tag: " + tag.getDeviceName() + " | Estimator Response: " + code);
	            }
	
	        } catch (Exception httpException) {
	            if(this.enableLogs) System.err.println("HTTP Error for tag " + tag.getDeviceName() + ": " + httpException.getMessage());
	        }
	    }
	}
}