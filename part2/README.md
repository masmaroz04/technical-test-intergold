# Part 2: Order Validation Module

## Requirements
- Java 11 or higher

## How to Run

```bash
cd part2
javac src/*.java -d out
java -cp out Main
```

## Expected Output

```
=======================================================
Gold Order Validation Demo
Market price: 45,000 THB/baht-weight
=======================================================

1. Valid buy order
Result: VALID

2. Invalid order_type
Result: INVALID: [order_type must be 'buy' or 'sell', got: hold]

3. Quantity not multiple of 0.5
Result: INVALID: [quantity must be a multiple of 0.5 baht-weight, got: 1.3]

4. Negative quoted_price
Result: INVALID: [quoted_price must be positive]

5. Insufficient balance (C002 has 50,000 THB)
Result: INVALID: [insufficient balance: need 90000.00 THB but customer has 50000.00 THB]

6. Price too stale (> 2% from market)
Result: INVALID: [quoted_price 40000.00 is more than 2% away from market price 45000.00 (deviation: 11.11%)]

7. Valid sell order
Result: VALID

8. Null order
Result: INVALID: [order must not be null]
```
