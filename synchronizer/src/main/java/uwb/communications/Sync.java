package uwb.communications;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import uwb.config.Config;
import uwb.devices.Anchor;
import uwb.devices.Tag;
import uwb.managers.ActionManager;
import uwb.managers.Synchronizer;
import uwb.measurements.Measurement;
import uwb.measurements.Reading;
import uwb.managers.ActionManager.Action;

@WebServlet("/Synchronizer/*")
public class Sync extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final String PATH_BOOT = "/anchorRegistration";
    private static final String PATH_MEASURE = "/measurementReport";
    private static final String PATH_SCAN = "/scanReport";
    
    private ActionManager actionManager = new ActionManager();
    private Synchronizer synchronizer = new Synchronizer();
    private String outputUrl;
    private Config config;

    public Sync() {
        super();
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                System.err.println("Sorry, unable to find config.properties");
                throw new ServletException("config.properties file not found.");
            }
            props.load(input);
            this.config = new Config(props);
            
        } catch (IOException ex) {
            ex.printStackTrace();
            throw new ServletException("Error loading config.properties", ex);
        }

        this.outputUrl = this.config.getPeUrl();
        this.actionManager = new ActionManager(
            this.config.getAmSlowScanPeriod(),
            this.config.getAmFastScanPeriod(),
            this.config.getAmScanInterval(),
            this.config.getAmScanTime()
        );
        
        super.init(config);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing path information (e.g., /boot, /measure, /scan)");
            return;
        }

        String jsonString = request.getParameter("jsondata");
        System.err.println("Received pathInfo: " + pathInfo);
        System.err.println("Received jsondata: " + jsonString);

        if (jsonString == null || jsonString.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing 'jsondata' parameter.");
            return;
        }

        try (PrintWriter writer = response.getWriter()) {
            JSONObject jsonObj = new JSONObject(jsonString);
            String responseString = null;

            if (PATH_BOOT.equals(pathInfo)) {
                responseString = handleBootRequest(jsonObj);
            } else if (PATH_MEASURE.equals(pathInfo)) {
                responseString = handleMeasureRequest(jsonObj);
            } else if (PATH_SCAN.equals(pathInfo)) {
                responseString = handleScanRequest(jsonObj);
            } else {
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Unknown path: " + pathInfo);
                return;
            }
            
            if (responseString != null) {
                writer.write(responseString);
                response.setStatus(HttpServletResponse.SC_OK);
            } else {
                 sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: Null response generated.");
            }
        } catch (JSONException e) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format: " + e.getMessage());
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private synchronized String handleBootRequest(JSONObject jsonObj) throws JSONException {
        if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
            return "{\"error\":\"Missing or invalid 'anchorID' in boot request.\"}";
        }
        String id = jsonObj.getString("anchorID");
        System.out.println("Boot request for anchorID: " + id);

        Anchor anchor = new Anchor(id, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        this.synchronizer.addNewAnchor(anchor);
        
        return this.getResponse(anchor);
    }

    private synchronized String handleMeasureRequest(JSONObject jsonObj) throws JSONException {
        if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
            return "{\"error\":\"Missing or invalid 'anchorID' in measure request.\"}";
        }
        String anchorID = jsonObj.getString("anchorID");

        if (!jsonObj.has("tags") || !(jsonObj.get("tags") instanceof JSONArray)) {
            return "{\"error\":\"Missing or invalid 'tags' array in measure request.\"}";
        }
        JSONArray tagArray = jsonObj.getJSONArray("tags");

        Anchor anchor = this.synchronizer.listOfAnchors.get(anchorID);
        
        for (int i = 0; i < tagArray.length(); i++) {
            JSONObject obj = tagArray.getJSONObject(i);
            
            if (obj.has("tagID") && obj.get("tagID") instanceof String &&
                obj.has("distance") && obj.get("distance") instanceof Number &&
                obj.has("executedAt") && obj.get("executedAt") instanceof Number) {

                String tagID = obj.getString("tagID");
                Number distance = obj.getNumber("distance");
                Number executedAt = obj.getNumber("executedAt");
                
                Tag tag = this.synchronizer.listOfTags.get(tagID);
                if (tag != null && tag.getMeasurements() != null && !tag.getMeasurements().isEmpty()) {
                    Measurement lastMeasurement = tag.getMeasurements().getLast();
                    if(lastMeasurement.checkIfValid(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())) {
                        Reading reading = new Reading(anchor, distance.longValue(), executedAt.longValue(), 5);
                        List<Reading> readings = lastMeasurement.getReadings();
                        readings.add(reading);
                        lastMeasurement.setReadings(readings);
                    }
                    System.out.println("Measure: tagID=" + tagID + ", distance=" + distance + ", executedAt=" + executedAt);
                }
            } else {
                System.err.println("Invalid tag object in measure request: " + obj.toString());
            }
        }
        
        List<Tag> tagList = new ArrayList<>(this.synchronizer.listOfTags.values());
        List<Anchor> anchorList = new ArrayList<>(this.synchronizer.listOfAnchors.values());
        
        if (!tagList.isEmpty() && !anchorList.isEmpty()) {
            if (tagList.getFirst().getMeasurements().getFirst().getReadings().size() == anchorList.size()) {
                startOutputThread(tagList);
            }
        }
        
        return this.getResponse(anchor);
    }

    private synchronized String handleScanRequest(JSONObject jsonObj) throws JSONException {
        if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
            return "{\"error\":\"Missing or invalid 'anchorID' in scan request.\"}";
        }
        String anchorID = jsonObj.getString("anchorID");

        if (!jsonObj.has("tags") || !(jsonObj.get("tags") instanceof JSONArray)) {
            return "{\"error\":\"Missing or invalid 'tags' array in scan request.\"}";
        }
        JSONArray tagArray = jsonObj.getJSONArray("tags");

        for (int i = 0; i < tagArray.length(); i++) {
            JSONObject obj = tagArray.getJSONObject(i);
            if (obj.has("tagID") && obj.get("tagID") instanceof String) {
                String tagID = obj.getString("tagID");
                Tag tag = new Tag(tagID, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                if (!this.synchronizer.tagExists(tag)) {
                    this.synchronizer.addNewTag(tag);
                }
            } else {
                System.err.println("Invalid tag object in scan request: " + obj.toString());
            }
        }
        
        Anchor anchor = this.synchronizer.listOfAnchors.get(anchorID);
        return this.getResponse(anchor);
    }
    
    private void startOutputThread(List<Tag> tagList) {
        if (tagList == null || tagList.isEmpty()) {
            System.err.println("Tag list is null or empty. Cannot start output thread.");
            return;
        }

        List<Tag> validTags = new ArrayList<>();
        for (Tag tag : tagList) {
            if (tag != null) {
                validTags.add(tag);
            } else {
                System.err.println("Null tag found in the list for OutputThread. Skipping.");
            }
        }

        try {
            OutputThread output = new OutputThread(validTags, this.outputUrl);
            output.start();
        } finally {
            this.synchronizer.addMeasurementRound(this.actionManager.getActionStartingTime(), this.actionManager.getChannelBusyUntil());
        }
    }
    
    private String getResponse(Anchor anchor) {
        Action action = this.actionManager.nextAction();
        String response = null;

        if (this.synchronizer.listOfTags.isEmpty()){
            response = this.synchronizer.getSlowScanResponse(this.actionManager.getSlowScanTime());
        } else if (action == Action.FAST_SCAN) {
            response = this.synchronizer.getFastScanResponse(this.actionManager.getFastScanTime());
        } else {
            response = this.synchronizer.getMeasurmentResponse(anchor, this.actionManager.getMeasurmentTime(this.synchronizer.listOfAnchors.size(), this.synchronizer.listOfTags.size()), this.actionManager.getScanTime());
            List<Tag> tagList = new ArrayList<>(this.synchronizer.listOfTags.values());
            if (!tagList.isEmpty()) {
                Tag tag = tagList.getFirst();
                if (tag != null && tag.getMeasurements().size() == 0) {
                    this.synchronizer.addMeasurementRound(this.actionManager.getActionStartingTime(), this.actionManager.getChannelBusyUntil());
                } else {
                    if (tag != null && tag.getMeasurements().getLast() != null && !tag.getMeasurements().getLast().checkIfValid(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())) {
                        startOutputThread(new ArrayList<>(this.synchronizer.listOfTags.values()));
                    }
                }
            }
        }
        
        return (response != null) ? response : "{\"error\":\"Internal server error: Null response generated.\"}";
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        try (PrintWriter writer = response.getWriter()) {
            writer.write("{\"error\": \"" + message + "\"}");
        }
    }
}