import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/portfolio")
public class PortfolioServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	System.out.println("start doGet");
        setCorsHeaders(response);
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User is not logged in.");
            return;
        }

        int userId = (Integer) session.getAttribute("userId");
        try (Connection conn = DBUtil.getConnection()) {
            String sql = "SELECT stock_symbol, quantity, last_purchased_price FROM user_stocks WHERE user_id = ?";
            String balanceSql = "SELECT balance FROM user WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 PreparedStatement balanceStmt = conn.prepareStatement(balanceSql)) {
                stmt.setInt(1, userId);
                balanceStmt.setInt(1, userId);

                ResultSet rs = stmt.executeQuery();
                ResultSet balanceRs = balanceStmt.executeQuery();

                List<Stock> stocks = new ArrayList<>();
                double balance = 0;
                if (balanceRs.next()) {
                    balance = balanceRs.getDouble("balance");
                }

                while (rs.next()) {
                    stocks.add(new Stock(
                        rs.getString("stock_symbol"),
                        rs.getInt("quantity"),
                        rs.getDouble("last_purchased_price")
                    ));
                }
                

                PortfolioResponse portfolioResponse = new PortfolioResponse(stocks, balance);
                Gson gson = new Gson();
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(gson.toJson(portfolioResponse));
                System.out.println("end doGet");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("end doGet");
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to retrieve portfolio data.");
        }
    }

    static class PortfolioResponse {
        List<Stock> stocks;
        double cashBalance;

        public PortfolioResponse(List<Stock> stocks, double cashBalance) {
            this.stocks = stocks;
            this.cashBalance = cashBalance;
        }
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        setCorsHeaders(response);
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User is not logged in.");
            return;
        }

        JsonObject jsonObject = new Gson().fromJson(request.getReader(), JsonObject.class);
        String symbol = jsonObject.get("symbol").getAsString();
        int quantity = jsonObject.get("quantity").getAsInt();
        double price = jsonObject.get("price").getAsDouble();
        String tradeType = jsonObject.get("tradeType").getAsString();
 

        int userId = (Integer) session.getAttribute("userId");


        try (Connection conn = DBUtil.getConnection()) {
            conn.setAutoCommit(false);

            boolean success;
            if ("buy".equals(tradeType)) {
                success = processBuy(userId, symbol, quantity, price, conn);
            } else {
                success = processSell(userId, symbol, quantity, price, conn);
            }

            if (!success) {
                conn.rollback(); // Rollback transaction if not successful
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Trade failed.");
                return;
            }

            conn.commit();
            response.getWriter().println("{\"success\": true, \"message\": \"Trade processed successfully.\"}");
        } catch (SQLException e) {
            
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error during trade.");
        }
    }

    private boolean processBuy(int userId, String symbol, int quantity, double price, Connection conn) throws SQLException {
        double totalCost = quantity * price;
        if (!hasSufficientFunds(userId, totalCost, conn)) {
            return false;
        }
        if (!updateOrInsertStock(userId, symbol, quantity, price, conn, true)) {
            return false;
        }
        updateBalance(userId, -totalCost, conn);
        return true;
    }

    private boolean processSell(int userId, String symbol, int quantity, double price, Connection conn) throws SQLException {
        if (!hasSufficientStocks(userId, symbol, quantity, conn)) {
            return false;
        }
        double totalRevenue = quantity * price;
        if (!updateOrInsertStock(userId, symbol, quantity, price, conn, false)) {
            return false;
        }
        updateBalance(userId, totalRevenue, conn);
        return true;
    }
    
    

    private void setCorsHeaders(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:3000");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");
    }

    private boolean hasSufficientFunds(int userId, double totalCost, Connection conn) throws SQLException {
        String sql = "SELECT balance FROM user WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                double balance = rs.getDouble("balance");
                return balance >= totalCost;
            }
            return false;
        }
    }

    private boolean hasSufficientStocks(int userId, String symbol, int quantity, Connection conn) throws SQLException {
        String sql = "SELECT quantity FROM user_stocks WHERE user_id = ? AND stock_symbol = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setString(2, symbol);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int existingQuantity = rs.getInt("quantity");
                return existingQuantity >= quantity;
            }
            return false;
        }
    }

    private boolean updateOrInsertStock(int userId, String symbol, int quantity, double price, Connection conn, boolean isBuy) throws SQLException {
        String selectSql = "SELECT quantity FROM user_stocks WHERE user_id = ? AND stock_symbol = ?";
        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setInt(1, userId);
            selectStmt.setString(2, symbol);
            ResultSet rs = selectStmt.executeQuery();
            if (rs.next()) {
                int existingQuantity = rs.getInt("quantity");
                int newQuantity = isBuy ? existingQuantity + quantity : existingQuantity - quantity;

                if (newQuantity < 0) {
                    return false;  // This should not happen due to client-side checks, but just in case
                } else if (newQuantity == 0) {
                    // If new quantity is zero, delete the stock entry
                    String deleteSql = "DELETE FROM user_stocks WHERE user_id = ? AND stock_symbol = ?";
                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                        deleteStmt.setInt(1, userId);
                        deleteStmt.setString(2, symbol);
                        return deleteStmt.executeUpdate() > 0;
                    }
                } else {
                    // Update the existing stock entry with the new quantity and price
                    String updateSql = "UPDATE user_stocks SET quantity = ?, last_purchased_price = ? WHERE user_id = ? AND stock_symbol = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, newQuantity);
                        updateStmt.setDouble(2, price);
                        updateStmt.setInt(3, userId);
                        updateStmt.setString(4, symbol);
                        return updateStmt.executeUpdate() > 0;
                    }
                }
            } else if (isBuy) {
                // If buying and no existing record, insert a new stock entry
                String insertSql = "INSERT INTO user_stocks (user_id, stock_symbol, quantity, last_purchased_price) VALUES (?, ?, ?, ?)";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setInt(1, userId);
                    insertStmt.setString(2, symbol);
                    insertStmt.setInt(3, quantity);
                    insertStmt.setDouble(4, price);
                    return insertStmt.executeUpdate() > 0;
                }
            }
            return false;
        }
    }


    private void updateBalance(int userId, double amount, Connection conn) throws SQLException {
        String sql = "UPDATE user SET balance = balance + ? WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        	System.out.println(amount);
            stmt.setDouble(1, amount);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        }
    }

    static class Stock {
        String symbol;
        int quantity;
        double lastPurchasedPrice;

        public Stock(String symbol, int quantity, double lastPurchasedPrice) {
            this.symbol = symbol;
            this.quantity = quantity;
            this.lastPurchasedPrice = lastPurchasedPrice;
          
        }
    }
}

