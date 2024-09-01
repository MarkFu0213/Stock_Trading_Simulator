import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/api/trade")
public class TradeServlet extends HttpServlet {
    
    private static final long serialVersionUID = 1L;
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }
    


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        setCorsHeaders(response);
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User is not logged in.");
            return;
        }
        

        JsonObject jsonObject = readJsonObject(request);
        if (jsonObject == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid JSON data.");
            return;
        }

        String stockSymbol = jsonObject.get("symbol").getAsString();
        int quantity = jsonObject.get("quantity").getAsInt();
        double price = jsonObject.get("price").getAsDouble();
        
        System.out.println(stockSymbol);
        System.out.println(quantity);
        System.out.println(price);

        int userId = (Integer) session.getAttribute("userId");
        Trade trade = new Trade(userId, stockSymbol, quantity, price);
        System.out.println(userId);
        
        System.out.println("start");
        processTrade(trade, response);
        System.out.println("finish");
        
    }

    private JsonObject readJsonObject(HttpServletRequest request) throws IOException {
        BufferedReader reader = request.getReader();
        return JsonParser.parseReader(reader).getAsJsonObject();
    }

    private void processTrade(Trade trade, HttpServletResponse response) throws IOException {
        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);
            
            System.out.println("cp1");
            
            double totalCost = trade.getQuantity() * trade.getPrice();
            if (!hasSufficientFunds(trade.getUserId(), totalCost, conn)) {
                conn.rollback();
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Insufficient funds to complete the transaction.");
                return;
            }

            System.out.println("cp2");
            Boolean result = updateOrInsertStock(trade, conn);
            System.out.println("the result is " + result);
            if (!result) {
            	System.out.println("insertorupdate error");
                conn.rollback();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to process trade.");
                return;
            }
            
            System.out.println("cp3");
            
            updateBalance(trade.getUserId(), totalCost, conn);
            
            System.out.println("cp4");
            conn.commit();
            response.getWriter().println("{\"success\": true, \"message\": \"Trade processed successfully, balance updated.\"}");
        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error during trade.");
        }
    }


    private boolean updateOrInsertStock(Trade trade, Connection conn) throws SQLException {
        String selectSql = "SELECT quantity FROM user_stocks WHERE user_id = ? AND stock_symbol = ?";
        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setInt(1, trade.getUserId());
            selectStmt.setString(2, trade.getStockSymbol());
            ResultSet rs = selectStmt.executeQuery();
            if (rs.next()) {
            	System.out.println("update");
                return updateStock(trade, conn, rs.getInt("quantity"));
            } else {
            	System.out.println("insert");
            	Boolean t = insertStock(trade, conn);
            	System.out.println(t);
            	System.out.println("after insert");
                return t;
            }
        }
    }

    private boolean updateStock(Trade trade, Connection conn, int existingQuantity) throws SQLException {
        String updateSql = "UPDATE user_stocks SET quantity = quantity + ?, last_purchased_price = ? WHERE user_id = ? AND stock_symbol = ?";
        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
            updateStmt.setInt(1, trade.getQuantity());
            updateStmt.setDouble(2, trade.getPrice());
            updateStmt.setInt(3, trade.getUserId());
            updateStmt.setString(4, trade.getStockSymbol());
            return updateStmt.executeUpdate() > 0;
        }
    }

    private boolean insertStock(Trade trade, Connection conn) throws SQLException {
        String insertSql = "INSERT INTO user_stocks (user_id, stock_symbol, quantity, last_purchased_price) VALUES (?, ?, ?, ?)";
        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
            insertStmt.setInt(1, trade.getUserId());
            insertStmt.setString(2, trade.getStockSymbol());
            insertStmt.setInt(3, trade.getQuantity());
            insertStmt.setDouble(4, trade.getPrice());

            boolean g = insertStmt.executeUpdate() > 0;
            System.out.println("insert-final");
            return g;
        } catch (SQLException e) {
            System.err.println("SQL Error: " + e.getMessage());
            System.err.println("SQL State: " + e.getSQLState());
            e.printStackTrace();
            return false;
        }

    }

    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000"); // Ensure this matches your client-side URL
        response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }
    
    private boolean hasSufficientFunds(int userId, double totalCost, Connection conn) throws SQLException {
    	System.out.println("inside balance check");
    	
        String sql = "SELECT balance FROM user WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        	
        	
            stmt.setInt(1, userId);
            
            System.out.println("inside balance check 1");
            
            ResultSet rs = stmt.executeQuery();
            
            System.out.println("inside balance check 2");
            if (rs.next()) {
                double balance = rs.getDouble("balance");
                
                
                System.out.println("balance: " + balance);
                return balance >= totalCost;
            }
            return false;
        }
    }
    private void updateBalance(int userId, double amount, Connection conn) throws SQLException {
        String sql = "UPDATE user SET balance = balance - ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        }
    }



    
}

