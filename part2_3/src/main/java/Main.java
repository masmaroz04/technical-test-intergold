import java.math.BigDecimal;

public class Main {

    private static final BigDecimal MARKET_PRICE = new BigDecimal("45000.00");

    public static void main(String[] args) {
        OrderValidator validator = new OrderValidator();

        System.out.println("=".repeat(55));
        System.out.println("Gold Order Validation Demo");
        System.out.println("Market price: " + MARKET_PRICE + " THB/baht-weight");
        System.out.println("=".repeat(55));

        // 1. Valid buy order
        processing(validator, "1. Valid buy order", new Order(
            "C001",
            "buy",
            new BigDecimal("2.0"),        // quantity
            new BigDecimal("45200.00"),   // quoted price
            new BigDecimal("200000.00"),  // balance
            MARKET_PRICE
        ));

        // 2. Invalid order_type
        processing(validator, "2. Invalid order_type", new Order(
            "C002",
            "hold",
            new BigDecimal("2.0"),        // quantity
            new BigDecimal("45000.00"),   // quoted price
            new BigDecimal("200000.00"),  // balance
            MARKET_PRICE
        ));

        // 3. Quantity not multiple of 0.5
        processing(validator, "3. Quantity not multiple of 0.5", new Order(
            "C003",
            "buy",
            new BigDecimal("1.3"),        // quantity
            new BigDecimal("45000.00"),   // quoted price
            new BigDecimal("200000.00"),  // balance
            MARKET_PRICE
        ));

        // 4. Negative quoted_price
        processing(validator, "4. Negative quoted_price", new Order(
            "C004",
            "buy",
            new BigDecimal("2.0"),        // quantity
            new BigDecimal("-100.00"),    // quoted price
            new BigDecimal("200000.00"),  // balance
            MARKET_PRICE
        ));

        // 5. Insufficient balance
        processing(validator, "5. Insufficient balance", new Order(
            "C005",
            "buy",
            new BigDecimal("0.5"),        // quantity
            new BigDecimal("45200.00"),   // quoted price
            new BigDecimal("100.00"),     // balance
            MARKET_PRICE
        ));

        // 6. Buy price too stale (> 2% from expected buy price)
        processing(validator, "6. Buy price too stale (> 2% from expected buy price)", new Order(
            "C006",
            "buy",
            new BigDecimal("0.5"),        // quantity
            new BigDecimal("40000.00"),   // quoted price
            new BigDecimal("200000.00"),  // balance
            MARKET_PRICE
        ));

        // 7. Valid sell order
        processing(validator, "7. Valid sell order", new Order(
            "C007",
            "sell",
            new BigDecimal("2.0"),        // quantity
            new BigDecimal("45000.00"),   // quoted price
            BigDecimal.ZERO,              // balance
            MARKET_PRICE
        ));

        // 8. Null order
        processing(validator, "8. Null order", null);

        // 9-10. Daily limit — C008 start counting
        processing(validator, "9. Buy 2.0 quantity", new Order(
            "C008",
            "buy",
            new BigDecimal("2.0"),        // quantity
            new BigDecimal("45200.00"),   // quoted price
            new BigDecimal("200000.00"),  // balance
            MARKET_PRICE
        ));

        processing(validator, "10. Buy 4.0 (total would be 6.0 — exceeds limit)", new Order(
            "C008",
            "buy",
            new BigDecimal("4.0"),        // quantity
            new BigDecimal("45200.00"),   // quoted price
            new BigDecimal("200000.00"),  // balance
            MARKET_PRICE
        ));
    }

    private static void processing(OrderValidator validator, String label, Order order) {
        System.out.println("\n" + label);
        try {
            validator.validate(order);
            System.out.println("Result: VALID");
        } catch (IllegalArgumentException e) {
            System.out.println("Result: INVALID - " + e.getMessage());
        }
    }
}
