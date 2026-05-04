package pt.um.ucl.positioning.C03a.uwb.managers;

import java.io.File; // ---> NEW <---
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths; // ---> NEW <---
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pt.um.ucl.positioning.C03a.uwb.config.Config;
import pt.um.ucl.positioning.C03a.uwb.devices.Anchor;
import pt.um.ucl.positioning.C03a.uwb.devices.Tag;
import pt.um.ucl.positioning.C03a.uwb.measurements.Measurement;

public class Synchronizer {
	
	public Map<String, Tag> listOfTags;
	public Map<String, Anchor> listOfAnchors;
	public Set<String> whitelistOfTags;
	public Set<String> whitelistOfAnchors;
	
    public static class RoundPlan {
        public final long executionTime;
        public final long completionTime;
        public final List<Anchor> anchors;
        public final List<Tag> tags;
        public final Set<String> dispatchedAnchors = ConcurrentHashMap.newKeySet();

        public RoundPlan(long executionTime, long completionTime, List<Anchor> anchors, List<Tag> tags) {
            this.executionTime = executionTime;
            this.completionTime = completionTime;
            this.anchors = anchors;
            this.tags = tags;
        }
    }

    private final Queue<RoundPlan> upcomingRounds = new ConcurrentLinkedQueue<>();

	public Synchronizer(Map<String, Tag> listOfTags, Map<String, Anchor> listOfAnchors) {
		super();
		this.listOfTags = listOfTags;
		this.listOfAnchors = listOfAnchors;
	}
	
	public Synchronizer() {
		this.listOfTags = new ConcurrentHashMap<String, Tag>();
		this.listOfAnchors = new ConcurrentHashMap<String, Anchor>();
		this.whitelistOfTags = ConcurrentHashMap.newKeySet();
		this.whitelistOfAnchors = ConcurrentHashMap.newKeySet();
	}
	
	public synchronized void addNewAnchor(Anchor anchor) {
		this.listOfAnchors.put(anchor.getDeviceName(), anchor);
	}
	
	public boolean anchorExists(Anchor anchor) {
		return this.listOfAnchors.containsKey(anchor.getDeviceName());
	}
	
	public synchronized void addNewTag(Tag tag) {
		this.listOfTags.put(tag.getDeviceName(), tag);
	}
	
	public boolean tagExists(Tag tag) {
		return this.listOfTags.containsKey(tag.getDeviceName());
	}
	
	public synchronized List<Tag> getTagList() {
	    return new ArrayList<>(this.listOfTags.values());
	}

	public synchronized List<Anchor> getAnchorList() {
	    return new ArrayList<>(this.listOfAnchors.values());
	}

	public synchronized void addMeasurementRound(long startTime, long endTime) {
	    long bufferMillis = 5000; 
	    for (Tag tag : listOfTags.values()) {
	        if (tag == null) continue;
	        
	        List<Measurement> measurements = tag.getMeasurements();
	        while (measurements.size() > 10) {
	            measurements.remove(0); 
	        }
	        
	        measurements.add(new Measurement(tag, startTime - bufferMillis, endTime + bufferMillis));
	    }
	}
	
	public String getSlowScanResponse(long executionTime) {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put("actionToExecute", "slowScan");
			jsonObject.put("whenToExecute", executionTime);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"error\":\"Failed to create slowScan response JSON.\"}";
		}
		return jsonObject.toString();
	}
	
	public String getFastScanResponse(long executionTime) {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put("actionToExecute", "fastScan");
			jsonObject.put("whenToExecute", executionTime);
		} catch (JSONException e) {
			e.printStackTrace();
			return "{\"error\":\"Failed to create fastScan response JSON.\"}";
		}
		return jsonObject.toString();
	}

    private void logServerExpectation(Anchor anchor, String tagId, long time, Config config) {
        if (!config.isEnableExecutionComparison()) return;
        
        File logDir = new File(config.getLogDirectory());
        if (!logDir.exists()) logDir.mkdirs(); // Safely create the folder if it doesn't exist
        
        String cleanTagNumber = tagId.replace("tag", ""); 
        String logMessage = "SCHED," + anchor.getDeviceName() + "," + cleanTagNumber + "," + time;
        String fullPath = Paths.get(config.getLogDirectory(), "server_scheduled.txt").toString();
        
        try (FileWriter fw = new FileWriter(fullPath, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(logMessage);
        } catch (IOException e) {
            System.err.println("Could not write to server_scheduled.txt: " + e.getMessage());
        }
    }
	
    public String getMeasurmentResponse(Anchor requestingAnchor, long scanTime, long safetyBuffer, Config config) {
        long now = System.currentTimeMillis();

        upcomingRounds.removeIf(round -> round.completionTime < now - 10000);

        RoundPlan targetRound = null;
        for (RoundPlan round : upcomingRounds) {
            if (round.anchors.contains(requestingAnchor) && !round.dispatchedAnchors.contains(requestingAnchor.getDeviceName())) {
                targetRound = round;
                break;
            }
        }

        if (targetRound == null) {
            long activeThreshold = now - 10000;
            List<Anchor> activeAnchors = this.listOfAnchors.values().stream()
                .filter(a -> a.getLastSeen() > activeThreshold)
                .collect(Collectors.toList());
            List<Tag> activeTags = new ArrayList<>(this.listOfTags.values());

            if (!activeAnchors.contains(requestingAnchor)) {
                activeAnchors.add(requestingAnchor);
            }

            int aCount = Math.max(1, activeAnchors.size());
            long slotTime = scanTime + (2 * safetyBuffer);
            long cycleDuration = aCount * activeTags.size() * slotTime;

            long nextStartTime = now + 1000; 
            
            RoundPlan lastRound = null;
            for (RoundPlan round : upcomingRounds) {
                lastRound = round; 
            }
            
            if (lastRound != null && lastRound.completionTime > now) {
                nextStartTime = lastRound.completionTime + 100; 
            }

            targetRound = new RoundPlan(nextStartTime, nextStartTime + cycleDuration, activeAnchors, activeTags);
            upcomingRounds.add(targetRound);
            
            this.addMeasurementRound(nextStartTime, nextStartTime + cycleDuration);
        }

        targetRound.dispatchedAnchors.add(requestingAnchor.getDeviceName());

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("actionToExecute", "measure");
            JSONArray tagsArray = new JSONArray();

            int anchorIndex = targetRound.anchors.indexOf(requestingAnchor);
            int anchorCount = targetRound.anchors.size();
            long slotTime = scanTime + (2 * safetyBuffer);

            for (int i = 0; i < targetRound.tags.size(); i++) {
                Tag tag = targetRound.tags.get(i);
                if (tag == null) continue;

                long slotStart = targetRound.executionTime + ((long) i * anchorCount * slotTime) + ((long) anchorIndex * slotTime);
                long timeToMeasure = slotStart + safetyBuffer;

                if (timeToMeasure < now - 500) continue; 

                logServerExpectation(requestingAnchor, tag.getDeviceName(), timeToMeasure, config);

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

	public String getRegisterResponse() {
	    JSONObject jsonObject = new JSONObject();
	    jsonObject.put("actionToExecute", "register"); 
	    return jsonObject.toString();
	}
}