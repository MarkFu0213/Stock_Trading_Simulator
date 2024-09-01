public class Trade {
    private int userId;
    private String stockSymbol;
    private int quantity;
    private double price;

    public Trade(int userId, String stockSymbol, int quantity, double price) {
        this.userId = userId;
        this.stockSymbol = stockSymbol;
        this.quantity = quantity;
        this.price = price;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getStockSymbol() {
        return stockSymbol;
    }

    public void setStockSymbol(String stockSymbol) {
        this.stockSymbol = stockSymbol;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }
}
