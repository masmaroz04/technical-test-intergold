import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

public class OrderValidator {

    private static final BigDecimal MAX_PRICE_DEVIATION = new BigDecimal("0.02");
    private static final BigDecimal QUANTITY_INCREMENT  = new BigDecimal("0.5");
    private static final BigDecimal SPREAD_MARGIN       = new BigDecimal("0.005"); // 0.5%
    private static final BigDecimal DAILY_LIMIT         = new BigDecimal("5.0");   // baht-weight

    // in-memory daily totals per customer: customerId -> total quantity today
    private final Map<String, BigDecimal> dailyTotals = new HashMap<>();

    public void validate(Order order) {
        if (order == null)
            throw new IllegalArgumentException("order must not be null");

        validateOrderType(order);
        validateQuantity(order);
        validateQuotedPrice(order);
        validateBalance(order);
        validatePrice(order);
        validateDailyLimit(order);

        // all rules passed — update daily total
        String id = order.getCustomerId();
        BigDecimal current = dailyTotals.getOrDefault(id, BigDecimal.ZERO);
        dailyTotals.put(id, current.add(order.getQuantity()));
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

    // Rule 5: price check
    //   buy  → quoted_price must be within 2% of expected buy price (market + 0.5% spread)
    //   sell → quoted_price must be within 2% of market price
    private void validatePrice(Order order) {
        BigDecimal price  = order.getQuotedPrice();
        BigDecimal market = order.getMarketPrice();
        if (price == null || market == null) return;

        if ("buy".equals(order.getOrderType())) {
            BigDecimal buyPrice   = market.multiply(BigDecimal.ONE.add(SPREAD_MARGIN))
                                          .setScale(2, RoundingMode.HALF_UP);
            BigDecimal spreadAmt  = buyPrice.subtract(market).setScale(2, RoundingMode.HALF_UP);
            BigDecimal deviation  = price.subtract(buyPrice)
                                         .abs()
                                         .divide(buyPrice, 10, RoundingMode.HALF_UP);

            System.out.println("  [spread] buy price: " + buyPrice
                    + " THB  |  spread amount: " + spreadAmt + " THB");

            if (deviation.compareTo(MAX_PRICE_DEVIATION) > 0) {
                String pct = deviation.multiply(new BigDecimal("100"))
                                      .setScale(2, RoundingMode.HALF_UP) + "%";
                throw new IllegalArgumentException("quoted_price " + price
                        + " is more than 2% away from expected buy price " + buyPrice
                        + " (deviation: " + pct + ")");
            }
        } else {
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

    // Rule 6: daily trading limit — max 5 baht-weight per customer per day
    private void validateDailyLimit(Order order) {
        String id  = order.getCustomerId();
        if (id == null) return;

        BigDecimal used      = dailyTotals.getOrDefault(id, BigDecimal.ZERO);
        BigDecimal remaining = DAILY_LIMIT.subtract(used);

        if (order.getQuantity().compareTo(remaining) > 0)
            throw new IllegalArgumentException("daily limit exceeded: remaining allowance is "
                    + remaining + " baht-weight but order requires " + order.getQuantity());
    }

    // reset daily totals (e.g. called at start of new day)
    public void resetDailyTotals() {
        dailyTotals.clear();
    }
}
