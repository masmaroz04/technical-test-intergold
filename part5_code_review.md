# Part 5: Code Review — batch_processor.py

---

## สิ่งที่ดีในโค้ด

- **balance_lock (บรรทัด 6):** มี threading.Lock() และตั้งใจใช้กับการอัปเดต balance — pattern ถูกต้อง แค่ scope ยังแคบเกินไป
- **Price slippage check (บรรทัด 40–42):** เช็คว่า price ที่ส่งมาห่างจาก market ไม่เกิน 5% เป็น business rule ที่ดี ป้องกัน stale/manipulated price
- **แยก function ชัดเจน:** `process_batch_orders`, `process_single_order`, `get_batch_summary` แยก concern ออกจากกัน อ่านง่าย test ได้ง่าย
- **Catch unknown order_type (บรรทัด 72–73):** มี else clause คืน error แทนที่จะ silent fail
- **Docstrings ครบ:** ทุก function มี docstring อธิบาย contract

---

## ปัญหาที่พบ

### Critical

**1. Race Condition — อ่าน balance นอก lock (บรรทัด 44–48)**

```python
balance = customer_balances[customer_id]   # อ่านนอก lock ← ปัญหา

if order_type == "buy":
    cost = quantity * price
    if balance >= cost:                    # เช็คจากค่าเก่า
        with balance_lock:
            customer_balances[customer_id] = customer_balances[customer_id] - cost
```

Thread A และ Thread B อ่าน balance เดิมพร้อมกัน ผ่านเงื่อนไขทั้งคู่ แล้วหักเงินซ้ำกัน — balance ติดลบได้

**แก้ไข:** ย้ายการอ่านและเช็คเข้าไปใน `with balance_lock`

---

**2. Race Condition — order_log ไม่มี lock (บรรทัด 51–58, 63–70)**

```python
order_log.append({...})   # shared list ไม่มี lock
```

`.append()` ใน CPython อาจ atomic ได้บน GIL แต่ไม่ใช่ language guarantee — จะพังบน PyPy หรือถ้ามีการ iterate พร้อมกัน

**แก้ไข:** ครอบ order_log.append ด้วย balance_lock หรือสร้าง log_lock แยก

---

**3. ไม่ validate key ใน order dict (บรรทัด 34–36)**

```python
order_type = order["type"]      # KeyError ถ้าไม่มี key
quantity = order["quantity"]
price = order["price"]
```

ถ้า order ที่ส่งมามี field ขาด จะ crash ทั้ง batch แทนที่จะ reject แค่ order นั้น

**แก้ไข:** ใช้ `.get()` และ return `{"status": "error", "reason": "missing field"}`

---

**4. ไม่เช็คว่า customer_id มีอยู่ (บรรทัด 44, 62)**

```python
balance = customer_balances[customer_id]   # KeyError ถ้าไม่มี customer
```

**แก้ไข:** เช็คก่อนว่า customer_id อยู่ใน customer_balances

---

### Major

**5. ใช้ float แทน Decimal (บรรทัด 10–11, 38–39)**

```python
"C001": 500000.00,      # float
cost = quantity * price # float arithmetic
```

`from decimal import Decimal` มีอยู่แล้วบรรทัด 2 แต่ไม่ได้ใช้เลย float ทำให้เกิด precision error เช่น `0.1 + 0.2 = 0.30000000000000004` — รับไม่ได้สำหรับระบบการเงิน

**แก้ไข:** ใช้ `Decimal` ทุกที่ที่เกี่ยวกับตัวเลขการเงิน

---

**6. Market price ดึงครั้งเดียวทั้ง batch (บรรทัด 24)**

```python
market_price = get_market_price()   # ดึงแค่ครั้งเดียว
for order in orders:                # ใช้ราคาเดิมทุก order
```

ราคาทองเปลี่ยนตลอดเวลา order ท้าย batch อาจ validate กับราคาเก่าหลายวินาที

**แก้ไข:** ดึง market price ต่อ order หรือกำหนด timestamp expiry ของ batch

---

**7. ไม่ validate quantity (บรรทัด 35)**

```python
quantity = order["quantity"]   # ไม่เช็คว่าบวก ไม่เป็น 0
```

quantity ติดลบบน buy order จะ *เพิ่ม* balance แทนที่จะหัก

**แก้ไข:** เช็คว่า quantity > 0 ก่อนประมวลผล

---

**8. Sell ไม่เช็ค gold balance (บรรทัด 60–70)**

```python
elif order_type == "sell":
    revenue = quantity * price
    with balance_lock:
        customer_balances[customer_id] += revenue   # เพิ่มเงินทันที
```

ไม่มีการตรวจสอบว่าลูกค้ามีทองจริงหรือเปล่า — ขายทองที่ไม่มีอยู่ได้ (naked short selling)

**แก้ไข:** เพิ่ม gold_balances store และเช็คก่อน sell

---

**9. Division by zero ถ้า market_price เป็น 0 (บรรทัด 40)**

```python
price_diff = abs(price - market_price) / market_price   # ZeroDivisionError
```

ถ้า API คืน 0 หรือ fail จะ crash

**แก้ไข:** validate ว่า market_price > 0 ก่อนคำนวณ

---

### Minor

**10. Import Decimal แต่ไม่ได้ใช้ (บรรทัด 2)**

```python
from decimal import Decimal   # import แต่ไม่ใช้
```

ทำให้เข้าใจผิดว่าโค้ดใช้ Decimal อยู่แล้ว

---

**11. order_log ไม่มีขีดจำกัด (บรรทัด 13)**

```python
order_log = []   # โตไปเรื่อยๆ ไม่มีวันหมด
```

ระบบที่รันนานๆ จะกิน memory จนหมด ควรใช้ persistent store หรือ rotate log

---

**12. get_batch_summary ไม่นับ error (บรรทัด 77–78)**

```python
filled   = [r for r in results if r["status"] == "filled"]
rejected = [r for r in results if r["status"] == "rejected"]
# status == "error" หายไปเงียบๆ
```

`total_orders` จะไม่ตรงกับ `filled + rejected` ถ้ามี error — สับสน

---

## สรุป

| # | ระดับ | ปัญหา | บรรทัด |
|---|-------|-------|--------|
| 1 | Critical | TOCTOU race: อ่าน balance นอก lock | 44–48 |
| 2 | Critical | order_log ไม่มี lock | 51–58, 63–70 |
| 3 | Critical | ไม่ validate key ใน order dict | 34–36 |
| 4 | Critical | ไม่เช็ค customer_id | 44, 62 |
| 5 | Major | ใช้ float แทน Decimal | 10–11, 38–39 |
| 6 | Major | Market price stale ทั้ง batch | 24 |
| 7 | Major | ไม่ validate quantity | 35 |
| 8 | Major | Sell ไม่เช็ค gold balance | 60–70 |
| 9 | Major | Division by zero จาก market_price | 40 |
| 10 | Minor | Import Decimal ไม่ได้ใช้ | 2 |
| 11 | Minor | order_log ไม่มีขีดจำกัด | 13 |
| 12 | Minor | get_batch_summary ไม่นับ error | 77–78 |

**ขอให้แก้ Critical และ Major ก่อน merge — โดยเฉพาะข้อ 1 (race condition) และข้อ 5 (float) ซึ่งอาจทำให้ลูกค้าสูญเสียเงินได้โดยตรง**
