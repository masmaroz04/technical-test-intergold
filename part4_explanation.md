# Part 4: Explain Your Decisions

---

## 1. Trade-offs

### Fail-fast แทน Collect-all-errors
เลือกใช้ fail-fast คือเจอ error แรกแล้วหยุดทันที แทนที่จะเก็บ error ทั้งหมดแล้วส่งคืนพร้อมกัน

**ข้อดี:** โค้ดสั้นกว่า อ่านง่ายกว่า ไม่ต้องบริหาร error list
**ข้อเสีย:** ลูกค้าเห็น error ครั้งละ 1 อย่าง ต้องส่ง request ซ้ำหลายรอบถ้าผิดหลายจุด

ตัวอย่าง:
```
ลูกค้าส่ง order ที่ผิด 3 อย่าง

fail-fast:
  ครั้งที่ 1 → "order_type must be 'buy' or 'sell'"
  แก้ → ส่งใหม่ → "quantity must be positive"
  แก้ → ส่งใหม่ → "insufficient balance"

collect-all:
  ครั้งที่ 1 → เห็นทั้ง 3 error พร้อมกัน → แก้ครั้งเดียวจบ
```

---

### ใส่ balance และ marketPrice ใน Order model
เลือกให้ Order object ถือ balance และ marketPrice แทนที่จะให้ validator ดึงจากภายนอก

**ข้อดี:** ง่าย ไม่ต้องต่อ DB หรือ API ตรง assessment
**ข้อเสีย:** ใน production จริง ลูกค้าสามารถส่งค่า balance มาเองได้ ซึ่งไม่น่าเชื่อถือ

ตัวอย่าง:
```
ตอนนี้ (assessment):
  ลูกค้าส่ง balance มาใน request → validator เชื่อค่านั้นเลย

Production จริง:
  validator ดึง balance จาก DB ตาม customerId
  validator ดึง marketPrice จาก market feed API
  → ลูกค้าปลอมค่าไม่ได้
```

---

### ไม่ใช้ Framework อื่นๆ เช่น Spring , Spring boot
เลือกใช้ Java + Maven โดยไม่มี Spring หรือ framework อื่น — ใช้ Maven เพื่อจัดการ dependency และรัน unit test เท่านั้น

**ข้อดี:** setup ง่าย เหมาะกับขนาด project นี้
**ข้อเสีย:** ถ้า project ใหญ่ขึ้นต้องจัดการเอง เช่น dependency injection, scheduling, logging

ตัวอย่าง:
```
ตอนนี้ (Java):
  ต้องเรียก validator.resetDailyTotals() เองทุกวัน

Spring Boot:
  @Scheduled(cron = "0 0 0 * * *")  → reset อัตโนมัติทุกเที่ยงคืน
```

---

## 2. Alternatives

### Collect-all-errors
ตอนแรกทำ ValidationResult object ที่เก็บ error list แต่สุดท้ายเปลี่ยนเป็น fail-fast ด้วย IllegalArgumentException เพราะ
- โค้ดสั้นกว่ามาก
- ไม่ต้องสร้าง class เพิ่ม
- สำหรับ order validation การเห็น error ครั้งละอย่างก็เพียงพอ

### เก็บ dailyTotals ใน DB หรือ Redis
พิจารณาการเก็บ daily trading history ใน DB เพื่อให้ข้อมูลคงอยู่หลัง restart แต่โจทย์ระบุให้ใช้ in-memory ได้ จึงใช้ HashMap แทน

### แยก DailyLimitService ออกมา
พิจารณาแยก daily limit logic ออกเป็น class ต่างหาก แต่เนื่องจาก logic ไม่ซับซ้อน การรวมไว้ใน OrderValidator ทำให้อ่านง่ายกว่า

---

## 3. Debugging Process (Part 1)

ดูโค้ดโดยเรียงความสำคัญจากความเสียหายที่เกิดขึ้นได้

**1. SQL Injection — ร้ายแรงที่สุด**
เห็น string concatenation ใน query ทันที ผู้โจมตีสามารถ drop table หรือดึงข้อมูลลูกค้าทั้งหมดได้

```python
# ผิด
"SELECT * FROM customers WHERE id = " + customer_id

# ถูก
"SELECT * FROM customers WHERE id = ?", (customer_id,)
```

**2. Race Condition**
SELECT แล้ว UPDATE แยกกัน ถ้ามี 2 request พร้อมกัน ทั้งคู่อ่าน balance เดิมแล้วหักเงินซ้ำกัน แก้ด้วย atomic UPDATE

**3. Float Arithmetic**
ใช้ float คำนวณเงิน ทำให้ได้ผลลัพธ์ที่ผิดพลาด เช่น 0.1 + 0.2 = 0.30000000000000004 แก้ด้วย Decimal

**4. Naked Short Selling**
ไม่เช็ค gold_balance ก่อน sell ลูกค้าขายทองที่ไม่มีอยู่จริงได้

**5. Error Handling**
ไม่มี rollback เมื่อเกิด error ข้อมูลใน DB อาจ inconsistent

---

## 4. Evolution

### สิ่งที่ต้องเปลี่ยน

**dailyTotals → Redis/Database**
เก็บค่าใน redis โดยกำหนดวันหมดอายุ 1 วันต่อ user

**balance/marketPrice → ดึงจาก DB และ Market Feed API**
ไม่ให้ลูกค้าส่งค่าเองได้ validator ต้องดึงข้อมูลจริงเสมอ

**Async Processing**
orders หลายพันต่อวินาทีไม่ควร validate แบบ synchronous ควรใช้ message queue เช่น Kafka

**Logging และ Monitoring**
ทุก validation failure ต้องมี log พร้อม timestamp, customerId, reason เพื่อ audit

**resetDailyTotals → Scheduled Job**
ต้อง reset อัตโนมัติทุกเที่ยงคืน ไม่ใช่เรียก manual

---

## 5. Tools

ใช้ Claude (AI) ช่วยในส่วนต่อไปนี้:

- **Part 1:** ช่วย review โค้ดที่มีปัญหาและอธิบาย concept เช่น race condition, SQL injection
- **Part 2-3:** ช่วย structure โค้ดและ suggest pattern เช่น fail-fast
- **Unit tests:** ช่วย generate test cases 
**วิธี verify ว่าถูกต้อง:**
- รัน unit test 17 cases ผ่านทั้งหมด (`mvn test`)
- คำนวณ deviation และ spread ด้วยมือเพื่อเทียบกับผลลัพธ์
- อ่านและเข้าใจทุกบรรทัดก่อน commit ไม่ใช้โค้ดที่ไม่เข้าใจ
