import java.math.BigDecimal;

/**
 * Demo runner — shows all 5 validation rules in action.
 * Run: javac src/*.java -d out && java -cp out Main
 */
public class Main {

    private static final BigDecimal MARKET_PRICE = new BigDecimal("45000.00");

    public static void main(String[] args) {
        OrderValidator validator = new OrderValidator();

        System.out.println("=".repeat(55));
        System.out.println("Gold Order Validation Demo");
        System.out.println("Market price: " + MARKET_PRICE + " THB/baht-weight");
        System.out.println("=".repeat(55));

        // 1. Valid buy order
        test(validator, "1. Valid buy order", new Order(
            "C001", "buy",
            new BigDecimal("2.0"),
            new BigDecimal("45000.00"),
            new BigDecimal("200000.00"),
            MARKET_PRICE
        ));

        // 2. Rule 1: invalid order_type
        test(validator, "2. Invalid order_type", new Order(
            "C001", "hold",
            new BigDecimal("2.0"),
            new BigDecimal("45000.00"),
            new BigDecimal("200000.00"),
            MARKET_PRICE
        ));

        // 3. Rule 2: quantity not multiple of 0.5
        test(validator, "3. Quantity not multiple of 0.5", new Order(
            "C001", "buy",
            new BigDecimal("1.3"),
            new BigDecimal("45000.00"),
            new BigDecimal("200000.00"),
            MARKET_PRICE
        ));

        // 4. Rule 3: negative quoted_price
        test(validator, "4. Negative quoted_price", new Order(
            "C001", "buy",
            new BigDecimal("2.0"),
            new BigDecimal("-100.00"),
            new BigDecimal("200000.00"),
            MARKET_PRICE
        ));

        // 5. Rule 4: insufficient balance
        test(validator, "5. Insufficient balance (has 50,000 THB)", new Order(
            "C001", "buy",
            new BigDecimal("5.0"),
            new BigDecimal("45000.00"),
            new BigDecimal("50000.00"),
            MARKET_PRICE
        ));

        // 6. Rule 5: price too far from market
        test(validator, "6. Price too stale (> 2% from market)", new Order(
            "C001", "buy",
            new BigDecimal("2.0"),
            new BigDecimal("40000.00"),
            new BigDecimal("200000.00"),
            MARKET_PRICE
        ));

        // 7. Valid sell order
        test(validator, "7. Valid sell order", new Order(
            "C001", "sell",
            new BigDecimal("2.0"),
            new BigDecimal("45000.00"),
            new BigDecimal("200000.00"),
            MARKET_PRICE
        ));

        // 8. Null order
        test(validator, "8. Null order", null);
    }

    private static void test(OrderValidator validator, String label, Order order) {
        System.out.println("\n" + label);
        try {
            validator.validate(order);
            System.out.println("Result: VALID");
        } catch (IllegalArgumentException e) {
            System.out.println("Result: INVALID - " + e.getMessage());
        }
    }
}
