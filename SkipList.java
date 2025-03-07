import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicInteger;

class SkipList {

    private static final int MAX_LEVEL = 32;
    private final Node head;
    private final Random random;
    private final int maxLevel;
    private final boolean ascending;

    static class Node {
        final Order value;
        AtomicReference<Node>[] next;
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
        this.head = new Node(null, maxLevel);
        this.random = new Random();
    }
    
    public Node getHead() {
        return this.head;
    }
    private int randomLevel() {
        int level = 0;
        while (random.nextInt(2) == 1  && level < maxLevel) {
            level++;
        }
        return level;
    }

    public boolean insert(Order value) {
        int level = randomLevel();
        Node newNode = new Node(value, level);
        Node[] update = new Node[maxLevel + 1];
        Node current = head;

        for (int i = maxLevel; i >= 0; i--) {
            while (current.next[i].get() != null) {
                int comparison = compare(current.next[i].get().value, value);
                if (comparison < 0) {
                    current = current.next[i].get();
                } else if (comparison == 0) {
                    return false;
                } else {
                    break;
                }
            }
            update[i] = current;
        }

        if (update[0].next[0].get() != null && compare(update[0].next[0].get().value, value) == 0) {
            return false;
        }

        for (int i = 0; i <= level; i++) {
            newNode.next[i].set(update[i].next[i].get());
            update[i].next[i].set(newNode);
        }
        return true;
    }

    public boolean delete(Order value) {
        Node[] update = new Node[maxLevel + 1];
        Node current = head;

        for (int i = maxLevel; i >= 0; i--) {
            while (current.next[i].get() != null) {
                int comparison = compare(current.next[i].get().value, value);
                if (comparison < 0) {
                    current = current.next[i].get();
                } else {
                    break;
                }
            }
            update[i] = current;
        }

        if (update[0].next[0].get() != null && compare(update[0].next[0].get().value, value) == 0) {
            Node deletedNode = update[0].next[0].get();
            for (int i = 0; i < deletedNode.next.length; i++) {
                update[i].next[i].set(deletedNode.next[i].get());
            }
            return true;
        }
        return false;
    }

    public boolean contains(Order value) {
        Node current = head;
        for (int i = maxLevel; i >= 0; i--) {
            while (current.next[i].get() != null) {
                int comparison = compare(current.next[i].get().value, value);
                if (comparison < 0) {
                    current = current.next[i].get();
                } else {
                    break;
                }
            }
        }
        return current.next[0].get() != null && compare(current.next[0].get().value, value) == 0;
    }

    private int compare(Order order1, Order order2) {
        if (order1 == null && order2 == null) {
            return 0;
        }
        if (order1 == null) {
            return ascending ? -1 : 1;
        }
        if (order2 == null) {
            return ascending ? 1 : -1;
        }
        double price1 = order1.getPrice();
        double price2 = order2.getPrice();

        if (price1 < price2) {
            return ascending ? -1 : 1;
        } else if (price1 > price2) {
            return ascending ? 1 : -1;
        } else {
            return 0;
        }
    }
 
    public MatchResult findAndMarkMatch(Predicate<Double> priceCondition, int quantityNeeded, boolean ascending, String ticker) {
        Node current = head.next[0].get(); 
        while (current != null) {
            if (current.value != null && current.marked.get() == 0) { 
                if (priceCondition.test(current.value.getPrice())) {
                    if (current.marked.compareAndSet(0, 1)) { 
                        int available = current.value.getQuantity().get();
                        if (available > 0) {
                            int matched = Math.min(available, quantityNeeded);
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
                        current.marked.compareAndSet(1, 0); 
                    }
                } else break;
            }
            current = current.next[0].get(); 
        }
        return new MatchResult(0, 0, null);
    }

    public void cleanupMarkedNodes() {
        Node current = head.next[0].get();
        Node previous = head;

        while (current != null) {
            if (current.marked.get() == 1) {
                
                for (int i = 0; i <= maxLevel; i++) {
                    Node prev = head;
                    Node curr = head.next[i].get();
                    while (curr != null && curr != current) {
                        prev = curr;
                        curr = curr.next[i].get();
                    }
                    if (curr != null && curr == current) {
                        prev.next[i].compareAndSet(current, current.next[i].get());
                    }
                }
                current = previous.next[0].get(); 
            } else {
                previous = current;
                current = current.next[0].get();
            }
        }
    }

    public void printSkipList() {
        for (int i = maxLevel; i >= 0; i--) {
            System.out.print("Level " + i + ": ");
            Node current = head.next[i].get();
            while (current != null) {
                System.out.print(current.value + " ");
                current = current.next[i].get();
            }
            System.out.println();
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