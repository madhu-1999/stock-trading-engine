import java.util.Random;

public class TradingEngine {

    private final static int NUM_TICKERS = 1024;
    //Stores the total number of stock tickers the system handles
    private int numTickers;
    // Array of skiplists of length 'numTickers'. Each ticker has a separate buy List
    private SkipList[] buyOrders;
    // Array of skiplists of length 'numTickers'. Each ticker has a separate sell List
    private SkipList[] sellOrders;
    // Array of length 'numTickers'. Stores String representation of each ticker. Varies from TICK0 to TICK1023.
    private String[] tickers;

    public TradingEngine(int numTickers) {
        this.numTickers = numTickers;
        this.buyOrders = new SkipList[numTickers];
        this.sellOrders = new SkipList[numTickers];
        this.tickers = new String[numTickers];

        for (int i = 0; i < numTickers; i++) {
            // Generating buy List as descending order priority queue for each ticker.
            buyOrders[i] = new SkipList(false);
            // Generating sell list as ascending order priority queue for each ticker.
            sellOrders[i] = new SkipList(true);
            tickers[i] = String.valueOf("TICK"+i);
        }
    }

     /**
     * Retrieves the index of a given ticker symbol in the 'tickers' array.
     * 
     * @param ticker The ticker symbol to find the index for.
     * @return The index of the ticker symbol in the 'tickers' array.
     * @throws IllegalArgumentException If the ticker symbol is not found in the 'tickers' array.
     */
    private int getTickerIndex(String ticker) {
        for (int i = 0; i < numTickers; i++) {
            if (tickers[i].equals(ticker)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Invalid ticker symbol: " + ticker);
    }

    /**
     * Adds a new order to the trading system and tries to match it
     *
     * @param orderType The type of order ("BUY" or "SELL").
     * @param ticker    The ticker symbol of the stock.
     * @param quantity  The quantity of shares to order.
     * @param price     The price per share.
     * @return true if the order was successfully added, false if the insert failed.
     * @throws IllegalArgumentException If the order type, quantity, or price is invalid.
     */
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
        int remainingQty = matchOrders(order); // Attempt to match new order
        
        // If there are remaining shares after matching, add the order to the appropriate queue after updating quantity.
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

    /**
     * Attempts to match a new order with existing orders in the trading system.
     *
     * @param newOrder The new order to match.
     * @return The remaining quantity of the new order after matching.
     */
    public int matchOrders(Order newOrder) {
        int tickerIdx = getTickerIndex(newOrder.getTicker());
        int remainingQty = newOrder.getQuantity().get(); // Initialize to original quantity of new order

        /*
         * Depending on order type we try to find a match in the counterpart queue (i.e buy Queue if order is sell and vice versa) of the 
         * order ticker. If the match happened and 'matchedQty' > 0 we update new order quantity and retry till either there is no match
         * or order is fulfilled.
         */
        if (newOrder.getOrderType() == Order.OrderType.BUY) {
            SkipList sellOrdersQueue = sellOrders[tickerIdx];

            // Loop while there are remaining shares to match.
            while (remainingQty > 0) {
                //Match on criteria sellPrice <= buyPrice 
                SkipList.MatchResult matchResult = sellOrdersQueue.findAndMarkMatch(p -> p <= newOrder.getPrice(), remainingQty, true, tickers[tickerIdx]);

                int matchedQty = matchResult.getMatchedQuantity();
                double matchPrice = matchResult.getPrice();

                // If no match is found, break the loop.
                if (matchedQty == 0) {
                    break;
                }

                // Update the remaining quantity.
                remainingQty -= matchedQty;
                System.out.printf("MATCHED: %d shares of %s at $%.2f%n", matchedQty, newOrder.getTicker(), matchPrice);
                System.out.printf("  BUY ORDER: %d/%d left for %s @ %.2f%n", remainingQty, newOrder.getOriginalQuantity(), tickers[tickerIdx], newOrder.getPrice());
                System.out.printf(matchResult.output);
            }
            sellOrdersQueue.cleanupMarkedNodes(); // Delete all nodes marked for deletion

        } else {
            SkipList buyOrdersQueue = buyOrders[tickerIdx];

            // Loop while there are remaining shares to match.
            while (remainingQty > 0) {
                //Match on criteria buyPrice >= sellPrice 
                SkipList.MatchResult matchResult = buyOrdersQueue.findAndMarkMatch(p -> p >= newOrder.getPrice(), remainingQty, false, tickers[tickerIdx]);

                int matchedQty = matchResult.getMatchedQuantity();

                // If no match is found, break the loop.
                if (matchedQty == 0) {
                    break;
                }

                // Update the remaining quantity.
                remainingQty -= matchedQty;
                System.out.printf("MATCHED: %d shares of %s at $%.2f%n", matchedQty, newOrder.getTicker(), newOrder.getPrice());
                System.out.printf("  SELL ORDER: %d/%d left for %s @ %.2f%n", remainingQty, newOrder.getOriginalQuantity(), tickers[tickerIdx], newOrder.getPrice());
                System.out.printf(matchResult.output);
            }
            buyOrdersQueue.cleanupMarkedNodes(); // Delete all nodes marked for deletion
        }

        return remainingQty;
    }

    /**
     * Simulates a trading session for a given duration.
     * Generates random buy and sell orders and adds them to the trading engine.
     * After the simulation, it prints the remaining orders in the order book.
     *
     * @param trading         The TradingEngine instance to use for trading.
     * @param durationSeconds The duration of the simulation in seconds.
     */
    public void simulateTrading(TradingEngine trading, int durationSeconds) {

        long startTime = System.currentTimeMillis();
        Random random = new Random();

        // Generate random orders and add them to appropriate queue.
        // Matching is attempted when new order is inserted into a queue.
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

        // Run the simulation for the specified duration.
        while (System.currentTimeMillis() - startTime < durationSeconds * 1000L) {
            // Create an array of 5 threads. This is used to simulate multithreaded environment.
            // The threads add random orders concurrently.
            Thread[] threads = new Thread[5];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(generateRandomOrder);
                threads[i].start();
            }

            // Wait for all threads to finish
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            // Pause thread for a random interval (100 to 300 ms) to simulate pauses between new transactions
            try {
                Thread.sleep((long) (random.nextDouble() * 200 + 100));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("\nSimulation complete!");

        //Iterate through all tickers to print status of buy and sell queues at the end of the simulation.
        System.out.println("\nRemaining orders in the book:");
        for (int tickerIdx = 0; tickerIdx < trading.numTickers; tickerIdx++) {
            String ticker = trading.tickers[tickerIdx];
            SkipList buyOrdersQueue = trading.buyOrders[tickerIdx];
            SkipList sellOrdersQueue = trading.sellOrders[tickerIdx];

            boolean hasOrders = false;
            StringBuilder output = new StringBuilder(String.format("\nTicker: %s", ticker));
            // Collect remaining buy order in queue of ticker
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

            // Collect remaining buy order in queue of ticker
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