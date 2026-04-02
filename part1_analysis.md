# Part 1: วิเคราะห์โค้ดที่มีปัญหา

---

## ข้อ 1: ฟังก์ชันนี้ทำอะไร?

ฟังก์ชัน `process_gold_order` ทำหน้าที่ประมวลผลคำสั่งซื้อหรือขายทองคำของลูกค้า

โดยทำงานดังนี้:
1. เปิดการเชื่อมต่อฐานข้อมูล SQLite และดึงยอดเงินปัจจุบันของลูกค้า
2. ถ้าเป็นคำสั่ง **ซื้อ (buy)** — คำนวณราคารวม (`quantity x price`) เช็คว่าเงินพอไหม ถ้าพอก็หักเงินและบันทึกคำสั่งซื้อ
3. ถ้าเป็นคำสั่ง **ขาย (sell)** — คำนวณรายได้ (`quantity x price`) บวกเงินเข้าบัญชี และบันทึกคำสั่งขาย
4. คืนค่า `{"status": "success"}` เมื่อสำเร็จ, `{"status": "failed"}` เมื่อเงินไม่พอ หรือ `None` ถ้า order_type ไม่ถูกต้อง

---

## ข้อ 2: ปัญหาที่พบ

| # | ปัญหา | ด้าน |
|---|-------|------|
| 1 | SQL Injection | Security |
| 2 | ใช้ Float คำนวณเงิน | Correctness |
| 3 | Race Condition | Reliability |
| 4 | ขายทองโดยไม่มีทอง | Correctness |
| 5 | ไม่มี Error Handling | Reliability |

---

## ข้อ 3: ทำไมแต่ละปัญหาถึงสำคัญ?

### ปัญหาที่ 1: SQL Injection

โค้ดนำค่าที่รับมาจากภายนอกไปต่อใน SQL ตรงๆ:
```python
"SELECT balance FROM customers WHERE id = " + str(customer_id)
```
ผู้โจมตีส่ง `"1; DROP TABLE customers--"` เพื่อลบข้อมูลทั้งหมดได้ทันที
ในระบบการเงินจริง ยอดเงิน ประวัติธุรกรรม และข้อมูลส่วนตัวของลูกค้าถูกลบหรือแก้ไขได้

---

### ปัญหาที่ 2: ใช้ Float คำนวณเงิน

```python
total_cost = quantity * price
```
Float ไม่แม่นยำ เช่น `2.1 * 45000 = 94499.99999999999` แทนที่จะเป็น `94500.00`
ทำธุรกรรมหลายพันครั้ง ความผิดพลาดสะสม ยอดเงินคลาดเคลื่อน
ในระบบบัญชีจริงต้องแม่นยำทุกสตางค์

---

### ปัญหาที่ 3: Race Condition

```python
balance = customer[0]         # อ่าน
new_balance = balance - cost  # คำนวณ
conn.execute("UPDATE ...")    # เขียน — มีช่องว่างระหว่างอ่านกับเขียน!
```
ถ้าลูกค้ากดซื้อ 2 ครั้งพร้อมกัน ทั้งคู่อ่านยอดเดิมพร้อมกัน
ลูกค้าใช้เงินไป 180,000 บาท แต่บัญชีหักแค่ 90,000 บาท

---

### ปัญหาที่ 4: ขายทองโดยไม่มีทอง

```python
elif order_type == "sell":
    new_balance = balance + total_revenue
    # ไม่มีการเช็ค gold_balance เลย!
```
ใครก็ได้สามารถขายทอง 99,999 บาทน้ำหนักโดยไม่มีทองในบัญชี
และได้รับเงินหลายพันล้านบาทฟรี ทำให้บริษัทล้มละลายได้

---

### ปัญหาที่ 5: ไม่มี Error Handling

```python
conn = sqlite3.connect("trading.db") 
balance = customer[0]                 # crash ถ้า customer เป็น None!
```
- ถ้าลูกค้าไม่มีในระบบ → โปรแกรม crash ทันที
- ถ้า UPDATE สำเร็จแต่ INSERT ล้มเหลว → เงินหายแต่ไม่มีบันทึกธุรกรรม

---

## ข้อ 4: วิธีแก้ไข

### แก้ปัญหาที่ 1: ใช้ Parameterized Query

```python
# แทนการต่อ string ตรงๆ
conn.execute(
    "SELECT balance, name FROM customers WHERE id = ?",
    (customer_id,)
)
# ค่าที่รับมาถูกมองเป็นข้อมูลเท่านั้น ไม่ใช่คำสั่ง SQL
```

---

### แก้ปัญหาที่ 2: ใช้ Decimal แทน Float

```python
from decimal import Decimal, ROUND_HALF_UP

quantity   = Decimal(str(quantity))
price      = Decimal(str(price))
total_cost = (quantity * price).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
# ได้ผลลัพธ์แม่นยำเสมอ เช่น 94500.00
```

---

### แก้ปัญหาที่ 3: ใช้ Atomic UPDATE

```python
rows_updated = conn.execute(
    """UPDATE customers
       SET balance = balance - ?
       WHERE id = ? AND balance >= ?""",
    (total_cost, customer_id, total_cost)
).rowcount

if rows_updated == 0:
    raise InsufficientFundsError("ยอดเงินไม่เพียงพอ")
# อ่านและเขียนในคำสั่งเดียว ไม่มีช่องว่างให้ race condition เกิด
```

---

### แก้ปัญหาที่ 4: เช็ค gold_balance

```python
rows_updated = conn.execute(
    """UPDATE customers
       SET balance      = balance + ?,
           gold_balance = gold_balance - ?
       WHERE id = ? AND gold_balance >= ?""",
    (total_revenue, quantity, customer_id, quantity)
).rowcount

if rows_updated == 0:
    raise InsufficientGoldError("มีทองไม่เพียงพอสำหรับขาย")
```

---

### แก้ปัญหาที่ 5: ใช้ rollback

```python
try:
    customer = conn.execute(...).fetchone()
    if customer is None:
        raise CustomerNotFoundError(f"ไม่พบลูกค้า id: {customer_id}")
    # ... logic ทั้งหมด
    conn.commit()
except Exception:
    conn.rollback()  # ยกเลิกทุกอย่างถ้าเกิด error
    raise
```
