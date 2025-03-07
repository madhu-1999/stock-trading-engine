import java.util.concurrent.atomic.AtomicInteger;

public class Order {
    enum OrderType {
        BUY,
        SELL
    }

    // Stores if order is BUY or SELL
    private OrderType orderType;
    // Ticker of order ranging from TICK0 to TICK1023
    private String ticker;
    /*
    This is the quantity which will be decremented whenever order is partially or fully fulfilled.
    AtomicInteger is used to ensure only one thread updates quantity at a time.
    */   
    private AtomicInteger quantity;
    // Order price will randomly vary between $9.8 and $102
    private double price;
    private long timestamp;
    //Quantity the order was added in with. Maintained for tracking how much of the order has been fulfilled.
    // Varies from 100 to 1000 
    private int originalQuantity;

    public Order(OrderType orderType, String ticker, int quantity, double price, long timestamp) {
        this.orderType = orderType;
        this.ticker = ticker;
        this.quantity = new AtomicInteger(quantity);
        this.price = price;
        this.timestamp = timestamp;
        this.originalQuantity = quantity;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public String getTicker() {
        return ticker;
    }

    public AtomicInteger getQuantity() {
        return quantity;
    }

    public double getPrice() {
        return price;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getOriginalQuantity() {
        return originalQuantity;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderType=" + orderType +
                ", ticker='" + ticker + '\'' +
                ", quantity=" + quantity.get() +
                ", price=" + price +
                ", timestamp=" + timestamp +
                ", originalQuantity=" + originalQuantity +
                '}';
    }
}
