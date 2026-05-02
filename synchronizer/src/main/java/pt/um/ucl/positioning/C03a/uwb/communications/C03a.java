package pt.um.ucl.positioning.C03a.uwb.communications;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

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
 * Main HttpServlet that acts as the central API endpoint for UWB
 * (Ultra-Wideband) anchors.
 * <p>
 * This servlet manages the UWB system's lifecycle by handling anchor
 * registration, processing scan reports for tag discovery, and collecting
 * measurement reports for positioning. It coordinates with
 * {@link ActionManager} to schedule tasks, {@link Synchronizer} to maintain
 * system state, and {@link MeasurementsDatabaseLogger} to persist data.
 * <p>
 * Completed measurements are dispatched to an {@link OutputThread} for
 * asynchronous database logging and forwarding to an external Position
 * Estimator service.
 * <p>
 * The servlet exposes three main POST endpoints:
 * <ul>
 * <li>{@code /anchorRegistration}: For new anchors to join the system.</li>
 * <li>{@code /scanReport}: For anchors to report discovered tags.</li>
 * <li>{@code /measurementReport}: For anchors to submit distance measurements
 * to tags.</li>
 * </ul>
 * 
 * @author Gustavo Oliveira
 * @version 0.6
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
	private String version = "0.6";
	/** Initialization time */
	private LocalDateTime startupTime;
	
	private HikariDataSource datasource;
	
	private boolean isOperational = true;

	/**
	 * Default constructor.
	 */
	public C03a() {
		super();
	}

	/**
	 * Initializes the servlet.
	 * <p>
	 * Loads configuration from {@code config.properties}, sets up the database
	 * connection (DataSource), and initializes all manager classes (ActionManager,
	 * MeasurementsDatabaseLogger, OutputThread).
	 *
	 * @param servletConfig The servlet configuration object.
	 * @throws ServletException if initialization fails (e.g., config not found, DB
	 *                          driver missing).
	 */
	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
	    super.init(servletConfig);
	    this.startupTime = LocalDateTime.now();

	    Properties props = new Properties();
	    try (InputStream input = servletConfig.getServletContext().getResourceAsStream("/WEB-INF/config.properties")) {
	        if (input == null) {
	            logger.severe("CRITICAL: config.properties file not found in /WEB-INF/.");
	            throw new ServletException("config.properties file not found.");
	        }
	        props.load(input);
	        this.config = new Config(props);
	    } catch (IOException ex) {
	        logger.log(Level.SEVERE, "Error reading config.properties", ex);
	        throw new ServletException("Error loading config.properties", ex);
	    }

	    if (config.isEnableGeneralLogs()) logger.info("Configuration loaded. Initializing ActionManager and HikariCP...");

	    this.actionManager = new ActionManager(
	        this.config.getAmSlowScanPeriod(), this.config.getAmFastScanPeriod(),
	        this.config.getAmScanInterval(), this.config.getAmScanTime(), this.config.getAmMinRoundTime()
	    );

	    HikariConfig hikariConfig = new HikariConfig();
	    hikariConfig.setJdbcUrl(this.config.getDbUrl() + "/" + this.config.getDbName());
	    hikariConfig.setUsername(this.config.getDbUsername());
	    hikariConfig.setPassword(this.config.getDbPassword());
	    hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");

	    hikariConfig.setMaximumPoolSize(10); 
	    hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
	    hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
	    
	    this.datasource = new HikariDataSource(hikariConfig);

	    try {
	        this.dbLogger = new MeasurementsDatabaseLogger(this.datasource, this.config); 
	        this.outputManager = new OutputThread(
	        		this,
	            this.dbLogger, 
	            this.config
	        );
	    } catch (Exception e) {
	        if (config.isEnableGeneralLogs()) logger.log(Level.SEVERE, "Failed to initialize sub-components", e);
	        throw new ServletException(e);
	    }
	    
	    if(config.isWhitelistEnabled()) {
	    	ObjectMapper mapper = new ObjectMapper();
	        

	        try {
	            JsonNode rootNode = mapper.readTree(new File("/WEB-INF/whitelist.json"));

	            JsonNode tagsNode = rootNode.get("Tags");
	            if (tagsNode != null && tagsNode.isArray()) {
	                for (JsonNode node : tagsNode) {
	                	this.synchronizer.whitelistOfTags.add(node.get("deviceId").asText());
	                }
	            }

	            JsonNode anchorsNode = rootNode.get("Anchors");
	            if (anchorsNode != null && anchorsNode.isArray()) {
	                for (JsonNode node : anchorsNode) {
	                	this.synchronizer.whitelistOfAnchors.add(node.get("deviceId").asText());
	                }
	            }

	            // Output the results to verify
	            if (config.isEnableGeneralLogs()) logger.info("Tags Set: " + this.synchronizer.whitelistOfTags);
	            if (config.isEnableGeneralLogs()) logger.info("Anchors Set: " + this.synchronizer.whitelistOfAnchors);

	        } catch (Exception e) {
	        	 if (config.isEnableGeneralLogs()) logger.info("Error reading or parsing the JSON file: " + e.getMessage());
	        }
	    }
	    
	    if (config.isEnableGeneralLogs()) logger.info("C30a Servlet "+version+" is ready.");
	}

	/**
	 * Cleans up resources when the servlet is destroyed. Primarily shuts down the
	 * OutputManager's thread pool.
	 */
	@Override
	public void destroy() {
		if (this.outputManager != null) {
			this.outputManager.shutdown();
            if (config.isEnableGeneralLogs()) logger.info("OutputManager shutdown.");
		}
		if (this.datasource != null) {
            this.datasource.close(); // Graceful shutdown
            if (config.isEnableGeneralLogs()) logger.info("HikariCP pool closed.");
        }
		super.destroy();
	}

	/**
	 * Handles HTTP GET requests. Provides a basic {@code /status} check.
	 *
	 * @param request  The servlet request.
	 * @param response The servlet response.
	 * @throws ServletException If a servlet-specific error occurs.
	 * @throws IOException      If an I/O error occurs.
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		response.setContentType("application/json");

		String pathInfo = request.getPathInfo();

		if (config.isEnableInputLogs()) System.out.println(request.getPathInfo());
		if (pathInfo.equals("/status")) {
		    PrintWriter writer = response.getWriter();
		    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		    
		    writer.println("C03a Middleware Status");
		    writer.println("----------------------");
		    writer.println("Running since: " + startupTime.format(formatter));
		    writer.println();

		    List<Anchor> anchors = this.synchronizer.getAnchorList();
		    writer.println("--- Active Anchors (" + anchors.size() + ") ---");
		    for (Anchor a : anchors) {
		        writer.printf("ID: %-10s | Last Seen: %s\n", 
		            a.getDeviceName(), 
		            Instant.ofEpochMilli(a.getLastSeen()).atZone(ZoneId.systemDefault()).toLocalDateTime().format(formatter));
		    }
		    writer.println();

		    List<Tag> tags = this.synchronizer.getTagList();
		    writer.println("--- Active Tags (" + tags.size() + ") ---");
		    for (Tag t : tags) {
		        writer.printf("ID: %-10s | Last Seen: %s\n", // Changed from 'Initialized'
		            t.getDeviceName(), 
		            Instant.ofEpochMilli(t.getLastSeen()).atZone(ZoneId.systemDefault()).toLocalDateTime().format(formatter));
		    }
		    writer.println();

		    if (this.datasource != null) {
		        var pool = this.datasource.getHikariPoolMXBean();
		        writer.println("--- Database Pool Status ---");
		        writer.println("Active Connections: " + pool.getActiveConnections());
		        writer.println("Threads Awaiting:   " + pool.getThreadsAwaitingConnection());
		    }
	        writer.println("--- Current State ---");
		    writer.println("Action: " + actionManager.getCurrentAction());
		    writer.close();
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
	 * @param request  The servlet request.
	 * @param response The servlet response.
	 * @throws ServletException If a servlet-specific error occurs.
	 * @throws IOException      If an I/O error occurs.
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		
		if (!isOperational) {
	        sendErrorResponse(response, 503, "Service Unavailable: Critical Database Failure.");
	        return;
	    }
		
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		String pathInfo = request.getPathInfo();
		


        if (config.isEnableInputLogs()) logger.info("logger.warning POST request on path: " + pathInfo);
        if (config.isEnableInputLogs()) logger.info(pathInfo);
		if (pathInfo == null || pathInfo.isEmpty()) {
			sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing path information.");
			return;
		}

		String jsonString;
		try (BufferedReader reader = request.getReader()) {
			jsonString = reader.lines().collect(Collectors.joining());
		} catch (IOException e) {
            if (config.isEnableInputLogs()) logger.warning("Error reading request body: " + e.getMessage());
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
            if (config.isEnableInputLogs()) logger.info(jsonObj.toString());
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

			if (responseString != null) {
				response.setStatus(HttpServletResponse.SC_OK);
				try (PrintWriter writer = response.getWriter()) {
					writer.write(responseString);
					writer.flush();
				}
			} else {
				sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
						"Internal server error: Null response generated.");
			}
		} catch (JSONException e) {
            if (config.isEnableInputLogs()) logger.warning("Invalid JSON received on path " + pathInfo + ": " + e.getMessage());
			sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format: " + e.getMessage());
		} catch (Exception e) {
            if (config.isEnableInputLogs()) logger.log(Level.SEVERE, "An internal server error occurred during POST for " + pathInfo, e);
			sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
					"An internal server error occurred: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Handles the anchor registration request ({@code /anchorRegistration}). Checks
	 * if the anchor exists in the DB first; if not, it creates it. and returns the
	 * first action command.
	 *
	 * @param jsonObj The parsed JSON request body.
	 * @return A JSON string representing the next action for the anchor.
	 * @throws JSONException If the JSON is missing required fields.
	 */
	private String handleBootRequest(JSONObject jsonObj) throws JSONException {
		if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
            if (config.isEnableInputLogs()) logger.warning("Boot request missing or invalid 'anchorID'.");
			return "{\"error\":\"Missing or invalid 'anchorID' in boot request.\"}";
		}

		String id = jsonObj.getString("anchorID");

		Anchor anchor = new Anchor(id, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
				LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

		int existingId = this.dbLogger.getAnchorIdByCode(id);

		if (existingId != -1) {
			anchor.setDeviceID(existingId);
            if (config.isEnableInputLogs()) logger.info("Anchor already exists: " + id + " (DB ID: " + existingId + ")");
		} else {
			int newId = this.dbLogger.saveAnchor(anchor);
			anchor.setDeviceID(newId);
            if (config.isEnableInputLogs()) logger.info("Registered NEW anchor: " + id + " with database ID: " + newId);
		}

		this.synchronizer.addNewAnchor(anchor);

		return this.getResponse(anchor);
	}

	/**
	 * Handles measurement reports (/measurementReport) with improved timing tolerance.
	 * <p>
	 * This refactored version processes each tag independently and uses a lenient 
	 * timing check to ensure readings are not discarded due to network or processing delays.
	 *
	 * @param jsonObj The parsed JSON request body.
	 * @return A JSON string representing the next action for the anchor.
	 * @throws JSONException If the JSON is missing required fields.
	 */
	private String handleMeasureRequest(JSONObject jsonObj) throws JSONException {
	    String anchorID = jsonObj.getString("anchorID");
	    Anchor anchor = this.synchronizer.listOfAnchors.get(anchorID);
	    if (anchor == null) return this.synchronizer.getRegisterResponse();
	    anchor.setLastSeen(System.currentTimeMillis());

	    JSONArray tagArray = jsonObj.getJSONArray("tags");
	    for (int i = 0; i < tagArray.length(); i++) {
	        JSONObject obj = tagArray.getJSONObject(i);
	        long executedAt = obj.getLong("executedAt");
	        Tag tag = this.synchronizer.listOfTags.get(obj.getString("tagID"));

	        if (tag != null) {
	            tag.setLastSeen(System.currentTimeMillis());
	            
	            // Find the OLDEST round that fits the time AND doesn't have this anchor yet
	            Measurement targetRound = tag.getMeasurements().stream()
	                .filter(m -> !m.getSentForOutput())
	                .filter(m -> !m.getAnchors().contains(anchor)) 
	                .filter(m -> m.checkIfValid(executedAt)) // Uses the 5s window
	                .findFirst()
	                .orElse(null);

	            // NO FALLBACK: This prevents "Cycle Cross-Contamination"
	            if (targetRound != null) {
	                targetRound.getReadings().add(new Reading(anchor, obj.getDouble("distance"), executedAt, 5));
	            }
	        }
	    }
	    startOutputProcess(new ArrayList<>(this.synchronizer.listOfTags.values()));
	    return this.getResponse(anchor);
	}
	/**
	 * Handles scan reports ({@code /scanReport}).
	 * <p>
	 * Processes a list of tags discovered by an anchor. New tags are registered in
	 * the system and saved to the database.
	 *
	 * @param jsonObj The parsed JSON request body.
	 * @return A JSON string representing the next action for the anchor.
	 * @throws JSONException If the JSON is missing required fields.
	 */
	private String handleScanRequest(JSONObject jsonObj) throws JSONException {
		if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
            if (config.isEnableInputLogs()) logger.warning("Scan request missing or invalid 'anchorID'.");
			return "{\"error\":\"Missing or invalid 'anchorID' in scan request.\"}";
		}
		String anchorID = jsonObj.getString("anchorID");
		Anchor anchor = this.synchronizer.listOfAnchors.get(anchorID);
		if (anchor != null) {
		    anchor.setLastSeen(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
		} else {
			return this.synchronizer.getRegisterResponse();
		}
		
		if (!jsonObj.has("tags") || !(jsonObj.get("tags") instanceof JSONArray)) {
            if (config.isEnableInputLogs()) logger.warning("Scan request missing or invalid 'tags' array.");
			return "{\"error\":\"Missing or invalid 'tags' array in scan request.\"}";
		}
		JSONArray tagArray = jsonObj.getJSONArray("tags");

		for (int i = 0; i < tagArray.length(); i++) {
			JSONObject obj = tagArray.getJSONObject(i);
			if (obj.has("tagID") && obj.get("tagID") instanceof String) {
				String tagID = obj.getString("tagID");

				Tag tag = new Tag(tagID, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
						LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

				if (!this.synchronizer.tagExists(tag)) {

					int existingId = this.dbLogger.getTargetIdByCode(tagID);

					if (existingId != -1) {
						tag.setDeviceID(existingId);
			            if (config.isEnableInputLogs()) logger.info("Tag found in DB, added to memory: " + tagID + " (DB ID: " + existingId + ")");
					} else {
						tag.setDeviceID(this.dbLogger.saveTarget(tag));
			            if (config.isEnableInputLogs()) logger.info("Discovered and registered NEW tag: " + tagID + " with database ID: "
								+ tag.getDeviceID());
					}

					this.synchronizer.addNewTag(tag);
				} else {
					Tag tagToUpdate = this.synchronizer.listOfTags.get(tagID);
					if(tagToUpdate != null) {
						tagToUpdate.setLastSeen(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
					}
				}
			}
		}
		return this.getResponse(anchor);
	}

	/**
	 * Collects all completed, non-submitted measurements and dispatches them to the
	 * {@link OutputManager} for asynchronous processing.
	 * <p>
	 * It clones the tag and measurement data to prevent race conditions and flags
	 * the original measurement as {@code sentForOutput} to avoid duplicates.
	 *
	 * @param tagList The list of all known tags to check for completed
	 *                measurements.
	 */
/**
 * Processes and dispatches measurement rounds that are either complete (all anchors reported)
 * or stale (timed out waiting for late anchors).
 *
 * @param tagList The list of all currently active tags to check.
 */
	private void startOutputProcess(List<Tag> tagList) {
	    if (tagList == null || tagList.isEmpty()) return;
	    List<Tag> tagsToSubmit = new ArrayList<>();
	    int anchorCount = this.synchronizer.listOfAnchors.size();
	    long now = System.currentTimeMillis();

	    for (Tag tag : tagList) {
	        if (tag != null && !tag.getMeasurements().isEmpty()) {
	            Iterator<Measurement> iterator = tag.getMeasurements().iterator();
	            
	            while (iterator.hasNext()) {
	                Measurement m = iterator.next();
	                boolean isComplete = m.getReadings().size() >= anchorCount;
	                // Wait 10s after cycle end for Wi-Fi lag
	                boolean isStale = now > m.getMeasurmentEndTime() + 10000; 

					if (!m.getSentForOutput() && !m.getReadings().isEmpty() && (isComplete || isStale)) {
	                    m.setSentForOutput(true);
	                    Tag tagClone = new Tag(tag.getDeviceName(), tag.getinitializedAt(), tag.getLastSeen());
	                    tagClone.setDeviceID(tag.getDeviceID());
	                    tagClone.getMeasurements().add(m); 
	                    tagsToSubmit.add(tagClone);
	                }

	                // Cleanup processed or dead rounds
	                if (m.getSentForOutput() || isStale) {
	                    iterator.remove();
	                }
	            }
	            // CRITICAL: Removed "needsNextRound" creation from here! 
	            // Creation is now handled ONLY in getResponse.
	        }
	    }

	    if (!tagsToSubmit.isEmpty()) {
	        this.outputManager.submitTagBatch(tagsToSubmit);
	        if (config.isEnableOutputLogs()) logger.info("Dispatched " + tagsToSubmit.size() + " round(s).");
	    }
	}
	/**
	 * Determines the next action for an anchor and generates the appropriate JSON
	 * response.
	 * <p>
	 * It coordinates with the {@link ActionManager} and {@link Synchronizer} to
	 * decide between slow scan, fast scan, or a measurement round. It also handles
	 * the creation and timeout of measurement rounds.
	 *
	 * @param anchor The anchor requesting the next action.
	 * @return A JSON string representing the next action command.
	 */
private String getResponse(Anchor anchor) {
    Action action = this.actionManager.nextAction();
    String response = null;
    
    // 1. EVICTION: Clean up truly dead devices to keep the schedule tight
    long evictionThreshold = System.currentTimeMillis() - (6 * config.getAmFastScanPeriod());
    this.synchronizer.listOfTags.values().removeIf(t -> t.getLastSeen() < evictionThreshold);
    this.synchronizer.listOfAnchors.values().removeIf(t -> t.getLastSeen() < evictionThreshold);
    
    List<Tag> tagList = new ArrayList<>(this.synchronizer.listOfTags.values());

    if (this.synchronizer.listOfTags.isEmpty() || this.synchronizer.listOfAnchors.isEmpty()) {
        response = this.synchronizer.getSlowScanResponse(this.actionManager.getSlowScanTime());
    } else if (action == Action.FAST_SCAN) {
        response = this.synchronizer.getFastScanResponse(this.actionManager.getFastScanTime());
    } else {
    	this.actionManager.forceTimeSync();
        // Calculate the FUTURE start time
        long nextExecutionTime = this.actionManager.getMeasurmentTime(
            this.synchronizer.listOfAnchors.size(), this.synchronizer.listOfTags.size());

        response = this.synchronizer.getMeasurmentResponse(anchor, nextExecutionTime, this.actionManager.getScanTime());

        long now = System.currentTimeMillis();
        // PATIENCE: Wait 10s before considering a round "dead"
        boolean isStale = tagList.stream().anyMatch(tag -> tag.getMeasurements().stream()
            .anyMatch(m -> now > m.getMeasurmentEndTime() + 10000)); 

        if (tagList.stream().anyMatch(t -> t.getMeasurements().isEmpty()) || isStale) {
            if (isStale) startOutputProcess(tagList);
            // ALIGNMENT: Create memory bucket at nextExecutionTime, NOT 'now'
            this.synchronizer.addMeasurementRound(nextExecutionTime, this.actionManager.getChannelBusyUntil());
        }
    }

    return (response != null) ? response : "{\"error\":\"Internal server error: Null response generated.\"}";
}

	/**
	 * Utility method to send a standardized JSON error response to the client.
	 *
	 * @param response   The servlet response object.
	 * @param statusCode The HTTP status code (e.g., 400, 404, 500).
	 * @param message    The error message to include in the JSON.
	 * @throws IOException If an I/O error occurs writing the response.
	 */
	private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        if (config.isEnableGeneralLogs()) logger.warning("Sending error response. Status: " + statusCode + ", Message: " + message);
		response.setStatus(statusCode);
		try (PrintWriter writer = response.getWriter()) {
			writer.write("{\"error\": \"" + message + "\"}");
			writer.flush();
		}
	}
	
	/** * Public method for the logger/tasks to report a fatal DB failure.
	 * @param reason Why the shutdown occurs
	 */
	public synchronized void signalFatalError(String reason) {
	    if (!isOperational) return;
	    
	    this.isOperational = false;
	    logger.severe("FATAL DATABASE ERROR: " + reason + ". Terminating service.");
	    
	    if (this.outputManager != null) this.outputManager.shutdown();
	    if (this.datasource != null) this.datasource.close();
	}
}