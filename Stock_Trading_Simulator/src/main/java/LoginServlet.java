import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;

@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Parse the JSON request to get the username and password
        JsonObject jsonObject = readJsonObject(request);
        String username = jsonObject.get("username").getAsString();
        String password = jsonObject.get("password").getAsString(); // Note: In production, use hashed passwords

        // Authenticate the user
        if (authenticateUser(username, password, request, response)) {
            
            response.getWriter().write(String.format("{\"success\": true, \"username\": \"%s\", \"message\": \"Login successful.\"}", username));
        } else {
            response.getWriter().write("{\"success\": false, \"message\": \"Invalid username or password.\"}");
        }
    }

    private JsonObject readJsonObject(HttpServletRequest request) throws IOException {
        StringBuilder buffer = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
          
            
        }
        
        return new Gson().fromJson(buffer.toString(), JsonObject.class);
    }

    private boolean authenticateUser(String username, String password, HttpServletRequest request, HttpServletResponse response) {
        try (Connection conn = DBUtil.getConnection()) {
            String sql = "SELECT id FROM myAppDB.user WHERE username = ? AND password = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);
                ResultSet rs = pstmt.executeQuery();
                
                
                if (rs.next()) {
                    int userId = rs.getInt("id");
                    
                    // Create a session for the logged in user
                    HttpSession session = request.getSession();  // true to create if does not exist
                    session.setAttribute("userId", userId);
                    System.out.println("Session created with ID: " + userId);

                    
                    return true;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            try {
                response.getWriter().write("{\"success\": false, \"message\": \"Database error during login.\"}");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
        return false;
    }
    
    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }
}
