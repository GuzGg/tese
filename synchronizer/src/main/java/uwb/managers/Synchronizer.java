package uwb.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uwb.devices.Anchor;
import uwb.devices.Tag;
import uwb.measurements.Measurement;

import org.json.JSONArray;
import org.json.JSONObject;


public class Synchronizer {
	
	public Map<String, Tag> listOfTags;
	public  Map<String, Anchor> listOfAnchors;
	
	public Synchronizer(Map<String, Tag> listOfTags, Map<String, Anchor> listOfAnchors) {
		super();
		this.listOfTags = listOfTags;
		this.listOfAnchors = listOfAnchors;

	}
	
	public Synchronizer() {
		this.listOfTags = new HashMap<String, Tag>();
		this.listOfAnchors = new HashMap<String, Anchor>();
	}
	
	/**
	 * Add new anchor to listOfAnchors
	 * @param anchor anchor to be added to the map
	 */
	public void addNewAnchor(Anchor anchor) {
		this.listOfAnchors.put(anchor.getDeviceId(), anchor);
	}
	
	/**
	 * Check if anchor exists
	 * @param anchor anchor to be checked
	 * @return if an anchor exists or not
	 */
	public boolean anchorExists(Anchor anchor) {
		return this.listOfAnchors.containsKey(anchor.getDeviceId());
	}
	
	/**
	 * Add new Tag to listOfTags
	 * @param tag tag to be added
	 */
	public void addNewTag(Tag tag) {
		this.listOfTags.put(tag.getDeviceId(), tag);
	}
	
	/**
	 * Check if tag exists
	 * @param tag anchor to be checked
	 * @return if an tag exists or not
	 */
	public boolean tagExists(Tag tag) {
		return this.listOfTags.containsKey(tag.getDeviceId());
	}
	
	public void addMeasurementRound(long startTime, long endTime) {
		List<Tag> tagList = new ArrayList<>(this.listOfTags.values());
		 for (Tag tag: tagList) {
			 List<Measurement> measurements = tag.getMeasurements();
			 measurements.add(new Measurement(tag, startTime, endTime));
			 tag.setMeasurements(measurements);
		 }
	}
	
	/**
	 * JSON RESPONSE BUILDERS
	 */
	/**
	 * Generates the JSON response for Slow Scan action
	 * @param executionTime time when the action will be executed
	 * @return response JSON written as a string
	 */
	public String getSlowScanResponse(long executionTime) {
		 JSONObject jsonObject = new JSONObject();

		   try {
	            jsonObject.put("actionToExecute", "slowScan");
	            jsonObject.put("whenToExecute", executionTime);
	        } catch (org.json.JSONException e) {
	            // Handle any JSONException that might occur
	            e.printStackTrace();
	        }
		return jsonObject.toString();
	}
	
	public String getFastScanResponse(long executionTime) {
		 JSONObject jsonObject = new JSONObject();

		   try {
	            jsonObject.put("actionToExecute", "fastScan");
	            jsonObject.put("whenToExecute", executionTime);
	        } catch (org.json.JSONException e) {
	            // Handle any JSONException that might occur
	            e.printStackTrace();
	        }
		return jsonObject.toString();
	}
	
	
	public String getMeasurmentResponse( Anchor anchor, long executionTime, long scanTime) {
		   JSONObject jsonObject = new JSONObject();
		   try {
	            jsonObject.put("actionToExecute", "measure");

	            JSONArray tagsArray = new JSONArray();
	            
	            List<Anchor> anchorList = new ArrayList<>(this.listOfAnchors.values());
	            List<Tag> tagList = new ArrayList<>(this.listOfTags.values());
	            
	            int anchorIndex = anchorList.indexOf(anchor);

	            for (Tag tag: tagList) {
	                long ellapsedTime = executionTime + tagList.indexOf(tag) * anchorList.size() * scanTime;
	                long timeToMeasure = ellapsedTime + anchorIndex * scanTime;
	                // Create a JSONObject for each tag
	                JSONObject tagJson = new JSONObject();
	                tagJson.put("deviceID", tag.getDeviceId());
	                tagJson.put("whenToExecute", timeToMeasure);

	                tagsArray.put(tagJson);
	            }

	            jsonObject.put("tags", tagsArray);

	        } catch (org.json.JSONException e) {
	            e.printStackTrace();
	        }
		return jsonObject.toString();
		
	}
}
