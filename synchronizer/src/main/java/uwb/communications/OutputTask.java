package uwb.communications;

import uwb.devices.Tag;
import uwb.measurements.Measurement;
import uwb.database.MeasurementsDatabaseLogger;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class OutputTask implements Runnable {
    private final Tag tag;
    private final String endpointUrl;
    private final MeasurementsDatabaseLogger dbLogger;

    public OutputTask(Tag tag, String endpointUrl, MeasurementsDatabaseLogger dbLogger) {
        this.tag = tag;
        this.endpointUrl = endpointUrl;
        this.dbLogger = dbLogger;
    }

    @Override
    public void run() {
        // Safely access the last measurement
        if (tag.getMeasurements().isEmpty()) {
            System.err.println("Error: Tag " + tag.getDeviceName() + " has no measurements to output.");
            return;
        }
        Measurement measurement = tag.getMeasurements().get(tag.getMeasurements().size() - 1);
        int measurementId = -1;

        // --- 1. Database Persistence ---
        try {
            System.out.println("OUTPUT: Persisting data for Tag " + tag.getDeviceName());
            measurementId = dbLogger.saveDataToA(tag, measurement); 

            if (measurementId > 0) {
                measurement.setMeasurmentId(measurementId); 
            } else {
                System.err.println("Failed to get valid ID for tag: " + tag.getDeviceName());
                return; 
            }
        } catch (Exception dbException) {
            System.err.println("DB Error for tag " + tag.getDeviceName() + ": " + dbException.getMessage());
            dbException.printStackTrace();
            return; 
        }

        // --- 2. HTTP Request (Refactored for x-www-form-urlencoded) ---
        try {
            // 2a. Build the JSON payload
            JSONObject payloadJson = measurement.toJson(); 
            String jsonString = payloadJson.toString();
            
            // 2b. Encode the JSON string as the 'measurements' parameter
            // Parameter name must be "measurements"
            // Content must be URL-encoded
            String encodedJson = URLEncoder.encode(jsonString, StandardCharsets.UTF_8);
            String formData = "measurements=" + encodedJson;
            
            byte[] postData = formData.getBytes(StandardCharsets.UTF_8);

            // 2c. Setup the Connection
            URL url = new URI(endpointUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            
            // CRITICAL FIX: Set Content-Type to x-www-form-urlencoded
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length", String.valueOf(postData.length));
            connection.setDoOutput(true);

            // 2d. Send the Data
            try (OutputStream os = connection.getOutputStream()) {
                os.write(postData);
            }

            // 2e. Process Response
            int code = connection.getResponseCode();
            System.out.println("Tag: " + tag.getDeviceName() + " | Estimator HTTP Response Code: " + code + " by worker " + Thread.currentThread().getName());

        } catch (Exception httpException) {
            System.err.println("HTTP Error for tag " + tag.getDeviceName() + ": " + httpException.getMessage());
            httpException.printStackTrace();
        }
    }
}