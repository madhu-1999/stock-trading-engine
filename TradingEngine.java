import java.util.Random;

public class TradingEngine {

    private final static int NUM_TICKERS = 1024;
    private int numTickers;
    private SkipList[] buyOrders;
    private SkipList[] sellOrders;
    private String[] tickers;

    public TradingEngine(int numTickers) {
        this.numTickers = numTickers;
        this.buyOrders = new SkipList[numTickers];
        this.sellOrders = new SkipList[numTickers];
        this.tickers = new String[numTickers];

        for (int i = 0; i < numTickers; i++) {
            buyOrders[i] = new SkipList(false);
            sellOrders[i] = new SkipList(true);
            tickers[i] = String.valueOf("TICK"+i);
        }
    }

    private int getTickerIndex(String ticker) {
        for (int i = 0; i < numTickers; i++) {
            if (tickers[i].equals(ticker)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid ticker symbol: " + ticker);
    }

    public boolean addOrder(String orderType, String ticker, int quantity, double price) {
        if (!orderType.equals("BUY") && !orderType.equals("SELL")) {
            throw new IllegalArgumentException("Order type must be 'BUY' or 'SELL'");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (price <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }

        int tickerIdx = getTickerIndex(ticker);
        Order order = new Order(orderType.equals("BUY") ? Order.OrderType.BUY : Order.OrderType.SELL, ticker, quantity, price, System.currentTimeMillis());
        int remainingQty = matchOrders(order);

        if (remainingQty > 0) {
            order.getQuantity().set(remainingQty);
            if (orderType.equals("BUY")) {
                return buyOrders[tickerIdx].insert(order);
            } else {
                return sellOrders[tickerIdx].insert(order);
            }
        }

        return true;
    }

    public int matchOrders(Order newOrder) {
        int tickerIdx = getTickerIndex(newOrder.getTicker());
        int remainingQty = newOrder.getQuantity().get();

        if (newOrder.getOrderType() == Order.OrderType.BUY) {
            SkipList sellOrdersQueue = sellOrders[tickerIdx];
            while (remainingQty > 0) {
                SkipList.MatchResult matchResult = sellOrdersQueue.findAndMarkMatch(p -> p <= newOrder.getPrice(), remainingQty, true, tickers[tickerIdx]);

                int matchedQty = matchResult.getMatchedQuantity();
                double matchPrice = matchResult.getPrice();

                if (matchedQty == 0) {
                    break;
                }

                remainingQty -= matchedQty;
                System.out.printf("MATCHED: %d shares of %s at $%.2f%n", matchedQty, newOrder.getTicker(), matchPrice);
                System.out.printf("  BUY ORDER: %d/%d left for %s @ %.2f%n", remainingQty, newOrder.getOriginalQuantity(), tickers[tickerIdx], newOrder.getPrice());
                System.out.printf(matchResult.output);
            }
            sellOrdersQueue.cleanupMarkedNodes();

        } else {
            SkipList buyOrdersQueue = buyOrders[tickerIdx];
            while (remainingQty > 0) {
                SkipList.MatchResult matchResult = buyOrdersQueue.findAndMarkMatch(p -> p >= newOrder.getPrice(), remainingQty, false, tickers[tickerIdx]);

                int matchedQty = matchResult.getMatchedQuantity();

                if (matchedQty == 0) {
                    break;
                }

                remainingQty -= matchedQty;
                System.out.printf("MATCHED: %d shares of %s at $%.2f%n", matchedQty, newOrder.getTicker(), newOrder.getPrice());
                System.out.printf("  SELL ORDER: %d/%d left for %s @ %.2f%n", remainingQty, newOrder.getOriginalQuantity(), tickers[tickerIdx], newOrder.getPrice());
                System.out.printf(matchResult.output);
            }
            buyOrdersQueue.cleanupMarkedNodes();
        }

        return remainingQty;
    }

    public void simulateTrading(TradingEngine trading, int durationSeconds) {

        long startTime = System.currentTimeMillis();
        Random random = new Random();

        Runnable generateRandomOrder = () -> {
            String orderType = random.nextBoolean() ? "BUY" : "SELL";
            String ticker = trading.tickers[random.nextInt(trading.tickers.length)]; 
            int quantity = random.nextInt(901) + 100;
            double basePrice = random.nextDouble() * 90 + 10;
            double price = Math.round(basePrice * (random.nextDouble() * 0.04 + 0.98) * 100.0) / 100.0;

            try {
                trading.addOrder(orderType, ticker, quantity, price);
                System.out.printf("ADDED: %s %d %s @ $%.2f%n", orderType, quantity, ticker, price);
            } catch (Exception e) {
                System.out.printf("Error adding order: %s%n", e.getMessage());
            }
        };

        System.out.println("Starting trading simulation... for " + durationSeconds + " seconds");

        while (System.currentTimeMillis() - startTime < durationSeconds * 1000L) {
            Thread[] threads = new Thread[5];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(generateRandomOrder);
                threads[i].start();
            }

            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            try {
                Thread.sleep((long) (random.nextDouble() * 200 + 100));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("\nSimulation complete!");

        System.out.println("\nRemaining orders in the book:");
        for (int tickerIdx = 0; tickerIdx < trading.numTickers; tickerIdx++) {
            String ticker = trading.tickers[tickerIdx];
            SkipList buyOrdersQueue = trading.buyOrders[tickerIdx];
            SkipList sellOrdersQueue = trading.sellOrders[tickerIdx];

            boolean hasOrders = false;
            StringBuilder output = new StringBuilder(String.format("\nTicker: %s", ticker));

            SkipList.Node current = buyOrdersQueue.getHead().next[0].get(); 
            if (current != null && current.value != null) { 
                hasOrders = true;
                output.append("\n  Buy Orders:");
                while (current != null && current.value != null) { 
                    if (current.marked.get() == 0 && current.value.getQuantity().get() > 0) {
                        output.append(String.format("\n    %d/%d shares @ $%.2f", current.value.getQuantity().get(), current.value.getOriginalQuantity(), current.value.getPrice()));
                    }
                    current = current.next[0].get(); 
                }
            }

            current = sellOrdersQueue.getHead().next[0].get(); 
            if (current != null && current.value != null) { 
                hasOrders = true;
                output.append("\n  Sell Orders:");
                while (current != null && current.value != null) { 
                    if (current.marked.get() == 0 && current.value.getQuantity().get() > 0) {
                        output.append(String.format("\n    %d/%d shares @ $%.2f", current.value.getQuantity().get(), current.value.getOriginalQuantity(), current.value.getPrice()));
                    }
                    current = current.next[0].get(); 
                }
            }

            if (hasOrders) {
                System.out.println(output.toString());
            }
        }
    }

    public static void main(String[] args) {
        int durationSeconds = 30; // Default duration
    
        if (args.length > 0) {
            try {
                durationSeconds = Integer.parseInt(args[0]);
                if (durationSeconds <= 0) {
                    System.err.println("Duration must be a positive integer. Using default duration of 30 seconds.");
                    durationSeconds = 30;
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid duration argument. Using default duration of 30 seconds.");
            }
        }
    
        TradingEngine trading = new TradingEngine(NUM_TICKERS);
        trading.simulateTrading(trading, durationSeconds);
    }

}