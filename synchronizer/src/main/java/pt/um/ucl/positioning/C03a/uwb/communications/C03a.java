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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.logging.Level;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
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

/**
 * Main HttpServlet that acts as the central API endpoint for UWB (Ultra-Wideband) anchors.
 * <p>
 * This servlet manages the UWB system's lifecycle by handling anchor registration,
 * processing scan reports for tag discovery, and collecting measurement reports for positioning.
 * It coordinates with {@link ActionManager} to schedule tasks, {@link Synchronizer} to maintain
 * system state, and {@link MeasurementsDatabaseLogger} to persist data.
 * <p>
 * Completed measurements are dispatched to an {@link OutputThread} for asynchronous
 * database logging and forwarding to an external Position Estimator service.
 * <p>
 * The servlet exposes three main POST endpoints:
 * <ul>
 * <li>{@code /anchorRegistration}: For new anchors to join the system.</li>
 * <li>{@code /scanReport}: For anchors to report discovered tags.</li>
 * <li>{@code /measurementReport}: For anchors to submit distance measurements to tags.</li>
 * </ul>
 * 
 * @author Gustavo Oliveira
 * @version 0.1
 */
public class C03a extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    /** Logger for this class. */
    private static final Logger logger = Logger.getLogger(C03a.class.getName());

    /** API endpoint path for anchor registration. */
    private static final String PATH_BOOT = "/anchorRegistration";
    /** API endpoint path for measurement reports. */
    private static final String PATH_MEASURE = "/measurementReport";
    /** API endpoint path for scan reports. */
    private static final String PATH_SCAN = "/scanReport";
    
    /** Manages the timing and sequence of actions (scan, measure). */
    private ActionManager actionManager;
    /** Manages the state (known anchors, tags) of the system. */
    private Synchronizer synchronizer = new Synchronizer(); 
    
    /** Handles persistence of measurements to the database. */
    private MeasurementsDatabaseLogger dbLogger; 
    /** Manages an asynchronous thread pool for data output. */
    private OutputThread outputManager;         
    /** Holds all loaded application configuration properties. */
    private Config config;
    /** Current verion number */
    private String version = "0.1";
    /** Initialization time */
    private LocalDateTime startupTime;

    /**
     * Default constructor.
     */
    public C03a() {
        super();
    }

    /**
     * Initializes the servlet.
     * <p>
     * Loads configuration from {@code config.properties}, sets up the database connection (DataSource),
     * and initializes all manager classes (ActionManager, MeasurementsDatabaseLogger, OutputThread).
     *
     * @param servletConfig The servlet configuration object.
     * @throws ServletException if initialization fails (e.g., config not found, DB driver missing).
     */
    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        logger.info("Sync Servlet initializing...");
        this.startupTime = LocalDateTime.now();
        // Load MariaDB JDBC Driver
        try {
            Class.forName("org.mariadb.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "Missing JDBC Driver. MariaDB JDBC Driver not found.", e);
            throw new ServletException("Missing JDBC Driver. MariaDB JDBC Driver not found.", e);
        }
        
        // Load configuration from config.properties
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
        
        // Initialize Action Manager
        this.actionManager = new ActionManager(
            this.config.getAmSlowScanPeriod(),
            this.config.getAmFastScanPeriod(),
            this.config.getAmScanInterval(),
            this.config.getAmScanTime()
        );
                
        final String dbUrl = this.config.getDbUrl() + "/" + this.config.getDbName();
        final String user = this.config.getDbUsername();
        final String password = this.config.getDbPassword();

        // Create a simple DataSource (not a full connection pool, but abstracts connection creation)
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

        // Initialize DB Logger and Output Manager
        try {
            this.dbLogger = new MeasurementsDatabaseLogger(dataSource, this.config); 
            
            this.outputManager = new OutputThread(
                this.config.getPeUrl(), 
                this.dbLogger, 
                config.isExportToDbQ(), 
                config.isExportToPeQ(), 
                config.getPeToken()
            );
            logger.info("Database and Output Manager initialized successfully.");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to initialize database connection or output manager.", e);
            throw new ServletException("Failed to initialize database connection or output manager. " + 
                                       "Check database credentials/availability.", e);
        }
        logger.info("Sync is ready and fully initialized.");
    }
    
    /**
     * Cleans up resources when the servlet is destroyed.
     * Primarily shuts down the OutputManager's thread pool.
     */
    @Override
    public void destroy() {
        if (this.outputManager != null) {
            this.outputManager.shutdown();
            logger.info("OutputManager shutdown.");
        }
        super.destroy();
    }
    
    /**
     * Handles HTTP GET requests.
     * Provides a basic {@code /status} check.
     *
     * @param request The servlet request.
     * @param response The servlet response.
     * @throws ServletException If a servlet-specific error occurs.
     * @throws IOException If an I/O error occurs.
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");

        String pathInfo = request.getPathInfo();
        
        System.out.println(request.getPathInfo());
        if(pathInfo.equals("/status")) {
        	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            // Format the current date and time
            String formattedStartupTime = startupTime.format(formatter);
            response.getWriter().println("C03a, version "+version+", is running since "+formattedStartupTime);
            response.getWriter().close();

        } else {
            response.sendError(400, "Unknown request.");

        }
    }
    
    /**
     * Handles HTTP POST requests.
     * <p>
     * This is the main entry point for all anchor communication. It parses the JSON
     * request body and routes to the appropriate handler (boot, measure, scan)
     * based on the request path.
     *
     * @param request The servlet request.
     * @param response The servlet response.
     * @throws ServletException If a servlet-specific error occurs.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String pathInfo = request.getPathInfo();
        
        logger.info("logger.warning POST request on path: " + pathInfo);
        logger.info(pathInfo);
        if (pathInfo == null || pathInfo.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing path information.");
            return;
        }

        // Read request body
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
            logger.info(jsonObj.toString());
            // Route based on path
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
            
            // Send response
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

    /**
     * Handles the anchor registration request ({@code /anchorRegistration}).
     * Registers a new anchor in the system, saves it to the database,
     * and returns the first action command.
     *
     * @param jsonObj The parsed JSON request body.
     * @return A JSON string representing the next action for the anchor.
     * @throws JSONException If the JSON is missing required fields.
     */
    private String handleBootRequest(JSONObject jsonObj) throws JSONException {
        if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
            logger.warning("Boot request missing or invalid 'anchorID'.");
            return "{\"error\":\"Missing or invalid 'anchorID' in boot request.\"}";
        }

        String id = jsonObj.getString("anchorID");	
        Anchor anchor = new Anchor(id, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        // Save to DB and get the auto-generated ID
        anchor.setDeviceID(this.dbLogger.saveAnchor(anchor));
        this.synchronizer.addNewAnchor(anchor);
        
        logger.info("Registered new anchor: " + id + " with database ID: " + anchor.getDeviceID());
        return this.getResponse(anchor);
    }

    /**
     * Handles measurement reports ({@code /measurementReport}).
     * <p>
     * Parses distance readings from an anchor for multiple tags. It adds these
     * readings to the current active measurement round. If a round is
     * determined to be complete (i.e., one tag has readings from all anchors),
     * it dispatches the data for output.
     *
     * @param jsonObj The parsed JSON request body.
     * @return A JSON string representing the next action for the anchor.
     * @throws JSONException If the JSON is missing required fields.
     */
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
        
        // Add readings to the last (current) measurement for each tag
        for (int i = 0; i < tagArray.length(); i++) {
            JSONObject obj = tagArray.getJSONObject(i);
            
            if (obj.has("tagID") && obj.get("tagID") instanceof String &&
                obj.has("distance") && obj.get("distance") instanceof Number &&
                obj.has("executedAt") && obj.get("executedAt") instanceof Number) {

                String tagID = obj.getString("tagID");
                Number distance = obj.getNumber("distance");
                Number executedAt = obj.getNumber("executedAt");
                
                Tag tag = this.synchronizer.listOfTags.get(tagID);
                logger.info(tag.toString());
                if (tag != null && tag.getMeasurements() != null && !tag.getMeasurements().isEmpty()) {
                    Measurement lastMeasurement = tag.getMeasurements().getLast();
                    // Check if the reading is for the current, valid time window
                    if(lastMeasurement.checkIfValid(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())) {
                        Reading reading = new Reading(anchor, distance.longValue(), executedAt.longValue(), 5); // Channel 5 is hardcoded
                        lastMeasurement.getReadings().add(reading);
                    } else {
                         logger.warning("Dropped reading for tag " + tagID + " from anchor " + anchorID + ": Measurement is stale/invalid.");
                    }
                }
            }
        }
        
        List<Tag> tagList = new ArrayList<>(this.synchronizer.listOfTags.values());
        List<Anchor> anchorList = new ArrayList<>(this.synchronizer.listOfAnchors.values());
        
        // Check if any tag has completed its measurement round
        boolean measurementIsComplete = tagList.stream().anyMatch(tag -> 
            tag != null && 
            !tag.getMeasurements().isEmpty() && 
            tag.getMeasurements().getLast().getReadings().size() == anchorList.size()
        );

        // If complete, send data for output and start a new measurement round
        if (measurementIsComplete) {
            logger.info("MEASUREMENT ROUND COMPLETE. At least one tag received reports from all " + anchorList.size() + " anchors. Submitting data and resetting state.");
            
            startOutputProcess(tagList); 
            
            // Create the next measurement round for all tags
            this.synchronizer.addMeasurementRound(this.actionManager.getActionStartingTime(), this.actionManager.getChannelBusyUntil());
        }
        
        return this.getResponse(anchor);
    }

    /**
     * Handles scan reports ({@code /scanReport}).
     * <p>
     * Processes a list of tags discovered by an anchor. New tags are
     * registered in the system and saved to the database.
     *
     * @param jsonObj The parsed JSON request body.
     * @return A JSON string representing the next action for the anchor.
     * @throws JSONException If the JSON is missing required fields.
     */
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

        // Register any new tags
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
    
    /**
     * Collects all completed, non-submitted measurements and dispatches them
     * to the {@link OutputManager} for asynchronous processing.
     * <p>
     * It clones the tag and measurement data to prevent race conditions and
     * flags the original measurement as {@code sentForOutput} to avoid duplicates.
     *
     * @param tagList The list of all known tags to check for completed measurements.
     */
    private void startOutputProcess(List<Tag> tagList) {
        if (tagList == null || tagList.isEmpty()) {
            return;
        }

        List<Tag> tagsToSubmit = new ArrayList<>();
        List<Measurement> measurementsToFlag = new ArrayList<>(); 

        for (Tag tag : tagList) {
            if (tag != null && !tag.getMeasurements().isEmpty()) {
                Measurement measurement = tag.getMeasurements().getLast();
                
                // Check if this measurement has readings and hasn't been sent
                if (!measurement.getReadings().isEmpty() && !measurement.getSentForOutput()) { 
                    
                    // Clone the Tag and its last measurement for thread-safe processing
                    Tag tagClone = new Tag(tag.getDeviceName(), tag.getinitializedAt(), tag.getLastSeen());
                    tagClone.setDeviceID(tag.getDeviceID());
                    tagClone.getMeasurements().add(measurement); // Add *only* the completed measurement
                    
                    tagsToSubmit.add(tagClone);
                    measurementsToFlag.add(measurement); // Mark this one for flagging
                } else if (tag != null && !tag.getMeasurements().isEmpty()) {
                    // Log why a tag was skipped
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

        // Flag originals as "sent" to prevent re-submission
        for (Measurement measurement : measurementsToFlag) {
            measurement.setSentForOutput(true);
        }

        // Submit the cloned, thread-safe list to the output manager
        this.outputManager.submitTagBatch(tagsToSubmit);
        logger.info("Submitted " + tagsToSubmit.size() + " tag batches to OutputManager for processing.");
    }
    
    /**
     * Determines the next action for an anchor and generates the appropriate JSON response.
     * <p>
     * It coordinates with the {@link ActionManager} and {@link Synchronizer} to
     * decide between slow scan, fast scan, or a measurement round. It also
     * handles the creation and timeout of measurement rounds.
     *
     * @param anchor The anchor requesting the next action.
     * @return A JSON string representing the next action command.
     */
    private String getResponse(Anchor anchor) {
        Action action = this.actionManager.nextAction();
        String response = null;

        List<Tag> tagList = new ArrayList<>(this.synchronizer.listOfTags.values());

        if (this.synchronizer.listOfTags.isEmpty()){
            // If no tags are known, force a slow scan
            response = this.synchronizer.getSlowScanResponse(this.actionManager.getSlowScanTime());
        } else if (action == Action.FAST_SCAN) {
            // If it's time for a fast scan, do it
            response = this.synchronizer.getFastScanResponse(this.actionManager.getFastScanTime());
        } else {
            // Otherwise, perform a measurement
            response = this.synchronizer.getMeasurmentResponse(anchor, this.actionManager.getMeasurmentTime(this.synchronizer.listOfAnchors.size(), this.synchronizer.listOfTags.size()), this.actionManager.getScanTime());
            
            long now = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            // Check if the current measurement round is stale (timed out)
            boolean isStale = tagList.stream()
                .anyMatch(tag -> 
                    tag != null && 
                    !tag.getMeasurements().isEmpty() && 
                    !tag.getMeasurements().getLast().checkIfValid(now)
                );
            
            // Check if any tag has no measurements at all (needs initial round)
            boolean needsInitialStart = tagList.stream()
                .anyMatch(tag -> tag != null && tag.getMeasurements().isEmpty());

            // If it's the first round or a round has timed out, start a new one
            if (needsInitialStart || isStale) {
                
                if (isStale) {
                    logger.warning("MEASUREMENT ROUND TIMEOUT/STALE. Submitting incomplete data and resetting state.");
                    // Submit whatever partial data was collected
                    startOutputProcess(new ArrayList<>(this.synchronizer.listOfTags.values()));
                } else {
                    logger.info("MEASUREMENT ROUND START. Initializing new measurement for tags.");
                }
                
                // Create the new measurement round for all tags
                this.synchronizer.addMeasurementRound(this.actionManager.getActionStartingTime(), this.actionManager.getChannelBusyUntil());
            }
        }
        
        return (response != null) ? response : "{\"error\":\"Internal server error: Null response generated.\"}";
    }

    /**
     * Utility method to send a standardized JSON error response to the client.
     *
     * @param response The servlet response object.
     * @param statusCode The HTTP status code (e.g., 400, 404, 500).
     * @param message The error message to include in the JSON.
     * @throws IOException If an I/O error occurs writing the response.
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        logger.warning("Sending error response. Status: " + statusCode + ", Message: " + message);
        response.setStatus(statusCode);
        try (PrintWriter writer = response.getWriter()) {
            writer.write("{\"error\": \"" + message + "\"}");
            writer.flush();
        }
    }
}