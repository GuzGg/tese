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
    private final boolean exportToDbQ;
    private final boolean exportToPeQ;
    private final String token;

    public OutputTask(Tag tag, String endpointUrl, MeasurementsDatabaseLogger dbLogger, boolean exportToDbQ, boolean exportToPeQ, String token) {
        this.tag = tag;
        this.endpointUrl = endpointUrl;
        this.dbLogger = dbLogger;
        this.exportToDbQ = exportToDbQ;
        this.exportToPeQ = exportToPeQ;
        this.token = token;
    }

    @Override
    public void run() {
        if (tag.getMeasurements().isEmpty()) {
            System.err.println("Error: Tag " + tag.getDeviceName() + " has no measurements to output.");
            return;
        }
        Measurement measurement = tag.getMeasurements().get(tag.getMeasurements().size() - 1);
        int measurementId = -1;
        
        if(this.exportToDbQ) {
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
        }

        if(this.exportToPeQ) {
            try {
                JSONObject payloadJson = measurement.toJson();
                payloadJson.append("accessToken " , this.token);
                String jsonString = payloadJson.toString();
                
                String encodedJson = URLEncoder.encode(jsonString, StandardCharsets.UTF_8);
                String formData = "measurements=" + encodedJson;
                
                byte[] postData = formData.getBytes(StandardCharsets.UTF_8);

                URL url = new URI(endpointUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setRequestProperty("Content-Length", String.valueOf(postData.length));
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(postData);
                }

    	            int code = connection.getResponseCode();
                System.out.println("Tag: " + tag.getDeviceName() + " | Estimator HTTP Response Code: " + code + " by worker " + Thread.currentThread().getName());

            } catch (Exception httpException) {
                System.err.println("HTTP Error for tag " + tag.getDeviceName() + ": " + httpException.getMessage());
                httpException.printStackTrace();
            }
        }

    }
}