package communications;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.ZoneId;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException; // Import JSONException
import org.json.JSONObject;

import devices.Anchor;
import devices.Tag;
import teste.Synchronizer;
import util.ActionManager;
import util.ActionManager.Action;

/**
 * Servlet implementation class Synchronizer
 */
@WebServlet("/Synchronizer/*") // Changed to handle sub-paths
public class Sync extends HttpServlet {
	private static final long serialVersionUID = 1L;

    // Define constants for paths for better maintainability
    private static final String PATH_BOOT = "/boot";
    private static final String PATH_MEASURE = "/measure";
    private static final String PATH_SCAN = "/scan";
    
    private ActionManager actionManager = new ActionManager();
    private Synchronizer synchronizer = new Synchronizer();

    /**
     * @see HttpServlet#HttpServlet()
     */
    public Sync() {
        super();
    }

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json"); // Set content type once
		String pathInfo = request.getPathInfo();

		// Check if pathInfo is null
		if (pathInfo == null || pathInfo.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing path information (e.g., /boot, /measure, /scan)");
            return;
        }

		String jsonString = request.getParameter("jsondata");

        if (jsonString == null || jsonString.isEmpty()) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing 'jsondata' parameter.");
            return;
        }

		try (PrintWriter writer = response.getWriter()) {
			JSONObject jsonObj = new JSONObject(jsonString);

			if (PATH_BOOT.equals(pathInfo)) {
				handleBootRequest(jsonObj, writer, response);
			} else if (PATH_MEASURE.equals(pathInfo)) {
				handleMeasureRequest(jsonObj, writer, response);
			} else if (PATH_SCAN.equals(pathInfo)) {
				handleScanRequest(jsonObj, writer, response);
			} else {
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Unknown path: " + pathInfo);
			}
			
			writer.close();
		} catch (JSONException e) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON format: " + e.getMessage());
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An internal server error occurred: " + e.getMessage());
        }
	}

	/**
	 * handle a request of type boot
	 * @param jsonObj json object received by the request
	 * @param writer servelet writer
	 * @param response servelet response
	 * @throws JSONException
	 * @throws IOException
	 */
    private void handleBootRequest(JSONObject jsonObj, PrintWriter writer, HttpServletResponse response) throws JSONException, IOException {
        if (!jsonObj.has("anchorId") || !(jsonObj.get("anchorId") instanceof String)) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid 'anchorId' in boot request.");
            return;
        }
        String id = jsonObj.getString("anchorId");
        System.out.println("Boot request for anchorId: " + id); // For logging

        // Add Anchor to Synchronizer
        this.synchronizer.addNewAnchor(new Anchor(id, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        
        String responseString = this.getResponse();
        
        // Send response
        writer.write(responseString);
        response.setStatus(HttpServletResponse.SC_OK);
    }

	/**
	 * handle a request of type measure
	 * @param jsonObj json object received by the request
	 * @param writer servelet writer
	 * @param response servelet response
	 * @throws JSONException
	 * @throws IOException
	 */
    private void handleMeasureRequest(JSONObject jsonObj, PrintWriter writer, HttpServletResponse response) throws JSONException, IOException {
        if (!jsonObj.has("anchorId") || !(jsonObj.get("anchorId") instanceof String)) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid 'anchorId' in measure request.");
            return;
        }
        String id = jsonObj.getString("anchorId");

        if (!jsonObj.has("tags") || !(jsonObj.get("tags") instanceof JSONArray)) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid 'tags' array in measure request.");
            return;
        }
        JSONArray tagArray = jsonObj.getJSONArray("tags");

        tagArray.forEach(element -> {
            if (element instanceof JSONObject) {
                JSONObject obj = (JSONObject) element;
                // Validate inner elements
                if (obj.has("tagID") && obj.get("tagID") instanceof String &&
                    obj.has("data") && obj.get("data") instanceof Number &&
                    obj.has("executedAt") && obj.get("executedAt") instanceof Number) {

                    String tagID = obj.getString("tagID");
                    Number data = obj.getNumber("data");
                    Number executedAt = obj.getNumber("executedAt");
                    System.out.println("Measure: tagID=" + tagID + ", data=" + data + ", executedAt=" + executedAt);
                    // Implement handling of measured data
                } else {
                    System.err.println("Invalid tag object in measure request: " + obj.toString());
                }
            }
        });

        writer.write("{}");
        response.setStatus(HttpServletResponse.SC_OK);
    }

	/**
	 * handle a request of type scan
	 * @param jsonObj json object received by the request
	 * @param writer servelet writer
	 * @param response servelet response
	 * @throws JSONException
	 * @throws IOException
	 */
    private void handleScanRequest(JSONObject jsonObj, PrintWriter writer, HttpServletResponse response) throws JSONException, IOException {
        if (!jsonObj.has("anchorID") || !(jsonObj.get("anchorID") instanceof String)) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid 'anchorID' in scan request.");
            return;
        }
        String anchorId = jsonObj.getString("anchorID");

        if (!jsonObj.has("tags") || !(jsonObj.get("tags") instanceof JSONArray)) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Missing or invalid 'tags' array in scan request.");
            return;
        }
        JSONArray tagArray = jsonObj.getJSONArray("tags");

        tagArray.forEach(element -> {
            if (element instanceof JSONObject) {
                JSONObject obj = (JSONObject) element;
                if (obj.has("tagID") && obj.get("tagID") instanceof String) {
                    String tagID = obj.getString("tagID");
                    Tag tag = new Tag(tagID, LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                    if(!this.synchronizer.tagExists(tag)) {
                        this.synchronizer.addNewTag(tag);
                    }
                } else {
                    System.err.println("Invalid tag object in scan request: " + obj.toString());
                }
            }
        });

        writer.write("{}");
        response.setStatus(HttpServletResponse.SC_OK);
    }
    
    private String getResponse() {
    	Action action = this.actionManager.nextAction();
    	if(this.synchronizer.listOfTags.isEmpty()){
    		this.actionManager.updateChannelBusyUntilSlowScan();
    		return "slowScan";
    	} else if(action == Action.FAST_SCAN) {
    		this.actionManager.updateChannelBusyUntilFastScan();
    		return "fastScan";
    	} else {
    		this.actionManager.updateChanelBusyMeasurement(this.synchronizer.listOfAnchors.size(), this.synchronizer.listOfTags.size());
    		return "measure";
    	}
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        try (PrintWriter writer = response.getWriter()) {
        	 writer.write("{}");
        }
    }
}