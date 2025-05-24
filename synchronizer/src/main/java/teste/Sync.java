package teste;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Servlet implementation class Synchronizer
 */
@WebServlet("/Synchronizer")
public class Sync extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Sync() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		doGet(request, response);
		String pathInfo = request.getPathInfo();
		response.setContentType("application/json");
		PrintWriter writer = response.getWriter();
		String jsonString = request.getParameter("jsondata");
		JSONObject jsonObj = new JSONObject(jsonString);
		
		if(pathInfo.equals("/boot")){
			// handle boot resequst
			String id = jsonObj.getString("anchorId");
		} else if (pathInfo.equals("/measure")) {
			// handle measure request
			String id = jsonObj.getString("anchorId");
			JSONArray tagArray = jsonObj.getJSONArray("tags");
			
			tagArray.forEach(element -> {
				JSONObject obj = new JSONObject(element);
				String tagID = obj.getString("tagID");
				Number data = obj.getNumber("data");
				Number executedAt = obj.getNumber("executedAt");
			});
			
	    } else if (pathInfo.equals("/scan")) {
	    	// handle scan request
			String anchorId = jsonObj.getString("anchorID");
			JSONArray tagArray = jsonObj.getJSONArray("tags");
			
			tagArray.forEach(element -> {
				JSONObject obj = new JSONObject(element);
				String tagID = obj.getString("tagID");
			});
	    } else {
			writer.flush();
			writer.close();
	    }
		writer.flush();
		writer.close();
	}

}
