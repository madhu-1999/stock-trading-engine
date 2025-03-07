import java.util.concurrent.atomic.AtomicInteger;

public class Order {
    enum OrderType {
        BUY,
        SELL
    }

    private OrderType orderType;
    private String ticker;
    private AtomicInteger quantity;
    private double price;
    private long timestamp;
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
