import java.math.BigDecimal;
import java.math.RoundingMode;

public class OrderValidator {

    private static final BigDecimal MAX_PRICE_DEVIATION = new BigDecimal("0.02");
    private static final BigDecimal QUANTITY_INCREMENT  = new BigDecimal("0.5");

    public void validate(Order order) {
        if (order == null)
            throw new IllegalArgumentException("order must not be null");

        validateOrderType(order);
        validateQuantity(order);
        validateQuotedPrice(order);
        validateBalance(order);
        validatePriceFreshness(order);
    }

    // Rule 1: order_type must be "buy" or "sell"
    private void validateOrderType(Order order) {
        String type = order.getOrderType();
        if (type == null || (!type.equals("buy") && !type.equals("sell")))
            throw new IllegalArgumentException("order_type must be 'buy' or 'sell', got: " + type);
    }

    // Rule 2: quantity must be positive and a multiple of 0.5
    private void validateQuantity(Order order) {
        BigDecimal qty = order.getQuantity();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("quantity must be positive");

        BigDecimal remainder = qty.remainder(QUANTITY_INCREMENT)
                                  .setScale(2, RoundingMode.HALF_UP)
                                  .abs();
        if (remainder.compareTo(BigDecimal.ZERO) != 0)
            throw new IllegalArgumentException("quantity must be a multiple of 0.5 baht-weight, got: " + qty);
    }

    // Rule 3: quoted_price must be positive
    private void validateQuotedPrice(Order order) {
        BigDecimal price = order.getQuotedPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("quoted_price must be positive");
    }

    // Rule 4: buy orders require sufficient balance
    private void validateBalance(Order order) {
        if (!"buy".equals(order.getOrderType())) return;

        BigDecimal qty   = order.getQuantity();
        BigDecimal price = order.getQuotedPrice();
        if (qty == null || price == null) return;

        BigDecimal totalCost = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance   = order.getBalance() != null ? order.getBalance() : BigDecimal.ZERO;

        if (balance.compareTo(totalCost) < 0)
            throw new IllegalArgumentException("insufficient balance: need " + totalCost
                    + " THB but customer has " + balance + " THB");
    }

    // Rule 5: quoted_price must be within 2% of market price
    private void validatePriceFreshness(Order order) {
        BigDecimal price  = order.getQuotedPrice();
        BigDecimal market = order.getMarketPrice();
        if (price == null || market == null) return;

        BigDecimal deviation = price.subtract(market)
                                    .abs()
                                    .divide(market, 10, RoundingMode.HALF_UP);

        if (deviation.compareTo(MAX_PRICE_DEVIATION) > 0) {
            String pct = deviation.multiply(new BigDecimal("100"))
                                  .setScale(2, RoundingMode.HALF_UP) + "%";
            throw new IllegalArgumentException("quoted_price " + price
                    + " is more than 2% away from market price " + market
                    + " (deviation: " + pct + ")");
        }
    }
}
