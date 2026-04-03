import java.math.BigDecimal;

/**
 * Represents a gold trading order submitted by a customer.
 */
public class Order {
    private String customerId;
    private String orderType;
    private BigDecimal quantity;
    private BigDecimal quotedPrice;
    private BigDecimal balance;
    private BigDecimal marketPrice;

    public Order(String customerId, String orderType, BigDecimal quantity, BigDecimal quotedPrice, BigDecimal balance, BigDecimal marketPrice) {
        this.customerId  = customerId;
        this.orderType   = orderType;
        this.quantity    = quantity;
        this.quotedPrice = quotedPrice;
        this.balance     = balance;
        this.marketPrice = marketPrice;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getQuotedPrice() {
        return quotedPrice;
    }

    public void setQuotedPrice(BigDecimal quotedPrice) {
        this.quotedPrice = quotedPrice;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getMarketPrice() {
        return marketPrice;
    }

    public void setMarketPrice(BigDecimal marketPrice) {
        this.marketPrice = marketPrice;
    }
}
