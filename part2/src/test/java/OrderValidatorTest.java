import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assertions;
import java.math.BigDecimal;

@DisplayName("OrderValidator Tests")
class OrderValidatorTest {

    private static final BigDecimal MARKET = new BigDecimal("45000.00");
    private final OrderValidator validator = new OrderValidator();

    // --- Valid cases ---
    @Test @DisplayName("valid buy order should pass")
    void testValidBuyOrder() {
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
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
            () -> validator.validate(null));
        Assertions.assertEquals("order must not be null", ex.getMessage());
    }

    // --- Rule 1: order_type ---
    @Test @DisplayName("order_type = hold should fail")
    void testInvalidOrderType() {
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
        IllegalArgumentException ex = Assertions.assertThrows(IllegalArgumentException.class,
            () -> validator.validate(new Order(
                "C001",
                "buy",
                new BigDecimal("2.0"),
                new BigDecimal("45000.00"),
                new BigDecimal("50000.00"),
                MARKET
            )));
        Assertions.assertEquals(
            "insufficient balance: need 90000.00 THB but customer has 50000.00 THB",
            ex.getMessage());
    }

    @Test @DisplayName("exact balance (2.0 x 45000 = 90000) should pass")
    void testExactBalance() {
        Assertions.assertDoesNotThrow(() -> validator.validate(new Order(
            "C001",
            "buy",
            new BigDecimal("2.0"),
            new BigDecimal("45000.00"),
            new BigDecimal("90000.00"),
            MARKET
        )));
    }

    @Test @DisplayName("sell with zero balance should pass")
    void testSellSkipsBalanceCheck() {
        Assertions.assertDoesNotThrow(() -> validator.validate(new Order(
            "C001",
            "sell",
            new BigDecimal("2.0"),
            new BigDecimal("45000.00"),
            BigDecimal.ZERO,
            MARKET
        )));
    }

    // --- Rule 5: price freshness ---
    @Test @DisplayName("price > 2% from market should fail")
    void testPriceTooStale() {
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
            "quoted_price 40000.00 is more than 2% away from market price 45000.00 (deviation: 11.11%)",
            ex.getMessage());
    }

    @Test @DisplayName("price exactly 2% above market should pass")
    void testPriceExactly2Percent() {
        // 45000 x 1.02 = 45900
        Assertions.assertDoesNotThrow(() -> validator.validate(new Order(
            "C001",
            "buy",
            new BigDecimal("2.0"),
            new BigDecimal("45900.00"),
            new BigDecimal("200000.00"),
            MARKET
        )));
    }
}
