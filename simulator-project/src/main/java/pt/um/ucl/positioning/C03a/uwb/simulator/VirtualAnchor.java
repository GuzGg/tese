package pt.um.ucl.positioning.C03a.uwb.simulator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

import pt.um.ucl.positioning.C03a.uwb.devices.Anchor;
import pt.um.ucl.positioning.C03a.uwb.devices.Tag;

/**
 * Simulates the behaviour of a physical UWB anchor device.
 */
public class VirtualAnchor extends Anchor {

    private static final Logger logger = Logger.getLogger(VirtualAnchor.class.getName());

    private static final int MAX_TAGS = 4;

    private List<Tag> listOfTags;
    private int nextTagID;
    private final String logFileName;
    private final Random random = new Random();

    public VirtualAnchor(String deviceId, long initializedAt, long lastSeen, int initialTags) {
        super(deviceId, initializedAt, lastSeen);
        this.nextTagID = 0;
        this.listOfTags = new ArrayList<>();
        
        int count = Math.min(initialTags, MAX_TAGS);
        for (int i = 0; i < count; i++) {
            long now = System.currentTimeMillis(); 
            this.listOfTags.add(new Tag("tag" + nextTagID, now, now));
            this.nextTagID++;
        }
        
        logger.info(deviceId + " initialized with " + this.listOfTags.size() + " tag(s).");
        this.logFileName = deviceId.replace(" ", "_") + "_logs.txt";
    }

    public VirtualAnchor(String deviceId, long initializedAt, long lastSeen) {
        this(deviceId, initializedAt, lastSeen, 1);
    }

    private void logMeasurement(String tagId, long scheduledTime) {
        String cleanTag = tagId.replace("tag", "");
        String line = getDeviceName() + " scheduled to measure tag " + cleanTag + " in " + scheduledTime;
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFileName, true))) {
            pw.println(line);
        } catch (IOException e) {
            logger.warning("Could not write to log file: " + e.getMessage());
        }
    }

    public JSONObject VirtualBehaviour(JSONObject response) {
        String actionToExecute = response.getString("actionToExecute");

        JSONObject replyPayload = new JSONObject();
        replyPayload.put("anchorID", getDeviceName());

        if ("slowScan".equals(actionToExecute) || "fastScan".equals(actionToExecute)) {
            handleScan(replyPayload, response);
        } else if ("measure".equals(actionToExecute)) {
            handleMeasure(replyPayload, response);
        }

        logger.info(getDeviceName() + " replying: " + replyPayload);
        return replyPayload;
    }

    private void handleScan(JSONObject replyPayload, JSONObject response) {
        JSONArray tagsArray = new JSONArray();
        this.listOfTags.forEach(tag -> {
            JSONObject tagObj = new JSONObject();
            tagObj.put("tagID", tag.getDeviceName());
            tagsArray.put(tagObj);
        });
        replyPayload.put("tags", tagsArray);
    }

    private void handleMeasure(JSONObject replyPayload, JSONObject response) {
        JSONArray responseTags = response.getJSONArray("tags");

        List<JSONObject> scheduledTasks = new ArrayList<>();
        for (int i = 0; i < responseTags.length(); i++) {
            scheduledTasks.add(responseTags.getJSONObject(i));
        }

        // ---> CRITICAL FIX: Anti-spin lock. If server filtered out past tasks, wait for next cycle.
        if (scheduledTasks.isEmpty()) {
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            replyPayload.put("tags", new JSONArray());
            return;
        }

        scheduledTasks.sort((a, b) -> Long.compare(a.getLong("whenToExecute"), b.getLong("whenToExecute")));

        JSONArray tagsArray = new JSONArray();

        for (JSONObject task : scheduledTasks) {
            String tagId = task.getString("deviceID");
            long scheduledTime = task.getLong("whenToExecute");

            boolean tagExists = this.listOfTags.stream().anyMatch(t -> t.getDeviceName().equals(tagId));
            if (!tagExists) continue;

            logMeasurement(tagId, scheduledTime);

            long sleepTime = scheduledTime - System.currentTimeMillis();

            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warning(getDeviceName() + ": sleep interrupted for tag " + tagId);
                }
            }

            long actualExecutionTime = System.currentTimeMillis();
            float distance = random.nextFloat() * 40;

            logger.info(getDeviceName() + " measured " + tagId
                + " | scheduled=" + scheduledTime
                + " | actual=" + actualExecutionTime
                + " | drift=" + (actualExecutionTime - scheduledTime) + "ms"
                + " | distance=" + String.format("%.2f", distance));

            JSONObject tagObj = new JSONObject();
            tagObj.put("tagID", tagId);
            tagObj.put("distance", distance);
            tagObj.put("executedAt", actualExecutionTime); 
            tagsArray.put(tagObj);
        }

        replyPayload.put("tags", tagsArray);
    }
}