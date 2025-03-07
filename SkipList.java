import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicInteger;

class SkipList {

    private static final int MAX_LEVEL = 32;
    // Sentinal of skip list
    private final Node head;
    private final Random random;
    //Stores the current maximum level of skipList. DEFAULT = 32
    private final int maxLevel;
    //Used to determine if list should bes ortedin ascending or descending order
    private final boolean ascending;

    static class Node {
        // Buy or sell order stored here
        final Order value;
        /*
         * Stores references to next nodes at different levels.
         * AtomicReference used since Nodes will be deleted/inserted and only one thread should have access to latest version.
        */
        AtomicReference<Node>[] next;
        /*
         * Used to mark a node for deletion(=1 else =0) if its quantity <=0
         * AtomicInteger used since value will be upddated by thread and only thread should have access at a time.
         * Default value is 0
         */
        AtomicInteger marked;
        @SuppressWarnings("unchecked")
        Node(Order value, int level) {
            this.value = value;
            this.next = new AtomicReference[level + 1];
            for (int i = 0; i < next.length; i++) {
                this.next[i] = new AtomicReference<>();
            }
            this.marked = new AtomicInteger(0);
        }
    }

    public SkipList(boolean ascending) {
        this(MAX_LEVEL, ascending);
    }

    public SkipList(int maxLevel, boolean ascending) {
        this.maxLevel = maxLevel;
        this.ascending = ascending;
        //Head is sentinel, so Order value is null
        this.head = new Node(null, maxLevel);
        this.random = new Random();
    }
    
    public Node getHead() {
        return this.head;
    }

    /**
     * Generates a random level for a new node in the skip list.
     * The level is determined probabilistically, with higher levels occurring less frequently.
     * The level is incremented as long as a random coin flip (nextInt(2)) results in 1 and the level is less than the maximum allowed level.
     *
     * @return The randomly generated level for the new node.
     */
    private int randomLevel() {
        int level = 0;
        while (random.nextInt(2) == 1  && level < maxLevel) {
            level++;
        }
        return level;
    }

    /**
     * Inserts a new Order into the skip list.
     * The insertion maintains the sorted order of Orders based on their prices.
     *
     * @param value The Order object to insert.
     * @return true if the Order was successfully inserted, false if the exact Order object already exists.
     */
    public boolean insert(Order value) {
        // Generate random level for new node
        int level = randomLevel();
        Node newNode = new Node(value, level);
        // Array to store the nodes that will be updated.
        Node[] update = new Node[maxLevel + 1];
        Node current = head;

        // Find the insertion points at each level.
        for (int i = maxLevel; i >= 0; i--) {
            while (current.next[i].get() != null) {
                if (current.next[i].get().value == value) { 
                    return false;
                }
                int comparison = compare(current.next[i].get().value, value);
                if (comparison < 0) {
                    // Move to the next node if the current node's value is less than the new value.
                    current = current.next[i].get();
                } else {
                    // Break the loop if the current node's value is greater than or equal to the new value.
                    break;
                }
            }
            update[i] = current; // Store the node that will be updated at this level.
        }

        if (update[0].next[0].get() != null && update[0].next[0].get().value == value) { 
            return false;
        }

        // Update the next pointers at each level to insert the new node
        for (int i = 0; i <= level; i++) {
            newNode.next[i].set(update[i].next[i].get());
            update[i].next[i].set(newNode);
        }
        return true;
    }

    /**
     * Compares two Order objects based on their prices, considering the ascending/descending order.
     *
     * @param order1 The first Order object to compare.
     * @param order2 The second Order object to compare.
     * @return -1 if order1 should come before order2, 1 if order1 should come after order2, 0 if they are considered equal.
     */
    private int compare(Order order1, Order order2) {
        if (order1 == null && order2 == null) {
            return 0;
        }
        // If order1 is null, it comes before order2 in ascending order, after in descending.
        if (order1 == null) {
            return ascending ? -1 : 1;
        }
        // If order2 is null, it comes before order1 in descending order, after in ascending.
        if (order2 == null) {
            return ascending ? 1 : -1;
        }
        double price1 = order1.getPrice();
        double price2 = order2.getPrice();

        // Compare the prices based on the ascending/descending order.
        if (price1 < price2) {
            return ascending ? -1 : 1;
        } else if (price1 > price2) {
            return ascending ? 1 : -1;
        } else {
            return 0;
        }
    }
 
    /**
     * Finds and marks a matching Order node in the skip list based on the price condition,
     * quantity needed, ascending/descending order, and ticker.
     *
     * @param priceCondition The predicate to test the Order's price against.
     * @param quantityNeeded The quantity of shares needed for the match.
     * @param ascending True if the skip list is in ascending order (sell orders), false for descending (buy orders).
     * @param ticker The ticker symbol for the Order.
     * @return A MatchResult object containing the matched quantity, price, and output message,
     * or a MatchResult with 0 quantity and null output if no match is found.
     */
    public MatchResult findAndMarkMatch(Predicate<Double> priceCondition, int quantityNeeded, boolean ascending, String ticker) {
        Node current = head.next[0].get(); 
        while (current != null) {
            /*
            * Unmarked node is checked to ensure that order at the top of queue is not fulfilled. 
            * For example a new order came in and order at the top of queue was used in first call to this method to partially fulfil order.
            * Then this method is called again with remaining quantity of new order. At this time, order at the top of queue is marked
            * for deletion but not yet deleted so we loop through til we find next active order that matches.
            * If next active order does not match we break out of the loop since following orders will also not match as the skiplist 
            * maintains a sorted order.
            */ 
            if (current.value != null && current.marked.get() == 0) {   // Check for null value and unmarked node
                if (priceCondition.test(current.value.getPrice())) { // Test the price condition
                    if (current.marked.compareAndSet(0, 1)) {  // Attempt to mark the node as matched
                        int available = current.value.getQuantity().get(); // Get current top value in queue, lowest for sell queue and highest for buy
                        if (available > 0) {
                            int matched = Math.min(available, quantityNeeded); // Match quantity will be minimum of the two
                            /*
                             * Changing of quantity at top of queue is attempted, if successful, we check if quantity > 0
                             * and if it is we unmark it from 1 to 0 since the order in the queue is not yet fulfilled i.e. newOrder is fulfilled
                             * else we keep mark for deletion i.e. newOrder may or may not be completely fulfilled but order on top of queue is fulfilled.
                             * If order is fulfilled, even partially, return MatchResult object
                             */
                            while (true) {
                                if (current.value.getQuantity().compareAndSet(available, available - matched)) { 
                                    available = current.value.getQuantity().get();
                                    if(available > 0)
                                        current.marked.compareAndSet(1, 0);
                                    String output = null;
                                    if(ascending)
                                        output = String.format("  SELL ORDER: %d/%d left for %s @ %.2f%n", available, current.value.getOriginalQuantity(), ticker, current.value.getPrice());
                                    else
                                        output = String.format("  BUY ORDER: %d/%d left for %s @ %.2f%n", available, current.value.getOriginalQuantity(), ticker, current.value.getPrice());
                                    return new MatchResult(matched, current.value.getPrice(), output);
                                }
                                available = current.value.getQuantity().get();
                                if (available <= 0) {
                                    break;
                                }
                                matched = Math.min(available, quantityNeeded);
                            }
                        }
                        //Unmark the node since match was not successful
                        current.marked.compareAndSet(1, 0); 
                    }
                } else break; // Break out of the loop since price condition did not match
            }
            current = current.next[0].get(); // Move to next node
        }
        return new MatchResult(0, 0, null);
    }

    /**
     * Removes all nodes from the skip list that are marked for deletion.
     * This method traverses the skip list, identifies marked nodes, and updates the 'next'
     * pointers at all levels to effectively remove the marked nodes.
     */
    public void cleanupMarkedNodes() {
        Node current = head.next[0].get();
        Node previous = head;

        while (current != null) {
            // Check if the current node is marked for deletion
            if (current.marked.get() == 1) {
                
                // Remove the marked node from all levels of the skip list.
                for (int i = 0; i <= maxLevel; i++) {
                    Node prev = head;
                    Node curr = head.next[i].get();

                    // Traverse the level until the marked node or the end is reached.
                    while (curr != null && curr != current) {
                        prev = curr;
                        curr = curr.next[i].get();
                    }

                    // If the marked node is found at this level, update the 'next' pointer of the previous node.
                    if (curr != null && curr == current) {
                        prev.next[i].compareAndSet(current, current.next[i].get());
                    }
                }
                current = previous.next[0].get(); // Move 'current' to the node after the removed one.
            } else {
                previous = current;
                current = current.next[0].get(); // Move 'current' to the next node.
            }
        }
    }

    static class MatchResult {
        int matchedQuantity;
        double price;
        String output;

        MatchResult(int matchedQuantity, double price, String output) {
            this.matchedQuantity = matchedQuantity;
            this.price = price;
            this.output = output;
        }

        public int getMatchedQuantity() {
            return matchedQuantity;
        }

        public double getPrice() {
            return price;
        }
    }
    
}