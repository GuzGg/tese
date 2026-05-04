package pt.um.ucl.positioning.C03a.uwb.communications;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Paths;
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

public class C03a extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(C03a.class.getName());

	private static final String PATH_BOOT = "/anchorRegistration";
	private static final String PATH_MEASURE = "/measurementReport";
	private static final String PATH_SCAN = "/scanReport";

	private ActionManager actionManager;
	private Synchronizer synchronizer = new Synchronizer();
	private MeasurementsDatabaseLogger dbLogger;
	private OutputThread outputManager;
	private Config config;
	private String version = "0.7-Reactive";
	private LocalDateTime startupTime;
	private HikariDataSource datasource;
	private boolean isOperational = true;

	public C03a() {
		super();
	}

	@Override
	public void init(ServletConfig servletConfig) throws ServletException {
	    super.init(servletConfig);
	    this.startupTime = LocalDateTime.now();

	    // 1. Load config.properties
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

	    // 2. Clear old execution logs on startup
	    if (this.config.isEnableExecutionComparison()) {
	        try {
	            File logDir = new File(this.config.getLogDirectory());
	            if (!logDir.exists()) logDir.mkdirs();
	            
	            File schedLog = new File(logDir, "server_scheduled.txt");
	            File execLog = new File(logDir, "anchor_executed.txt");
	            
	            if (schedLog.exists()) schedLog.delete();
	            if (execLog.exists()) execLog.delete();
	            
	            if (config.isEnableGeneralLogs()) logger.info("Execution logs cleared for fresh startup.");
	        } catch (Exception e) {
	            logger.warning("Could not clear previous execution logs: " + e.getMessage());
	        }
	    }

	    if (config.isEnableGeneralLogs()) logger.info("Configuration loaded. Initializing Managers...");

	    // 3. Initialize the Action Manager
	    this.actionManager = new ActionManager(
	        this.config.getAmSlowScanPeriod(), 
	        this.config.getAmFastScanPeriod(),
	        this.config.getAmScanInterval(), 
	        this.config.getAmScanTime(), 
	        this.config.getAmMinRoundTime(),
	        this.config.getAmSafetyBuffer() 
	    );

	    // 4. Initialize Database Connection Pool
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
	        this.outputManager = new OutputThread(this, this.dbLogger, this.config);
	    } catch (Exception e) {
	        if (config.isEnableGeneralLogs()) logger.log(Level.SEVERE, "Failed to initialize components", e);
	        throw new ServletException(e);
	    }
	    
	    // 5. Load and Parse the Whitelist
	    if(config.isWhitelistEnabled()) {
	        try (InputStream is = servletConfig.getServletContext().getResourceAsStream("/WEB-INF/whitelist.json")) {
	            if (is != null) {
	                String jsonText = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
	                JSONObject rootNode = new JSONObject(jsonText);

	                if (rootNode.has("Tags")) {
	                    JSONArray tagsNode = rootNode.getJSONArray("Tags");
	                    for (int i = 0; i < tagsNode.length(); i++) {
	                        JSONObject tagObj = tagsNode.getJSONObject(i);
	                        this.synchronizer.whitelistOfTags.add(tagObj.getString("deviceId"));
	                    }
	                }

	                if (rootNode.has("Anchors")) {
	                    JSONArray anchorsNode = rootNode.getJSONArray("Anchors");
	                    for (int i = 0; i < anchorsNode.length(); i++) {
	                        JSONObject anchorObj = anchorsNode.getJSONObject(i);
	                        this.synchronizer.whitelistOfAnchors.add(anchorObj.getString("deviceId"));
	                    }
	                }
	                
	                if (config.isEnableGeneralLogs()) logger.info("Whitelist loaded successfully.");
	            } else {
	                if (config.isEnableGeneralLogs()) logger.warning("whitelist.json not found in /WEB-INF/! Security lists remain empty.");
	            }
	        } catch (Exception e) {
                 if (config.isEnableGeneralLogs()) logger.severe("Error reading whitelist.json: " + e.getMessage());
                 this.synchronizer.whitelistOfTags.clear();
                 this.synchronizer.whitelistOfAnchors.clear();
	        }
	    }
	    
	    if (config.isEnableGeneralLogs()) logger.info("C30a Servlet " + version + " is ready.");
	}

	@Override
	public void destroy() {
		if (this.outputManager != null) {
			this.outputManager.shutdown();
		}
		if (this.datasource != null) {
            this.datasource.close(); 
        }
		super.destroy();
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		String pathInfo = request.getPathInfo();

		if (pathInfo.equals("/status")) {
		    PrintWriter writer = response.getWriter();
		    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		    
		    writer.println("C03a Middleware Status");
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
		        writer.printf("ID: %-10s | Last Seen: %s\n",
		            t.getDeviceName(), 
		            Instant.ofEpochMilli(t.getLastSeen()).atZone(ZoneId.systemDefault()).toLocalDateTime().format(formatter));
		    }
		    writer.println();

		    if (this.datasource != null) {
		        var pool = this.datasource.getHikariPoolMXBean();
		        writer.println("--- Database Pool Status ---");
		        writer.println("Active Connections: " + pool.getActiveConnections());
		    }
		    writer.close();
		} else {
			response.sendError(400, "Unknown request.");
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (!isOperational) {
	        sendErrorResponse(response, 503, "Service Unavailable: Critical Database Failure.");
	        return;
	    }
		
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		String pathInfo = request.getPathInfo();
		
		if (pathInfo == null || pathInfo.isEmpty()) {
			sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing path information.");
			return;
		}

		String jsonString;
		try (BufferedReader reader = request.getReader()) {
			jsonString = reader.lines().collect(Collectors.joining());
		} catch (IOException e) {
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
				sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Null response generated.");
			}
		} catch (JSONException e) {
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

		if (config.isWhitelistEnabled() && !this.synchronizer.whitelistOfAnchors.contains(id)) {
            return "{\"error\":\"Unauthorized anchor ID.\"}";
        }

		Anchor anchor = new Anchor(id, System.currentTimeMillis(), System.currentTimeMillis());
		int existingId = this.dbLogger.getAnchorIdByCode(id);

		if (existingId != -1) {
			anchor.setDeviceID(existingId);
		} else {
			int newId = this.dbLogger.saveAnchor(anchor);
			anchor.setDeviceID(newId);
		}

		this.synchronizer.addNewAnchor(anchor);
		return this.getResponse(anchor);
	}

	private void logAnchorExecution(String anchorId, String tagId, long executedAt) {
	    if (!config.isEnableExecutionComparison()) return;
	    
	    File logDir = new File(config.getLogDirectory());
        if (!logDir.exists()) logDir.mkdirs(); 
	    
	    String cleanTagNumber = tagId.replace("tag", "");
	    String logMessage = "EXEC," + anchorId + "," + cleanTagNumber + "," + executedAt;
	    String fullPath = Paths.get(config.getLogDirectory(), "anchor_executed.txt").toString();
	    
	    try (FileWriter fw = new FileWriter(fullPath, true);
	         PrintWriter pw = new PrintWriter(fw)) {
	        pw.println(logMessage);
	    } catch (IOException e) {
	        System.err.println("Could not write to anchor_executed.txt: " + e.getMessage());
	    }
	}

	private String handleMeasureRequest(JSONObject jsonObj) throws JSONException {
	    String anchorID = jsonObj.getString("anchorID");
	    
	    if (config.isWhitelistEnabled() && !this.synchronizer.whitelistOfAnchors.contains(anchorID)) {
             return "{\"error\":\"Unauthorized anchor ID.\"}";
        }
	    
	    Anchor anchor = this.synchronizer.listOfAnchors.get(anchorID);
	    if (anchor == null) return this.synchronizer.getRegisterResponse();
	    anchor.setLastSeen(System.currentTimeMillis());

	    JSONArray tagArray = jsonObj.getJSONArray("tags");
	    for (int i = 0; i < tagArray.length(); i++) {
	        JSONObject obj = tagArray.getJSONObject(i);
	        String tagID = obj.getString("tagID");
	        
	        if (config.isWhitelistEnabled() && !this.synchronizer.whitelistOfTags.contains(tagID)) {
                continue;
            }
	        
	        long executedAt = obj.getLong("executedAt");
	        
	        logAnchorExecution(anchorID, tagID, executedAt);
	        
	        Tag tag = this.synchronizer.listOfTags.get(tagID);

	        if (tag != null) {
	            tag.setLastSeen(System.currentTimeMillis());
	            
	            Measurement targetRound = tag.getMeasurements().stream()
	                .filter(m -> !m.getSentForOutput())
	                .filter(m -> !m.getAnchors().contains(anchor)) 
	                .filter(m -> m.checkIfValid(executedAt))
	                .findFirst()
	                .orElse(null);

	            if (targetRound != null) {
	                targetRound.getReadings().add(new Reading(anchor, obj.getDouble("distance"), executedAt, 5));
	            }
	        }
	    }
	    startOutputProcess(new ArrayList<>(this.synchronizer.listOfTags.values()));
	    return this.getResponse(anchor);
	}

	private String handleScanRequest(JSONObject jsonObj) throws JSONException {
		if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
			return "{\"error\":\"Missing or invalid 'anchorID' in scan request.\"}";
		}
		String anchorID = jsonObj.getString("anchorID");
		
		if (config.isWhitelistEnabled() && !this.synchronizer.whitelistOfAnchors.contains(anchorID)) {
             return "{\"error\":\"Unauthorized anchor ID.\"}";
        }
		
		Anchor anchor = this.synchronizer.listOfAnchors.get(anchorID);
		if (anchor != null) {
		    anchor.setLastSeen(System.currentTimeMillis());
		} else {
			return this.synchronizer.getRegisterResponse();
		}
		
		if (!jsonObj.has("tags") || !(jsonObj.get("tags") instanceof JSONArray)) {
			return "{\"error\":\"Missing or invalid 'tags' array in scan request.\"}";
		}
		JSONArray tagArray = jsonObj.getJSONArray("tags");

		for (int i = 0; i < tagArray.length(); i++) {
			JSONObject obj = tagArray.getJSONObject(i);
			if (obj.has("tagID") && obj.get("tagID") instanceof String) {
				String tagID = obj.getString("tagID");
				
				if (config.isWhitelistEnabled() && !this.synchronizer.whitelistOfTags.contains(tagID)) {
                    continue; 
                }

				Tag tag = new Tag(tagID, System.currentTimeMillis(), System.currentTimeMillis());

				if (!this.synchronizer.tagExists(tag)) {
					int existingId = this.dbLogger.getTargetIdByCode(tagID);

					if (existingId != -1) {
						tag.setDeviceID(existingId);
					} else {
						tag.setDeviceID(this.dbLogger.saveTarget(tag));
					}

					tag.setLastSeen(System.currentTimeMillis());
					this.synchronizer.addNewTag(tag);
					
				} else {
					Tag tagToUpdate = this.synchronizer.listOfTags.get(tagID);
					if(tagToUpdate != null) {
						tagToUpdate.setLastSeen(System.currentTimeMillis());
					}
				}
			}
		}
		return this.getResponse(anchor);
	}

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
	                boolean isStale = now > m.getMeasurmentEndTime() + 10000; 

					if (!m.getSentForOutput() && !m.getReadings().isEmpty() && (isComplete || isStale)) {
	                    m.setSentForOutput(true);
	                    Tag tagClone = new Tag(tag.getDeviceName(), tag.getinitializedAt(), tag.getLastSeen());
	                    tagClone.setDeviceID(tag.getDeviceID());
	                    tagClone.getMeasurements().add(m); 
	                    tagsToSubmit.add(tagClone);
	                }

	                if (m.getSentForOutput() || isStale) {
	                    iterator.remove();
	                }
	            }
	        }
	    }

	    if (!tagsToSubmit.isEmpty()) {
	        this.outputManager.submitTagBatch(tagsToSubmit);
	    }
	}

	private String getResponse(Anchor anchor) {
	    Action action = this.actionManager.nextAction();
	    String response = null;
	    
	    long evictionThreshold = System.currentTimeMillis() - (6 * config.getAmFastScanPeriod());
	    this.synchronizer.listOfTags.values().removeIf(t -> t.getLastSeen() < evictionThreshold);
	    this.synchronizer.listOfAnchors.values().removeIf(t -> t.getLastSeen() < evictionThreshold);
	    
	    List<Tag> tagList = new ArrayList<>(this.synchronizer.listOfTags.values());
	
	    if (this.synchronizer.listOfTags.isEmpty() || this.synchronizer.listOfAnchors.isEmpty()) {
	        response = this.synchronizer.getSlowScanResponse(this.actionManager.getSlowScanTime());
	    } else if (action == Action.FAST_SCAN) {
	        response = this.synchronizer.getFastScanResponse(this.actionManager.getFastScanTime());
	    } else {
	        response = this.synchronizer.getMeasurmentResponse(anchor, this.actionManager.getScanTime(), this.config.getAmSafetyBuffer(), this.config);
	
	        long now = System.currentTimeMillis();
	        boolean isStale = tagList.stream().anyMatch(tag -> tag.getMeasurements().stream()
	            .anyMatch(m -> now > m.getMeasurmentEndTime() + 10000)); 
	
	        if (tagList.stream().anyMatch(t -> t.getMeasurements().isEmpty()) || isStale) {
	            if (isStale) startOutputProcess(tagList);
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
	
	public synchronized void signalFatalError(String reason) {
	    if (!isOperational) return;
	    this.isOperational = false;
	    logger.severe("FATAL DATABASE ERROR: " + reason + ". Terminating service.");
	    if (this.outputManager != null) this.outputManager.shutdown();
	    if (this.datasource != null) this.datasource.close();
	}
}