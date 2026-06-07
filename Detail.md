Controller = รับ/ส่ง HTTP
Service = logic
Repository = DB
MailService = ส่งเมลอย่างเดียว


accessToken อายุ 15 นาที
refresh token  อายุ 7 วัน หมด = logout

list สิ่งที่ต้องแก้
- แก้ database มีปัญหาการ reset pass ที่ซ้อนกับการ login หน้าเว็บ กับ google  ทำตารางบอกว่า login เข้ามาด้วยอะไร (pass)
-แก้ การแยก path การดดเข้่าถึงแบบ private และ public
- แยกเส้น api แบบที่ public และในเว็บให้ส่ง JWT
- แก้ auth control ให้อะไรที่ยุ่งเกี่ยวกับข้อมูลเอามาไว้ใน auth sevice
-แก้ auth service ไม่ใช้ ResponseEntity ให้ auth controller เป็นคนคืน HTTP ไป


list debug
-เวลาในการเข้าถึงรหัส OTP
-การกด enter เข้าหน้าต่างๆแทนการกดปุ่ม เช่น หน้า forgotpass
-เวลากดส่ง OTP แล้วอย่าให้กดอีกซ้ำ
-เวลากรอก OTP เสร็จแล้วมันไม่ถูกให้บอกด้วยแล้วก็เอาปุ่ม sead ออกเป็นปุ่มการส่ง OTP ใหม่

next-step
-ทำเรื่อง token กับ cookie ให้เสร็จ แล้วก็เส้น Api และการจัดการ exception ต่างๆ
-แก้เส้น Api หน้าเว็บให้ไปร่วมกันเพื่อให้รู้ว่า token หมดอายุ
-เอาข้อมูล order ออกมาโชว์
-แก้ design mail ที่ส่งเวลาจะส่ง OTP เปลี่ยนเมลด้วยทำเป็นเมลของร้านโดยเฉพาะ
-ทำการแจ้งเตือนส่งไปทางไลน์ เมื่อมีคนสั่งซื้อสินค้า
-ทำไลน์ OA แล้วให้ลูกค้าสามารถเชื่อมต่อกับ web ได้เพื่อติดตามหรือเช็ึคสินค้าผ่าน line 
-ทำใบกำกับภาษีให้ลูกค้า
-ให้ลูกค้ากำหนดวันที่จะรับสินค้าได้



Successful
- accessToken refresh token  cookie เสร็จแล้ว

Code ในส่วนที reviewd ไปแล้ว
- folder service


test api 
- pass line order in admin


สิ่งที่ควรทำต่อ (ยังไม่ทำหรือทำบางส่วน)
-Logout จริง — ลบ access_token / refresh_token + setUser(null) #pass 
-เหลือ AuthProvider ชั้นเดียว (เอาออกจาก layout ย่อย)
-/me ใช้ AbortController กัน race
-Dashboard user อย่าเรียก getorders() จาก admin ถ้า user ธรรมดาเข้าได้
-เพิ่ม field sku ใน Product ถ้าต้องการ snapshot ใน order line


-กลับมาทำหน้า editProduct ตอนนี้ api การดึงข้อมูลจาก id ใช้ได้แล้ว



✅ รู้แล้ว (จาก project นี้)
   JWT, Cookie Auth, Refresh Token
   REST API + ApiResponse standard
   Layered Architecture (Controller-Service-Repository)
   ORM (JPA), Dependency Injection
   Flyway, CORS, DTO, Environment Variables

🎯 ต่อไปที่ควรศึกษา
   Docker — package app ให้รันที่ไหนก็ได้
   CI/CD — auto deploy เมื่อ push code
   Testing — Unit test, Integration test
   Index ใน Database — ทำให้ query เร็วขึ้น
   Caching (Redis) — ลด load DB
   HTTPS/TLS — production ต้องมี



#Explain for me
-Flyway = เอาไว้เก็บว่า database มีอะไรเปลี่ยนแปลงไป
-การแย่ง layout 
    Controller  →  รับ HTTP request / ส่ง HTTP response
    Service     →  logic ทางธุรกิจ
    Repository  →  คุยกับ database


