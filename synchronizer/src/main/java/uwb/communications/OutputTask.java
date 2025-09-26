package uwb.communications;

import uwb.devices.Tag;
import uwb.measurements.Measurement;
import uwb.database.MeasurementsDatabaseLogger;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
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
        // ASSUMPTION: Tag provides access to its latest measurement.
        Measurement measurement = tag.getMeasurements().getLast();
        int measurementId = -1;

        // --- 1. Database Write (ToA Only) ---
        try {
            // This call is blocking, but it runs on a separate worker thread.
            measurementId = dbLogger.saveMeasurements(dbLogger.getTargetIdByCode(tag.getDeviceName()), "ToA", measurement.getMeasurmentEndTime()); 

            if (measurementId > 0) {
                // ASSUMPTION: Measurement.setMeasurementId(int) exists
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

        // --- 2. HTTP Request ---
        try {
            URL url = new URI(endpointUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Payload now includes the measurementId
            JSONObject payload = measurement.toJson(); 

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = connection.getResponseCode();
            System.out.println("Tag: " + tag.getDeviceName() + " | HTTP Response Code: " + code + " by worker " + Thread.currentThread().getName());

        } catch (Exception httpException) {
            System.err.println("HTTP Error for tag " + tag.getDeviceName() + ": " + httpException.getMessage());
            httpException.printStackTrace();
        }
    }
}