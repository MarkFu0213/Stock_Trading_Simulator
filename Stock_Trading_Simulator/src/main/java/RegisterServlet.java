import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;

class DBUtil {
	static String URL = "jdbc:mysql://localhost:3306/myAppDB?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
    static String USERNAME = "root";  
    static String PASSWORD = "$VEg54vv";  

    static {
        try {
            // Ensure the JDBC driver is loaded by registering it
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }
}

@WebServlet("/api/register")
public class RegisterServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    public void init() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        String createDatabaseSql = "CREATE DATABASE IF NOT EXISTS myAppDB";
        String createUsersTableSql = """
            CREATE TABLE IF NOT EXISTS myAppDB.user (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(50) NOT NULL,
                password VARCHAR(255) NOT NULL,
                email VARCHAR(100) NOT NULL UNIQUE,
                balance INT NOT NULL DEFAULT 50000
            );
            """;
    


        String createUserStocksTableSql = """
            CREATE TABLE IF NOT EXISTS myAppDB.user_stocks (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                stock_symbol VARCHAR(10) NOT NULL,
                quantity INT DEFAULT 0,
                last_purchased_price DECIMAL(10, 2),
                FOREIGN KEY (user_id) REFERENCES user(id)
            );
            """;

        try (Connection conn = DBUtil.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createDatabaseSql); 
            stmt.execute("USE myAppDB");           
            stmt.executeUpdate(createUsersTableSql);       
            stmt.executeUpdate(createUserStocksTableSql);    
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        setCorsHeaders(response);

        Gson gson = new Gson();
        StringBuilder buffer = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line);
            }
        }

        JsonObject jsonObject = gson.fromJson(buffer.toString(), JsonObject.class);
        String username = jsonObject.get("username").getAsString();
        String password = jsonObject.get("password").getAsString();
        String email = jsonObject.get("email").getAsString();

        boolean isRegistered = registerUser(username, password, email, request);

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        if (isRegistered) {
            response.getWriter().write("{\"success\": true, \"message\": \"Registration and login successful.\"}");
        } else {
            response.getWriter().write("{\"success\": false, \"message\": \"email is already associated with an existing account.\"}");
        }
    }


    private boolean registerUser(String username, String password, String email, HttpServletRequest request) {
        
        if (emailExists(email)) {
            return false; // email exists, so return false 
        }

 
        String sql = "INSERT INTO myAppDB.user (username, password, email) VALUES (?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setString(2, password);  
            stmt.setString(3, email);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int userId = generatedKeys.getInt(1);
                        HttpSession session = request.getSession(true);
                        session.setAttribute("userId", userId);  // Store user ID in session
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean emailExists(String username) {
        String checkUserSql = "SELECT COUNT(*) FROM myAppDB.user WHERE email = ?";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement(checkUserSql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0; 
            }
        } catch (SQLException e) {
            e.printStackTrace();
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
