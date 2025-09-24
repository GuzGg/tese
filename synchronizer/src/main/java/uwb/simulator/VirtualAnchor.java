package uwb.simulator;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.json.JSONArray;
import org.json.JSONObject;

import uwb.devices.Anchor;
import uwb.devices.Tag;

public class VirtualAnchor extends Anchor {
	private List<Tag> listOfTags;
	private static final AtomicInteger count = new AtomicInteger(0); 
	private int tagID;
	
	
	public VirtualAnchor(String deviceId, long initializedAt, long lastSeen) {
		super(deviceId, initializedAt, lastSeen);
		this.tagID = count.incrementAndGet();
		this.listOfTags = new ArrayList<Tag>();
	}
	
	public JSONObject VirtualBehaviour(JSONObject response) {
	    String actionToExecute = response.getString("actionToExecute");
	    
	    JSONObject replyPayload = new JSONObject();
	    replyPayload.put("anchorID", getDeviceId());

	    if ("slowScan".equals(actionToExecute) || "fastScan".equals(actionToExecute)) {
	        Random random = new Random();
	        
	        if (random.nextInt(100) < 40) { 
	            Tag newTag = new Tag("tag" + this.tagID, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
	            this.tagID = count.incrementAndGet();
	            this.listOfTags.add(newTag);
	        }
	        
	        JSONArray tagsArray = new JSONArray();
	        if(!this.listOfTags.isEmpty()) {
		        this.listOfTags.forEach(tag -> {
		            JSONObject tagObj = new JSONObject();
		            tagObj.put("tagID", tag.getDeviceId());
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
	            if (executionTimes.containsKey(tag.getDeviceId())) {
	                JSONObject tagObj = new JSONObject();
	                tagObj.put("tagID", tag.getDeviceId());
	                
	                float distance = random.nextFloat() * 40;
	                tagObj.put("distance", distance);
	                
	                tagObj.put("executedAt", executionTimes.get(tag.getDeviceId()));
	                tagsArray.put(tagObj);
	            }
	        });
	        
	        replyPayload.put("tags", tagsArray);
	    }
	    System.out.println(replyPayload);
	    return replyPayload;
	}
}
