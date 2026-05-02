package pt.um.ucl.positioning.C03a.uwb.managers;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import pt.um.ucl.positioning.C03a.uwb.devices.Anchor;
import pt.um.ucl.positioning.C03a.uwb.devices.Tag;
import pt.um.ucl.positioning.C03a.uwb.measurements.Measurement;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Manages the state of all known {@link Anchor}s and {@link Tag}s in the system.
 * <p>
 * This class provides thread-safe methods to add and check for the existence
 * of devices. It also generates JSON-formatted action commands
 * (slowScan, fastScan, measure) to be sent to anchors.
 * 
 * @author Gustavo Oliveira
 * @version 0.1
 */
public class Synchronizer {
	
	/** A map of known tags, keyed by their device name (ID). */
	public Map<String, Tag> listOfTags;
	/** A map of known anchors, keyed by their device name (ID). */
	public Map<String, Anchor> listOfAnchors;
	/** A set of known whitelisted tags, keyed by their device name (ID). */
	public Set<String> whitelistOfTags;
	/** A set of known whitelisted anchors, keyed by their device name (ID). */
	public Set<String> whitelistOfAnchors;
	
	private List<Anchor> lockedAnchorList = new ArrayList<>();
	private List<Tag> lockedTagList = new ArrayList<>();
	private long currentCycleExecutionTime = 0;
	/**
	 * Constructs a new Synchronizer with pre-populated lists of tags and anchors.
	 *
	 * @param listOfTags A map of tags to initialize with.
	 * @param listOfAnchors A map of anchors to initialize with.
	 */
	public Synchronizer(Map<String, Tag> listOfTags, Map<String, Anchor> listOfAnchors) {
		super();
		this.listOfTags = listOfTags;
		this.listOfAnchors = listOfAnchors;

	}
	
	/**
	 * Default constructor.
	 * Initializes empty maps for tags and anchors.
	 */
	public Synchronizer() {
	    this.listOfTags = new ConcurrentHashMap<String, Tag>();
	    this.listOfAnchors = new ConcurrentHashMap<String, Anchor>();
	    this.whitelistOfTags = ConcurrentHashMap.newKeySet();
	    this.whitelistOfAnchors = ConcurrentHashMap.newKeySet();
	}
	
	/**
	 * Adds a new anchor to the list of known anchors in a thread-safe manner.
	 *
	 * @param anchor The {@link Anchor} to be added.
	 */
	public synchronized void addNewAnchor(Anchor anchor) {
		this.listOfAnchors.put(anchor.getDeviceName(), anchor);
	}
	
	/**
	 * Checks if an anchor already exists in the list of known anchors.
	 *
	 * @param anchor The {@link Anchor} to check.
	 * @return {@code true} if the anchor exists (by device name), {@code false} otherwise.
	 */
	public boolean anchorExists(Anchor anchor) {
		return this.listOfAnchors.containsKey(anchor.getDeviceName());
	}
	
	/**
	 * Adds a new tag to the list of known tags in a thread-safe manner.
	 *
	 * @param tag The {@link Tag} to be added.
	 */
	public synchronized void addNewTag(Tag tag) {
		this.listOfTags.put(tag.getDeviceName(), tag);
	}
	
	/**
	 * Checks if a tag already exists in the list of known tags.
	 *
	 * @param tag The {@link Tag} to check.
	 * @return {@code true} if the tag exists (by device name), {@code false} otherwise.
	 */
	public boolean tagExists(Tag tag) {
		return this.listOfTags.containsKey(tag.getDeviceName());
	}
	
	/**
	 * Returns a list of all currently registered Tag objects.
	 * @return A list of Tag objects.
	 */
	public synchronized List<Tag> getTagList() {
	    return new ArrayList<>(this.listOfTags.values());
	}

	/**
	 * Returns a list of all currently registered Anchor objects.
	 * @return A list of Anchor objects.
	 */
	public synchronized List<Anchor> getAnchorList() {
	    return new ArrayList<>(this.listOfAnchors.values());
	}

	/**
	 * Creates a new {@link Measurement} object for every known tag for an upcoming
	 * measurement round.
	 * <p>
	 * This method is thread-safe.
	 *
	 * @param startTime The start time for the new measurement round.
	 * @param endTime The end time for the new measurement round.
	 */
	public synchronized void addMeasurementRound(long startTime, long endTime) {
	    long bufferMillis = 5000; // Increased to 5s to match Measurement.java
	    for (Tag tag : listOfTags.values()) {
	        if (tag == null) continue;
	        
	        List<Measurement> measurements = tag.getMeasurements();
	        // Prevent list from growing too large
	        while (measurements.size() > 10) {
	            measurements.remove(0); 
	        }
	        
	        // Window now perfectly encapsulates the hardware cycle
	        measurements.add(new Measurement(tag, startTime - bufferMillis, endTime + bufferMillis));
	    }
	}
	
	/**
	 * Generates a JSON string for a "slowScan" action.
	 *
	 * @param executionTime The timestamp when the action should be executed.
	 * @return A JSON string representing the slowScan command.
	 */
	public String getSlowScanResponse(long executionTime) {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put("actionToExecute", "slowScan");
			jsonObject.put("whenToExecute", executionTime);
		} catch (JSONException e) {
			e.printStackTrace();
			// Return a JSON error message if an exception occurs
			return "{\"error\":\"Failed to create slowScan response JSON.\"}";
		}
		return jsonObject.toString();
	}
	
	/**
	 * Generates a JSON string for a "fastScan" action.
	 *
	 * @param executionTime The timestamp when the action should be executed.
	 * @return A JSON string representing the fastScan command.
	 */
	public String getFastScanResponse(long executionTime) {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put("actionToExecute", "fastScan");
			jsonObject.put("whenToExecute", executionTime);
		} catch (JSONException e) {
			e.printStackTrace();
			// Return a JSON error message if an exception occurs
			return "{\"error\":\"Failed to create fastScan response JSON.\"}";
		}
		return jsonObject.toString();
	}
	
	/**
	 * Generates a JSON string for a "measure" action.
	 * <p>
	 * This method calculates a specific execution time for each tag based on
	 * the anchor's index, ensuring a staggered measurement schedule.
	 *
	 * @param anchor The specific {@link Anchor} this command is intended for.
	 * @param executionTime The base start time for the measurement round.
	 * @param scanTime The duration allotted for a single tag measurement.
	 * @return A JSON string representing the measure command, including a
	 * list of tags and their scheduled execution times.
	 */
	private void logServerExpectation(Anchor anchor, String tagId, long time) {
	    String cleanTagNumber = tagId.replace("tag", ""); 
	    String logMessage = "Server expected " + anchor.getDeviceName() + " to measure tag " + cleanTagNumber + " in " + time;
	    
	    try (FileWriter fw = new FileWriter("Server_Expected_Logs.txt", true);
	         PrintWriter pw = new PrintWriter(fw)) {
	        pw.println(logMessage);
	    } catch (IOException e) {
	        System.err.println("Could not write to server log file: " + e.getMessage());
	    }
	}

	public String getMeasurmentResponse(Anchor anchor, long executionTime, long scanTime, long safetyBuffer) {
	    if (executionTime != this.currentCycleExecutionTime) {
	        this.currentCycleExecutionTime = executionTime;
	        
	        long activeThreshold = System.currentTimeMillis() - 10000; 
	        
	        this.lockedAnchorList = this.listOfAnchors.values().stream()
	            .filter(a -> a.getLastSeen() > activeThreshold)
	            .collect(java.util.stream.Collectors.toList());
	            
	        this.lockedTagList = new ArrayList<>(this.listOfTags.values());
	    }

	    JSONObject jsonObject = new JSONObject();
	    try {
	        jsonObject.put("actionToExecute", "measure");
	        JSONArray tagsArray = new JSONArray();

	        int anchorIndex = this.lockedAnchorList.indexOf(anchor);

	        if (anchorIndex == -1) {
	            anchorIndex = this.lockedAnchorList.size(); 
	        }

	        int anchorCount = this.lockedAnchorList.size();
	        long slotTime = scanTime + (2 * safetyBuffer);

	        for (int i = 0; i < this.lockedTagList.size(); i++) {
	            Tag tag = this.lockedTagList.get(i);
	            if (tag == null) continue;

	            long slotStart = executionTime + ((long) i * anchorCount * slotTime) + ((long) anchorIndex * slotTime);
	            long timeToMeasure = slotStart + safetyBuffer;

	            logServerExpectation(anchor, tag.getDeviceName(), timeToMeasure);

	            JSONObject tagJson = new JSONObject();
	            tagJson.put("deviceID", tag.getDeviceName());
	            tagJson.put("whenToExecute", timeToMeasure);
	            tagsArray.put(tagJson);
	        }

	        jsonObject.put("tags", tagsArray);

	    } catch (JSONException e) {
	        e.printStackTrace();
	        return "{\"error\":\"Failed to create measurement response JSON.\"}";
	    }
	    return jsonObject.toString();
	}
	
	/**
	 * Generates a JSON string to force an anchor to register.
	 * * @return A JSON string representing a register command.
	 */
	public String getRegisterResponse() {
	    JSONObject jsonObject = new JSONObject();
	    jsonObject.put("actionToExecute", "register"); // Or whatever command your firmware expects
	    return jsonObject.toString();
	}
}