import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assertions;
import java.math.BigDecimal;

@DisplayName("OrderValidator Tests")
class OrderValidatorTest {

    private static final BigDecimal MARKET    = new BigDecimal("45000.00");

    // --- Valid cases ---
    @Test @DisplayName("valid buy order should pass")
    void testValidBuyOrder() {
        OrderValidator validator = new OrderValidator();
        Assertions.assertDoesNotThrow(() -> validator.validate(new Order(
            "C001",
            "buy",
            new BigDecimal("2.0"),
            new BigDecimal("45000.00"),
            new BigDecimal("200000.00"),
            MARKET
        )));
    }

    @Test @DisplayName("valid sell order should pass")
    void testValidSellOrder() {
        OrderValidator validator = new OrderValidator();
        Assertions.assertDoesNotThrow(() -> validator.validate(new Order(
            "C001",
            "sell",
            new BigDecimal("2.0"),
            new BigDecimal("45000.00"),
            BigDecimal.ZERO,
            MARKET
        )));
    }

    // --- Null order ---
    @Test @DisplayName("null order should fail")
    void testNullOrder() {
        OrderValidator validator = new OrderValidator();
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
            () -> validator.validate(null));
        Assertions.assertEquals("order must not be null", ex.getMessage());
    }

    // --- Rule 1: order_type ---
    @Test @DisplayName("order_type = hold should fail")
    void testInvalidOrderType() {
        OrderValidator validator = new OrderValidator();
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
            () -> validator.validate(new Order(
                "C001",
                "hold",
                new BigDecimal("2.0"),
                new BigDecimal("45000.00"),
                new BigDecimal("200000.00"),
                MARKET
            )));
        Assertions.assertEquals("order_type must be 'buy' or 'sell', got: hold", ex.getMessage());
    }

    // --- Rule 2: quantity ---
    @Test @DisplayName("quantity = 0 should fail")
    void testQuantityZero() {
        OrderValidator validator = new OrderValidator();
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
            () -> validator.validate(new Order(
                "C001",
                "buy",
                new BigDecimal("0"),
                new BigDecimal("45000.00"),
                new BigDecimal("200000.00"),
                MARKET
            )));
        Assertions.assertEquals("quantity must be positive", ex.getMessage());
    }

    @Test @DisplayName("quantity = 1.3 (not multiple of 0.5) should fail")
    void testQuantityNotMultipleOfHalf() {
        OrderValidator validator = new OrderValidator();
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
            () -> validator.validate(new Order(
                "C001",
                "buy",
                new BigDecimal("1.3"),
                new BigDecimal("45000.00"),
                new BigDecimal("200000.00"),
                MARKET
            )));
        Assertions.assertEquals("quantity must be a multiple of 0.5 baht-weight, got: 1.3", ex.getMessage());
    }

    // --- Rule 3: quoted_price ---
    @Test @DisplayName("quoted_price = -100 should fail")
    void testNegativeQuotedPrice() {
        OrderValidator validator = new OrderValidator();
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
            () -> validator.validate(new Order(
                "C001",
                "buy",
                new BigDecimal("2.0"),
                new BigDecimal("-100.00"),
                new BigDecimal("200000.00"),
                MARKET
            )));
        Assertions.assertEquals("quoted_price must be positive", ex.getMessage());
    }

    // --- Rule 4: balance ---
    @Test @DisplayName("insufficient balance should fail")
    void testInsufficientBalance() {
        OrderValidator validator = new OrderValidator();
        // 0.5 x 45000 = 22500.00
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
            () -> validator.validate(new Order(
                "C001",
                "buy",
                new BigDecimal("0.5"),
                new BigDecimal("45000.00"),
                new BigDecimal("100.00"),
                MARKET
            )));
        Assertions.assertEquals(
            "insufficient balance: need 22500.00 THB but customer has 100.00 THB",
            ex.getMessage());
    }

    // --- Rule 5: spread calculation ---
    @Test @DisplayName("buy: price within 2% of expected buy price should pass")
    void testBuyPriceWithinTolerance() {
        OrderValidator validator = new OrderValidator();
        // 45000 is 0.49% below buy price 45225 → pass
        Assertions.assertDoesNotThrow(() -> validator.validate(new Order(
            "C001",
            "buy",
            new BigDecimal("2.0"),
            new BigDecimal("45000.00"),
            new BigDecimal("200000.00"),
            MARKET
        )));
    }

    @Test @DisplayName("buy: price > 2% from expected buy price should fail")
    void testBuyPriceTooFarFromBuyPrice() {
        OrderValidator validator = new OrderValidator();
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
            () -> validator.validate(new Order(
                "C001",
                "buy",
                new BigDecimal("2.0"),
                new BigDecimal("40000.00"),
                new BigDecimal("200000.00"),
                MARKET
            )));
        Assertions.assertEquals(
            "quoted_price 40000.00 is more than 2% away from expected buy price 45225.00 (deviation: 11.55%)",
            ex.getMessage());
    }

    @Test @DisplayName("sell: price > 2% from market should fail")
    void testSellPriceTooFarFromMarket() {
        OrderValidator validator = new OrderValidator();
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
            () -> validator.validate(new Order(
                "C001",
                "sell",
                new BigDecimal("2.0"),
                new BigDecimal("40000.00"),
                BigDecimal.ZERO,
                MARKET
            )));
        Assertions.assertEquals(
            "quoted_price 40000.00 is more than 2% away from market price 45000.00 (deviation: 11.11%)",
            ex.getMessage());
    }

    // --- Rule 6: daily trading limit ---
    @Test @DisplayName("order exactly at daily limit should pass")
    void testDailyLimitExact() {
        OrderValidator validator = new OrderValidator();
        Assertions.assertDoesNotThrow(() -> validator.validate(new Order(
            "C001",
            "buy",
            new BigDecimal("5.0"),
            new BigDecimal("45000.00"),
            new BigDecimal("500000.00"),
            MARKET
        )));
    }

    @Test @DisplayName("second order pushing total over limit should fail")
    void testDailyLimitExceeded() {
        OrderValidator validator = new OrderValidator();
        // first order: 2.0
        validator.validate(new Order(
            "C001",
            "buy",
            new BigDecimal("2.0"),
            new BigDecimal("45000.00"),
            new BigDecimal("200000.00"),
            MARKET
        ));
        // second order: 4.0 → total 6.0 → fail, remaining = 3.0
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
            () -> validator.validate(new Order(
                "C001",
                "buy",
                new BigDecimal("4.0"),
                new BigDecimal("45000.00"),
                new BigDecimal("200000.00"),
                MARKET
            )));
        Assertions.assertEquals(
            "daily limit exceeded: remaining allowance is 3.0 baht-weight but order requires 4.0",
            ex.getMessage());
    }

    @Test @DisplayName("reset daily totals allows trading again")
    void testResetDailyTotals() {
        OrderValidator validator = new OrderValidator();
        // C001 uses full limit
        validator.validate(new Order(
            "C001",
            "buy",
            new BigDecimal("5.0"),
            new BigDecimal("45000.00"),
            new BigDecimal("500000.00"),
            MARKET
        ));
        // reset (new day)
        validator.resetDailyTotals();
        // C001 can trade again
        Assertions.assertDoesNotThrow(() -> validator.validate(new Order(
            "C001",
            "buy",
            new BigDecimal("5.0"),
            new BigDecimal("45000.00"),
            new BigDecimal("500000.00"),
            MARKET
        )));
    }
}
