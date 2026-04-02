import sqlite3
from decimal import Decimal, ROUND_HALF_UP
from exceptions import InsufficientFundsError, InsufficientGoldError, CustomerNotFoundError


# ============================================================
# ORIGINAL CODE
# ============================================================
#
# import sqlite3
# def process_gold_order(customer_id, order_type, quantity, price):
#     conn = sqlite3.connect("trading.db")
#     # Get customer balance
#     cursor = conn.execute(
#         "SELECT balance, name FROM customers WHERE id = " + str(customer_id)
#     )
#     customer = cursor.fetchone()
#     balance = customer[0]
#     name = customer[1]
#     if order_type == "buy":
#         total_cost = quantity * price
#         if balance >= total_cost:
#             new_balance = balance - total_cost
#             conn.execute(
#                 f"UPDATE customers SET balance = {new_balance} WHERE id = {customer_id}"
#             )
#             conn.execute(
#                 f"INSERT INTO orders (customer_id, type, quantity, price, total)"
#                 f" VALUES ({customer_id}, '{order_type}', {quantity}, {price}, {total_cost})"
#             )
#             conn.commit()
#             print(f"Order successful for {name}. New balance: {new_balance}")
#             return {"status": "success", "balance": new_balance}
#         else:
#             print("Insufficient balance")
#             return {"status": "failed", "reason": "insufficient balance"}
#     elif order_type == "sell":
#         total_revenue = quantity * price
#         new_balance = balance + total_revenue
#         conn.execute(
#             f"UPDATE customers SET balance = {new_balance} WHERE id = {customer_id}"
#         )
#         conn.execute(
#             f"INSERT INTO orders (customer_id, type, quantity, price, total)"
#             f" VALUES ({customer_id}, '{order_type}', {quantity}, {price}, {total_revenue})"
#         )
#         conn.commit()
#         print(f"Sell order for {name}. New balance: {new_balance}")
#         return {"status": "success", "balance": new_balance}
#     return None


# ============================================================
# FIXED CODE
# ============================================================

def process_gold_order(customer_id: int, order_type: str, quantity, price):
    """
    Process a gold buy or sell order for a customer.

    Args:
        customer_id: customer ID
        order_type:  "buy" or "sell"
        quantity:    amount of gold (baht-weight)
        price:       price per baht-weight (THB)

    Returns:
        dict: {"status": "success", ...}

    Raises:
        ValueError:             invalid input
        CustomerNotFoundError:  customer not found
        InsufficientFundsError: not enough balance to buy
        InsufficientGoldError:  not enough gold to sell
    """
    conn = sqlite3.connect("trading.db")

    # [Fix] validate input
    if order_type not in ("buy", "sell"):
        raise ValueError("invalid order_type: " + order_type)

    # [Fix] convert to Decimalto avoid float precision errors
    quantity = Decimal(str(quantity))
    price    = Decimal(str(price))

    if quantity <= 0:
        raise ValueError("quantity must be greater than 0")
    if price <= 0:
        raise ValueError("price must be greater than 0")

    try:
        # [Fix] use ? to prevent SQL injection
        customer = conn.execute(
            "SELECT id, name, balance, gold_balance FROM customers WHERE id = ?",
            (customer_id,)
        ).fetchone()

        # [Fix] check customer exists?
        if customer is None:
            raise CustomerNotFoundError(f"customer not found: id={customer_id}")

        if order_type == "buy":
            # [Fix] formatter #.##
            total_cost = (quantity * price).quantize(
                Decimal("0.00"), rounding=ROUND_HALF_UP
            )

            # [Fix] atomic UPDATE: read + check + write in one SQL statement prevents race condition
            rows_updated = conn.execute(
                """UPDATE customers
                   SET balance = balance - ?
                   WHERE id = ? AND balance >= ?""",
                (str(total_cost), customer_id, str(total_cost))
            ).rowcount

            # rowcount = 0 means WHERE condition failed (not enough balance)
            if rows_updated == 0:
                raise InsufficientFundsError("insufficient balance")

            # [Fix] INSERT also uses ?
            conn.execute(
                """INSERT INTO orders (customer_id, type, quantity, price, total)
                   VALUES (?, ?, ?, ?, ?)""",
                (customer_id, order_type, str(quantity), str(price), str(total_cost))
            )

            print(f"Order successful for {customer[1]}. New balance: {customer[2]}")
            conn.commit()
            return {"status": "success", "total_cost": str(total_cost)}

        elif order_type == "sell":
            # [Fix] formatter #.##
            total_revenue = (quantity * price).quantize(
                Decimal("0.00"), rounding=ROUND_HALF_UP
            )

            # [Fix] check gold_balance before selling — prevents selling gold not owned
            rows_updated = conn.execute(
                """UPDATE customers
                   SET balance      = balance + ?,
                       gold_balance = gold_balance - ?
                   WHERE id = ? AND gold_balance >= ?""",
                (str(total_revenue), str(quantity), customer_id, str(quantity))
            ).rowcount

            # rowcount = 0 means WHERE condition failed (not enough gold_balance)
            if rows_updated == 0:
                raise InsufficientGoldError("insufficient gold balance")

            conn.execute(
                """INSERT INTO orders (customer_id, type, quantity, price, total)
                   VALUES (?, ?, ?, ?, ?)""",
                (customer_id, order_type, str(quantity), str(price), str(total_revenue))
            )


            print(f"Sell order for {customer[1]}. Total Revenue: {total_revenue}")
            conn.commit()
            return {"status": "success", "total_revenue": str(total_revenue)}

    # [Fix] rollback on any error — prevents partial data (e.g. balance deducted but no order recorded)
    except (InsufficientFundsError, InsufficientGoldError, CustomerNotFoundError):
        conn.rollback()
        raise
    except Exception:
        conn.rollback()
        raise
