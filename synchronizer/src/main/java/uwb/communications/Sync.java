package uwb.communications;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

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
import uwb.database.MeasurementsDatabaseLogger; 
import uwb.devices.Anchor;
import uwb.devices.Tag;
import uwb.managers.ActionManager;
import uwb.managers.Synchronizer;
import uwb.measurements.Measurement;
import uwb.measurements.Reading;
import uwb.managers.ActionManager.Action;

@WebServlet(urlPatterns = "/Synchronizer/*", loadOnStartup = 1) 
public class Sync extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final String PATH_BOOT = "/anchorRegistration";
    private static final String PATH_MEASURE = "/measurementReport";
    private static final String PATH_SCAN = "/scanReport";
    
    // Core Managers
    private ActionManager actionManager;
    private Synchronizer synchronizer = new Synchronizer();
    
    // Database and Output Handlers
    private MeasurementsDatabaseLogger dbLogger; 
    private OutputThread outputManager;         
    private Config config;

    public Sync() {
        super();
    }

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ServletException("Missing JDBC Driver. MariaDB JDBC Driver not found.", e);
        }
        
        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new ServletException("config.properties file not found.");
            }
            props.load(input);
            this.config = new Config(props);
            
        } catch (IOException ex) {
            throw new ServletException("Error loading config.properties", ex);
        }
        
        this.actionManager = new ActionManager(
            this.config.getAmSlowScanPeriod(),
            this.config.getAmFastScanPeriod(),
            this.config.getAmScanInterval(),
            this.config.getAmScanTime()
        );
        
        // **********************************************
        // ********* FIX: INITIALIZE LOGGER **********
        // **********************************************
        // REMOVED: javax.sql.DataSource dummyDataSource = null; 
        
        try {
            // Logger now only accepts Config object
            this.dbLogger = new MeasurementsDatabaseLogger(this.config);
            this.dbLogger.initializeDatabase();
            
            this.outputManager = new OutputThread(this.config.getPeUrl(), this.dbLogger);
            
        } catch (Exception e) {
            throw new ServletException("Failed to initialize database or output manager.", e);
        }
        // **********************************************
    }
    
    @Override
    public void destroy() {
        if (this.outputManager != null) {
            this.outputManager.shutdown();
        }
        super.destroy();
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String pathInfo = request.getPathInfo();
        
        if (pathInfo == null || pathInfo.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing path information.");
            return;
        }

        // --- FIX: Read as FORM PARAMETER since client uses x-www-form-urlencoded ---
        String jsonString = request.getParameter("jsondata"); 

        if (jsonString == null || jsonString.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing 'jsondata' parameter in the form data.");
            return;
        }

        String responseString = null;

        try {
            // FIX: Trim the parameter's value to remove leading/trailing spaces/newlines
            String trimmedJson = jsonString.trim();
            
            if (trimmedJson.isEmpty()) {
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "JSON payload contained only whitespace.");
                return;
            }

            JSONObject jsonObj = new JSONObject(trimmedJson);

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
            
            // --- Explicit Response Write ---
            if (responseString != null) {
                response.setStatus(HttpServletResponse.SC_OK);
                try (PrintWriter writer = response.getWriter()) {
                    writer.write(responseString);
                    writer.flush();
                }
            } else {
                 sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error: Null response generated.");
            }
        } catch (JSONException e) {
            // The trimmedJson now contains the malformed content, which helps debugging.
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format: " + e.getMessage());
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String handleBootRequest(JSONObject jsonObj) throws JSONException {
        if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
            return "{\"error\":\"Missing or invalid 'anchorID' in boot request.\"}";
        }

        String id = jsonObj.getString("anchorID");	
        Anchor anchor = new Anchor(id, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        anchor.setDeviceID(this.dbLogger.saveAnchor(anchor));
        this.synchronizer.addNewAnchor(anchor);
        
        return this.getResponse(anchor);
    }

    private String handleMeasureRequest(JSONObject jsonObj) throws JSONException {
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
                }
            }
        }
        
        List<Tag> tagList = new ArrayList<>(this.synchronizer.listOfTags.values());
        List<Anchor> anchorList = new ArrayList<>(this.synchronizer.listOfAnchors.values());
        
        // Condition to trigger output: all tags have readings from all anchors
        if (!tagList.isEmpty() && !anchorList.isEmpty() && tagList.getFirst() != null && tagList.getFirst().getMeasurements() != null && !tagList.getFirst().getMeasurements().isEmpty()) {
            if (tagList.getFirst().getMeasurements().getLast().getReadings().size() == anchorList.size()) {
                startOutputProcess(tagList);
            }
        }
        
        return this.getResponse(anchor);
    }

    private String handleScanRequest(JSONObject jsonObj) throws JSONException {
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
                    tag.setDeviceID(this.dbLogger.saveTarget(tag));
                    this.synchronizer.addNewTag(tag);
                }
            }
        }
        
        Anchor anchor = this.synchronizer.listOfAnchors.get(anchorID);
        return this.getResponse(anchor);
    }
    
    // --- ASYNCHRONOUS OUTPUT METHOD ---
    
    private void startOutputProcess(List<Tag> tagList) {
        if (tagList == null || tagList.isEmpty()) {
            return;
        }

        List<Tag> validTags = new ArrayList<>();
        for (Tag tag : tagList) {
            if (tag != null) {
                validTags.add(tag);
            }
        }
        
        if (validTags.isEmpty()) {
            return;
        }

        this.outputManager.submitTagBatch(validTags);

        this.synchronizer.addMeasurementRound(this.actionManager.getActionStartingTime(), this.actionManager.getChannelBusyUntil());
    }
    
    // --- RESPONSE AND ERROR METHODS ---
    
    private String getResponse(Anchor anchor) {
        Action action = this.actionManager.nextAction();
        String response = null;

        List<Tag> tagList = new ArrayList<>(this.synchronizer.listOfTags.values());

        if (this.synchronizer.listOfTags.isEmpty()){
            response = this.synchronizer.getSlowScanResponse(this.actionManager.getSlowScanTime());
        } else if (action == Action.FAST_SCAN) {
            response = this.synchronizer.getFastScanResponse(this.actionManager.getFastScanTime());
        } else {
            response = this.synchronizer.getMeasurmentResponse(anchor, this.actionManager.getMeasurmentTime(this.synchronizer.listOfAnchors.size(), this.synchronizer.listOfTags.size()), this.actionManager.getScanTime());
            
            if (!tagList.isEmpty()) {
                Tag tag = tagList.getFirst();
                if (tag != null) {
                    if (tag.getMeasurements().isEmpty() || 
                        !tag.getMeasurements().getLast().checkIfValid(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())) {
                        
                        if (!tag.getMeasurements().isEmpty() && 
                            !tag.getMeasurements().getLast().checkIfValid(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())) {
                            
                            startOutputProcess(new ArrayList<>(this.synchronizer.listOfTags.values()));
                        }
                        this.synchronizer.addMeasurementRound(this.actionManager.getActionStartingTime(), this.actionManager.getChannelBusyUntil());
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
            writer.flush();
        }
    }
}