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

    private void logMeasurementExpectation(long roundId, String tagId, long timeToWaitMs, long localTargetMillis) {
        String cleanTag = tagId.replace("tag", "");
        String line = "EXPECTED," + roundId + "," + getDeviceName() + "," + cleanTag + ",Wait:" + timeToWaitMs + "ms,Target:" + localTargetMillis;
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFileName, true))) {
            pw.println(line);
        } catch (IOException e) {}
    }

    public JSONObject VirtualBehaviour(JSONObject response) {
        String actionToExecute = response.getString("actionToExecute");
        long localBaselineMillis = System.currentTimeMillis(); 
        long serverBaselineMs = response.has("serverTimeNow") ? response.getLong("serverTimeNow") : localBaselineMillis;
        long currentRoundId = response.optLong("roundId", -1); // Extract long ID

        JSONObject replyPayload = new JSONObject();
        replyPayload.put("anchorID", getDeviceName());

        if ("slowScan".equals(actionToExecute) || "fastScan".equals(actionToExecute)) {
            handleScan(replyPayload, response);
        } else if ("measure".equals(actionToExecute)) {
            handleMeasure(replyPayload, response, localBaselineMillis, serverBaselineMs, currentRoundId);
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

    private void handleMeasure(JSONObject replyPayload, JSONObject response, long localBaselineMillis, long serverBaselineMs, long currentRoundId) {
        JSONArray responseTags = response.getJSONArray("tags");
        long roundStartTime = System.currentTimeMillis();
        
        replyPayload.put("roundId", currentRoundId); // Bounce long ID back

        List<JSONObject> scheduledTasks = new ArrayList<>();
        for (int i = 0; i < responseTags.length(); i++) {
            scheduledTasks.add(responseTags.getJSONObject(i));
        }

        if (scheduledTasks.isEmpty()) {
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            replyPayload.put("tags", new JSONArray());
            return;
        }

        scheduledTasks.sort((a, b) -> Long.compare(a.getLong("whenToExecute"), b.getLong("whenToExecute")));
        JSONArray tagsArray = new JSONArray();
        long ACCEPTANCE_INTERVAL_MS = 20;

        for (JSONObject task : scheduledTasks) {
            String tagId = task.getString("deviceID");
            long scheduledServerTime = task.getLong("whenToExecute");

            boolean tagExists = this.listOfTags.stream().anyMatch(t -> t.getDeviceName().equals(tagId));
            if (!tagExists) continue;

            long timeToWaitMs = scheduledServerTime - serverBaselineMs;
            long localTargetMillis = localBaselineMillis + timeToWaitMs;

            logMeasurementExpectation(currentRoundId, tagId, timeToWaitMs, localTargetMillis);

            if (timeToWaitMs < -ACCEPTANCE_INTERVAL_MS) {
                logger.warning(getDeviceName() + ": Skipping " + tagId + " (Window missed by " + Math.abs(timeToWaitMs) + "ms)");
                continue;
            }

            long sleepTime = localTargetMillis - System.currentTimeMillis();
            if (sleepTime > 0) {
                try { Thread.sleep(sleepTime); } 
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }

            long actualExecutionTimeLocal = System.currentTimeMillis();
            float distance = random.nextFloat() * 40;
            
            long timeSinceLocalBaseline = actualExecutionTimeLocal - localBaselineMillis;
            long serverDomainExecutedAt = serverBaselineMs + timeSinceLocalBaseline;

            logger.info(getDeviceName() + " measured " + tagId
                + " | expectedWait=" + timeToWaitMs + "ms"
                + " | actualWait=" + timeSinceLocalBaseline + "ms"
                + " | jitter=" + (timeSinceLocalBaseline - timeToWaitMs) + "ms");

            JSONObject tagObj = new JSONObject();
            tagObj.put("tagID", tagId);
            tagObj.put("distance", distance);
            tagObj.put("executedAt", serverDomainExecutedAt); 
            tagsArray.put(tagObj);
        }

        long roundEndTime = System.currentTimeMillis();
        replyPayload.put("tags", tagsArray);
        replyPayload.put("actualDurationMs", (roundEndTime - roundStartTime));
    }
}