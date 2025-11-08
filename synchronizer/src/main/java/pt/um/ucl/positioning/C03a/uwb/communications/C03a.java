package pt.um.ucl.positioning.C03a.uwb.communications;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.Connection; 
import java.sql.DriverManager; 
import java.sql.SQLException; 
import java.sql.SQLFeatureNotSupportedException; 
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.logging.Level;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import pt.um.ucl.positioning.C03a.uwb.config.Config;
import pt.um.ucl.positioning.C03a.uwb.database.MeasurementsDatabaseLogger; 
import pt.um.ucl.positioning.C03a.uwb.devices.Anchor;
import pt.um.ucl.positioning.C03a.uwb.devices.Tag;
import pt.um.ucl.positioning.C03a.uwb.managers.ActionManager;
import pt.um.ucl.positioning.C03a.uwb.managers.Synchronizer;
import pt.um.ucl.positioning.C03a.uwb.measurements.Measurement;
import pt.um.ucl.positioning.C03a.uwb.measurements.Reading;
import pt.um.ucl.positioning.C03a.uwb.managers.ActionManager.Action;

public class C03a extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    private static final Logger logger = Logger.getLogger(C03a.class.getName());

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

    public C03a() {
        super();
    }

    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        logger.info("Sync Servlet initializing...");
        
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Missing JDBC Driver. MariaDB JDBC Driver not found.", e);
            throw new ServletException("Missing JDBC Driver. MariaDB JDBC Driver not found.", e);
        }
        
        Properties props = new Properties();
        try (InputStream input = servletConfig.getServletContext().getResourceAsStream("/WEB-INF/config.properties")) {
            if (input == null) {
                logger.severe("config.properties file not found.");
                throw new ServletException("config.properties file not found.");
            }
            props.load(input);
            this.config = new Config(props);
            logger.info("Configuration loaded successfully.");
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error loading config.properties", ex);
            throw new ServletException("Error loading config.properties", ex);
        }
        
        this.actionManager = new ActionManager(
            this.config.getAmSlowScanPeriod(),
            this.config.getAmFastScanPeriod(),
            this.config.getAmScanInterval(),
            this.config.getAmScanTime()
        );
                
        final String dbUrl = this.config.getDbUrl() + "/" + this.config.getDbName();
        final String user = this.config.getDbUsername();
        final String password = this.config.getDbPassword();

        DataSource dataSource = new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(dbUrl, user, password);
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException { return getConnection(); }
            @Override public PrintWriter getLogWriter() throws SQLException { return null; }
            @Override public void setLogWriter(PrintWriter out) throws SQLException { /* NOP */ }
            @Override public void setLoginTimeout(int seconds) throws SQLException { /* NOP */ }
            @Override public int getLoginTimeout() throws SQLException { return 0; }
            @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not supported"); }
            @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
            @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException("Not supported"); }
        };

        try {
            this.dbLogger = new MeasurementsDatabaseLogger(dataSource, this.config); 
            
            // The output manager is configured using peUrl from config.properties
            this.outputManager = new OutputThread(this.config.getPeUrl(), this.dbLogger, config.isExportToDbQ(), config.isExportToPeQ(), config.getPeToken());
            logger.info("Database and Output Manager initialized successfully.");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize database connection or output manager.", e);
            throw new ServletException("Failed to initialize database connection or output manager. " + 
                                       "Check database credentials/availability.", e);
        }
        logger.info("Sync is ready and fully initialized.");
    }
    
    @Override
    public void destroy() {
        if (this.outputManager != null) {
            this.outputManager.shutdown();
            logger.info("OutputManager shutdown.");
        }
        super.destroy();
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String pathInfo = request.getPathInfo();
        
        logger.info("Received POST request on path: " + pathInfo);

        if (pathInfo == null || pathInfo.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing path information.");
            return;
        }

        String jsonString;
        try (BufferedReader reader = request.getReader()) {
            jsonString = reader.lines().collect(Collectors.joining());
        } catch (IOException e) {
            logger.warning("Error reading request body: " + e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Error reading request body.");
            return;
        }
        String trimmedJson = jsonString.trim();
        if (trimmedJson.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing or empty JSON request body.");
            return;
        }

        String responseString = null;

        try {
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
            logger.warning("Invalid JSON received on path " + pathInfo + ": " + e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format: " + e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An internal server error occurred during POST for " + pathInfo, e);
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String handleBootRequest(JSONObject jsonObj) throws JSONException {
        if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
            logger.warning("Boot request missing or invalid 'anchorID'.");
            return "{\"error\":\"Missing or invalid 'anchorID' in boot request.\"}";
        }

        String id = jsonObj.getString("anchorID");	
        Anchor anchor = new Anchor(id, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        anchor.setDeviceID(this.dbLogger.saveAnchor(anchor));
        this.synchronizer.addNewAnchor(anchor);
        
        logger.info("Registered new anchor: " + id + " with database ID: " + anchor.getDeviceID());
        return this.getResponse(anchor);
    }

    private String handleMeasureRequest(JSONObject jsonObj) throws JSONException {
        if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
            logger.warning("Measure request missing or invalid 'anchorID'.");
            return "{\"error\":\"Missing or invalid 'anchorID' in measure request.\"}";
        }
        String anchorID = jsonObj.getString("anchorID");
        logger.fine("Processing measurement report from Anchor: " + anchorID);

        if (!jsonObj.has("tags") || !(jsonObj.get("tags") instanceof JSONArray)) {
            logger.warning("Measure request missing or invalid 'tags' array.");
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
                        lastMeasurement.getReadings().add(reading);
                    } else {
                         logger.warning("Dropped reading for tag " + tagID + " from anchor " + anchorID + ": Measurement is stale/invalid.");
                    }
                }
            }
        }
        
        List<Tag> tagList = new ArrayList<>(this.synchronizer.listOfTags.values());
        List<Anchor> anchorList = new ArrayList<>(this.synchronizer.listOfAnchors.values());
        
        boolean measurementIsComplete = tagList.stream().anyMatch(tag -> 
            tag != null && 
            !tag.getMeasurements().isEmpty() && 
            tag.getMeasurements().getLast().getReadings().size() == anchorList.size()
        );

        if (measurementIsComplete) {
            logger.info("MEASUREMENT ROUND COMPLETE. At least one tag received reports from all " + anchorList.size() + " anchors. Submitting data and resetting state.");
            
            startOutputProcess(tagList); 
            
            this.synchronizer.addMeasurementRound(this.actionManager.getActionStartingTime(), this.actionManager.getChannelBusyUntil());
        }
        
        return this.getResponse(anchor);
    }

    private String handleScanRequest(JSONObject jsonObj) throws JSONException {
        if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
            logger.warning("Scan request missing or invalid 'anchorID'.");
            return "{\"error\":\"Missing or invalid 'anchorID' in scan request.\"}";
        }
        String anchorID = jsonObj.getString("anchorID");

        if (!jsonObj.has("tags") || !(jsonObj.get("tags") instanceof JSONArray)) {
            logger.warning("Scan request missing or invalid 'tags' array.");
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
                    logger.info("Discovered and registered new tag: " + tagID + " with database ID: " + tag.getDeviceID());
                }
            }
        }
        
        Anchor anchor = this.synchronizer.listOfAnchors.get(anchorID);
        return this.getResponse(anchor);
    }
    
    private void startOutputProcess(List<Tag> tagList) {
        if (tagList == null || tagList.isEmpty()) {
            return;
        }

        List<Tag> tagsToSubmit = new ArrayList<>();
        
        List<Measurement> measurementsToFlag = new ArrayList<>(); 

        for (Tag tag : tagList) {
            if (tag != null && !tag.getMeasurements().isEmpty()) {
                Measurement measurement = tag.getMeasurements().getLast();
                
                if (!measurement.getReadings().isEmpty() && !measurement.getSentForOutput()) { 
                    
                    Tag tagClone = new Tag(tag.getDeviceName(), tag.getinitializedAt(), tag.getLastSeen());
                    tagClone.setDeviceID(tag.getDeviceID());
                    tagClone.getMeasurements().add(measurement); 
                    
                    tagsToSubmit.add(tagClone);
                    measurementsToFlag.add(measurement);
                } else if (tag != null && !tag.getMeasurements().isEmpty()) {
                    if (measurement.getReadings().isEmpty()) {
                        logger.warning("Output process skipped tag " + tag.getDeviceName() + ": Last measurement has no readings.");
                    } else {
                        logger.fine("Output process skipped tag " + tag.getDeviceName() + ": Last measurement already submitted.");
                    }
                }
            }
        }
        
        if (tagsToSubmit.isEmpty()) {
            logger.info("Output process skipped: No new valid tags with readings found in the batch.");
            return;
        }

        for (Measurement measurement : measurementsToFlag) {
            measurement.setSentForOutput(true);
        }

        this.outputManager.submitTagBatch(tagsToSubmit);
        logger.info("Submitted " + tagsToSubmit.size() + " tag batches to OutputManager for processing.");
    }
    
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
            
            long now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            boolean isStale = tagList.stream()
                .anyMatch(tag -> 
                    tag != null && 
                    !tag.getMeasurements().isEmpty() && 
                    !tag.getMeasurements().getLast().checkIfValid(now)
                );
                
            boolean needsInitialStart = tagList.stream()
                .anyMatch(tag -> tag != null && tag.getMeasurements().isEmpty());

            if (needsInitialStart || isStale) {
                
                if (isStale) {
                    logger.warning("MEASUREMENT ROUND TIMEOUT/STALE. Submitting incomplete data and resetting state.");
                    startOutputProcess(new ArrayList<>(this.synchronizer.listOfTags.values()));
                } else {
                    logger.info("MEASUREMENT ROUND START. Initializing new measurement for tags.");
                }
                
                this.synchronizer.addMeasurementRound(this.actionManager.getActionStartingTime(), this.actionManager.getChannelBusyUntil());
            }
        }
        
        return (response != null) ? response : "{\"error\":\"Internal server error: Null response generated.\"}";
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        logger.warning("Sending error response. Status: " + statusCode + ", Message: " + message);
        response.setStatus(statusCode);
        try (PrintWriter writer = response.getWriter()) {
            writer.write("{\"error\": \"" + message + "\"}");
            writer.flush();
        }
    }
}