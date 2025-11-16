package pt.um.ucl.positioning.C03a.uwb.simulator;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import pt.um.ucl.positioning.C03a.uwb.devices.Anchor;
import pt.um.ucl.positioning.C03a.uwb.devices.Tag;

/**
 * Represents a simulated UWB (Ultra-Wideband) Anchor.
 * <p>
 * This class simulates the behavior of a physical anchor, including
 * discovering virtual tags and reporting simulated distances to them.
 * It extends the base {@link Anchor} class.
 * 
 * @author Gustavo Oliveira
 * @version 0.1
 */
public class VirtualAnchor extends Anchor {
	
	/** A list of virtual tags "detected" by this anchor. */
	private List<Tag> listOfTags;
	/** Counter to generate unique IDs for new virtual tags. */
	private int tagID;
	
	
	/**
	 * Constructs a new VirtualAnchor.
	 *
	 * @param deviceId The unique identifier for this anchor.
	 * @param initializedAt The timestamp when the anchor was initialized.
	 * @param lastSeen The timestamp when the anchor was last seen.
	 */
	public VirtualAnchor(String deviceId, long initializedAt, long lastSeen) {
		super(deviceId, initializedAt, lastSeen);
		this.tagID = 0;
		this.listOfTags = new ArrayList<Tag>();
		// Initialize with one tag
		this.listOfTags.add(new Tag("tag" + tagID, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
		this.tagID += 1 ;
	}
	
	/**
	 * Simulates the anchor's behavior based on a command from the server.
	 * <p>
	 * This method processes the "actionToExecute" from the response JSON.
	 * <ul>
	 * <li><b>"slowScan" / "fastScan":</b> Simulates scanning for tags. It may
	 * randomly "discover" a new tag (though the current probability is 0). 
	 * It returns a list of all known tags.</li>
	 * <li><b>"measure":</b> Simulates measuring the distance to a specific list of tags
	 * provided in the request. It returns a JSON array of tags with simulated distances.</li>
	 * </ul>
	 *
	 * @param response The JSONObject received from the server, containing the "actionToExecute"
	 * and other necessary data (like tags to measure).
	 * @return A JSONObject representing the reply payload to be sent back to the server.
	 */
	public JSONObject VirtualBehaviour(JSONObject response) {
	    String actionToExecute = response.getString("actionToExecute");
	    
	    JSONObject replyPayload = new JSONObject();
	    replyPayload.put("anchorID", getDeviceName());

	    if ("slowScan".equals(actionToExecute) || "fastScan".equals(actionToExecute)) {
	        Random random = new Random();
	        
	        // Note: This condition (random.nextInt(100) < 0) will never be true.
	        // To enable random tag discovery, change 0 to a positive integer (e.g., 10 for 10% chance).
	        if (random.nextInt(100) < 0) { 
	            Tag newTag = new Tag("tag" + this.tagID, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
	            this.tagID += 1;
	            this.listOfTags.add(newTag);
	        }
	        
	        JSONArray tagsArray = new JSONArray();
	        if(!this.listOfTags.isEmpty()) {
		        this.listOfTags.forEach(tag -> {
		            JSONObject tagObj = new JSONObject();
		            tagObj.put("tagID", tag.getDeviceName());
		            tagsArray.put(tagObj);
		        });
	        }

	        
	        replyPayload.put("tags", tagsArray);
	        
	    } else if ("measure".equals(actionToExecute)) {
	        Random random = new Random();
	        
	        JSONArray responseTags = response.getJSONArray("tags");
	        Map<String, Long> executionTimes = new HashMap<>();
	        responseTags.forEach(responseTag -> {
	            JSONObject responseTagObj = (JSONObject) responseTag;
	            String tagID = responseTagObj.getString("deviceID");
	            Long whenToExecuteTag = responseTagObj.getLong("whenToExecute");
	            executionTimes.put(tagID, whenToExecuteTag);
	        });
 
	        JSONArray tagsArray = new JSONArray();
	        this.listOfTags.forEach(tag -> {
	            if (executionTimes.containsKey(tag.getDeviceName())) {
	                JSONObject tagObj = new JSONObject();
	                tagObj.put("tagID", tag.getDeviceName());
	                
	                // Simulate a distance between 0.0 and 40.0
	                float distance = random.nextFloat() * 40;
	                tagObj.put("distance", distance);
	                
	                tagObj.put("executedAt", executionTimes.get(tag.getDeviceName()));
	                tagsArray.put(tagObj);
	            }
	        });
	        
	        replyPayload.put("tags", tagsArray);
	    }
	    System.out.println(replyPayload);
	    return replyPayload;
	}
}