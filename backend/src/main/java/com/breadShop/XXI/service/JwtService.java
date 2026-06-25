package com.breadShop.XXI.service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

// สำหรับการจัดการ JWT token reviewed by peak
@Service
public class JwtService {

    // Secret key ต้องเข้ารหัส Base64 และมีความยาว >= 256 bit (32 bytes)
    // ตัวอย่างใน application.properties:
    // app.jwtSecret=VGhpcyBpcyBhIHZlcnkgc2VjdXJlIHNlY3JldCBrZXkgZm9yIEpXVA==
    @Value("${app.jwtSecret}")
    private String secretKey;

    // เวลา token หมดอายุ (ms)
    @Value("${app.jwtExpirationMs}")
    private long jwtExpirationMs;

    // ------------------- Extract Claim -------------------
   /**
    * ดึง email (หรือ username) จาก JWT token โดยใช้ฟังก์ชัน generic extractClaim
    * @param token JWT token ที่ต้องการดึงข้อมูล
    * @return email หรือ username ที่ถูกเก็บใน claim "sub" (subject) ของ token
    */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // ฟังก์ชัน generic สำหรับดึงค่า claim ใดๆ
    /**
     * ฟังก์ชัน generic สำหรับดึงค่า claim ใดๆ จาก JWT token โดยใช้ resolver function ที่รับ Claims และคืนค่า T
     * @param <T> ประเภทของข้อมูลที่ต้องการดึงจาก claim
     * @param token JWT token ที่ต้องการดึงข้อมูล
     * @param resolver ฟังก์ชันที่รับ Claims และคืนค่า T ซึ่งจะถูกใช้ในการดึงข้อมูลจาก claim ที่ต้องการ
     * @return ค่าที่ได้จากการใช้ resolver function กับ Claims ที่ดึงมาจาก token
     */
    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    // ------------------- Generate Token -------------------
    /**
     * สร้าง JWT token โดยรับ UserDetails ซึ่งจะดึง username/email และ role มาเก็บใน claim ของ token
     * @param userDetails ข้อมูลผู้ใช้ที่ต้องการสร้าง token โดยจะดึง username/email และ role มาเก็บใน claim ของ token
     * @return JWT token ที่ถูกสร้างขึ้นโดยมีข้อมูล username/email ใน claim "sub" และ role ใน claim "roles" พร้อมกับเวลาหมดอายุและ signature ที่ถูกเซ็นด้วย secret key
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        // เก็บ role ไว้ใน claim
        claims.put("roles", userDetails.getAuthorities());

        // สร้าง JWT token
        return Jwts.builder()
                .setClaims(claims) // ใส่ custom claims
                .setSubject(userDetails.getUsername()) // ใส่ username/email
                .setIssuedAt(new Date()) // เวลาออก token
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs)) // หมดอายุ
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // เซ็นด้วย key HS256
                .compact();
    }

    // ------------------- Validate Token -------------------
    /**
     * ตรวจสอบว่า JWT token ถูกต้องและยังไม่หมดอายุ โดยเปรียบเทียบ email ที่ดึงจาก token กับ username ของ UserDetails และตรวจสอบว่า token ยังไม่หมดอายุ
     * @param token JWT token ที่ต้องการตรวจสอบ
     * @param user ข้อมูลผู้ใช้ที่ต้องการเปรียบเทียบกับข้อมูลใน token โดยจะใช้ username/email ของ user ในการเปรียบเทียบกับข้อมูลใน claim "sub" ของ token
     * @return true ถ้า email ใน token ตรงกับ username ของ user และ token ยังไม่หมดอายุ, false ถ้าไม่ตรงหรือหมดอายุ
     */
    public boolean isTokenValid(String token, UserDetails user) {
        String email = extractEmail(token);
        return email.equals(user.getUsername()) && !isTokenExpired(token);
    }

    // ตรวจว่า token ยังไม่หมดอายุ โดยไม่ต้องโหลด UserDetails
    // หมายเหตุ: signature ถูกตรวจโดย JJWT อัตโนมัติตอน parse ก่อนหน้านี้แล้ว
    public boolean isTokenNotExpired(String token) {
        return !isTokenExpired(token);
    }

    // ดึง role จาก JWT claims (format: [{authority=ROLE_USER}])
    /**
     * ดึง role ของผู้ใช้จาก JWT token โดยจะดึงข้อมูลจาก claim "roles" ซึ่งเป็น list ของ authorities ที่ถูกเก็บใน token และคืนค่า role แรกที่พบ ถ้าไม่มี role จะคืนค่า "ROLE_USER" เป็น default
     * @param token JWT token ที่ต้องการดึงข้อมูล role โดยจะดึงข้อมูลจาก claim "roles" ซึ่งเป็น list ของ authorities ที่ถูกเก็บใน token และคืนค่า role แรกที่พบ ถ้าไม่มี role จะคืนค่า "ROLE_USER" เป็น default
     * @return role ของผู้ใช้ที่ดึงมาจาก token ถ้ามี, หรือ "ROLE_USER" ถ้าไม่มี role ใน token
     */
    @SuppressWarnings("unchecked")
    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        var roles = (java.util.List<?>) claims.get("roles");
        if (roles != null && !roles.isEmpty()) {
            Object first = roles.get(0);
            if (first instanceof java.util.Map<?, ?> map) {
                Object authority = map.get("authority");
                if (authority != null) return authority.toString();
            }
        }
        return "ROLE_USER";
    }

    /**
     * ตรวจสอบว่า JWT token หมดอายุหรือไม่ โดยเปรียบเทียบเวลาหมดอายุที่ดึงมาจาก token กับเวลาปัจจุบัน ถ้าเวลาหมดอายุก่อนเวลาปัจจุบันแสดงว่า token หมดอายุแล้ว
     * @param token JWT token ที่ต้องการตรวจสอบว่าหมดอายุหรือไม่ โดยจะดึงเวลาหมดอายุจาก claim "exp" ของ token และเปรียบเทียบกับเวลาปัจจุบัน
     * @return true ถ้า token หมดอายุแล้ว (เวลาหมดอายุก่อนเวลาปัจจุบัน), false ถ้า token ยังไม่หมดอายุ (เวลาหมดอายุหลังเวลาปัจจุบัน)
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * ดึงเวลาหมดอายุจาก JWT token โดยใช้ฟังก์ชัน extractClaim กับ Claims::getExpiration เพื่อดึงค่า claim "exp" ซึ่งเป็นเวลาหมดอายุของ token และคืนค่าเป็น Date object
     * @param token JWT token ที่ต้องการดึงเวลาหมดอายุ โดยจะใช้ฟังก์ชัน extractClaim กับ Claims::getExpiration เพื่อดึงค่า claim "exp" ซึ่งเป็นเวลาหมดอายุของ token และคืนค่าเป็น Date object
     * @return เวลาหมดอายุของ token ที่ดึงมาจาก claim "exp" ใน token โดยคืนค่าเป็น Date object
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // ------------------- Internal Helpers -------------------
    /**
     * ดึง Claims ทั้งหมดจาก JWT token โดยใช้ Jwts.parserBuilder() เพื่อสร้าง parser ที่ตั้งค่า signing key สำหรับตรวจสอบ signature ของ token และเรียก parseClaimsJws(token) เพื่อดึงข้อมูล claims จาก token ถ้า signature ไม่ถูกต้องหรือ token หมดอายุจะเกิด exception ขึ้น
     * @param token JWT token ที่ต้องการดึงข้อมูล claims โดยจะใช้ Jwts.parserBuilder() เพื่อสร้าง parser ที่ตั้งค่า signing key สำหรับตรวจสอบ signature ของ token และเรียก parseClaimsJws(token) เพื่อดึงข้อมูล claims จาก token ถ้า signature ไม่ถูกต้องหรือ token หมดอายุจะเกิด exception ขึ้น
     * @return Claims object ที่ดึงมาจาก token ซึ่งประกอบด้วยข้อมูลต่างๆ ที่ถูกเก็บใน token เช่น subject, roles, issuedAt, expiration เป็นต้น
     */
    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSigningKey()) // ใส่ key สำหรับตรวจสอบ signature
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * สร้าง Key object สำหรับใช้ในการเซ็นและตรวจสอบ JWT token โดยแปลง secretKey ที่เป็น Base64 string เป็น byte[] 
     * และใช้ Keys.hmacShaKeyFor() เพื่อสร้าง Key object ที่เหมาะสมสำหรับการใช้กับ HS256 algorithm ซึ่งต้องการ key ที่มีความยาวอย่างน้อย 256 bit (32 bytes)
     * @return Key object ที่ถูกสร้างขึ้นจาก secretKey ซึ่งสามารถใช้ในการเซ็นและตรวจสอบ JWT token ด้วย HS256 algorithm
     */
    private Key getSigningKey() {
        // แปลง Base64 string เป็น byte[]
        // สำคัญ: key ต้องมีความยาว >= 256 bit สำหรับ HS256
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
