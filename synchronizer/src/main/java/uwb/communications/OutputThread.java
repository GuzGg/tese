package uwb.communications;

import uwb.devices.Tag;
import uwb.measurements.Measurement;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class OutputThread extends Thread {
	private List<Tag> tags;
	private String endpointUrl;
	
	public OutputThread (List<Tag> tag, String endpointUrl) {
		this.tags = tag;
		this.endpointUrl = endpointUrl;
	}
	
	public void run() {
		for(Tag tag: this.tags) {
			Measurement measurement = tag.getMeasurements().getLast();
			 try {
					URL url = new URI(endpointUrl).toURL();
		            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		            connection.setRequestMethod("POST");
		            connection.setRequestProperty("Content-Type", "application/json");
		            connection.setDoOutput(true);
		            
		            JSONObject payload = measurement.toJson();

		            try (OutputStream os = connection.getOutputStream()) {
		                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
		                os.write(input, 0, input.length);
		            }

		            int code = connection.getResponseCode();
		            
		            System.out.println(code);


		        } catch (Exception e) {
		            e.printStackTrace();
		        }
		}
	}
}
