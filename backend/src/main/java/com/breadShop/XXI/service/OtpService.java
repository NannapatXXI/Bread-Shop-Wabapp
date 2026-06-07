package com.breadShop.XXI.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.breadShop.XXI.dto.OtpResult;
import com.breadShop.XXI.entity.EmailOtp;
import com.breadShop.XXI.repository.EmailOtpRepository;

import jakarta.transaction.Transactional;
//สำหรับการจัดการ OTP ทั้งการสร้างและตรวจสอบ OTP โดยใช้ Spring Data JPA และ Spring Security  |  reviewd by peak
@Service
public class OtpService {

    private final EmailOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;

    public OtpService(
            EmailOtpRepository otpRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------ generate OTP ------------------
   @Transactional
    public OtpResult generateOtp(String email, String purpose) {

        String otp = String.valueOf(
            100000 + new SecureRandom().nextInt(900000)
        );

        EmailOtp entity = new EmailOtp();
        String token = UUID.randomUUID().toString();

        entity.setEmail(email);
        entity.setPurpose(purpose);
        entity.setToken(token);
        entity.setOtpHash(passwordEncoder.encode(otp));
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        entity.setUsed(false);
        entity.setAttemptCount(0);
       // entity.setCreatedAt(LocalDateTime.now());

        otpRepository.save(entity);

        return new OtpResult(token, otp);
    }


    // ------------------ verify OTP ------------------
    /**
     * ใช้ตอน verify OTP โดยจะเช็คว่า OTP ถูกต้องไหม หมดอายุหรือยัง และเกินจำนวนครั้งที่พยายามใส่ OTP ไหม
     * @param token เอา token ไปค้นหารหัส OTP ที่เก็บไว้
     * @param otpInput รหัส OTP ที่ผู้ใช้กรอกมา
     */
    @Transactional // เราจะใช้ @Transactional ในกรณีที่เราต้องการให้มันทำงานเสร็จพร้อมกันเพราะถ้าเสร็จไม่พร้อมกันอาจจะมีเรื่องข้อมูล
    //เข่นการอัพเดตสินค้าหลายๆรายการพร้อมกันแล้วเกิดปัญหาเรื่องข้อมูลไม่ตรงกันได้
    public void verifyOtp(String token, String otpInput) {
    
        EmailOtp otp = otpRepository
            .findByTokenAndUsedFalse(token)
            .orElseThrow(() -> new RuntimeException("OTP_NOT_FOUND"));
    
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otp.setUsed(true);
            throw new RuntimeException("OTP_EXPIRED");
        }
    
        if (!passwordEncoder.matches(otpInput, otp.getOtpHash())) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
    
            if (otp.getAttemptCount() >= 5) {
                otp.setUsed(true);
                throw new RuntimeException("OTP_LOCKED");
            }
    
            throw new RuntimeException("OTP_INVALID");
        }
        System.out.println("Before verify - used: " + otp.isUsed());
        otp.setVerified(true);
        System.out.println("After verify - used: " + otp.isUsed());
        
    }
    
     // ------------------ invalidate Token ------------------
     /**
      * ใช้ตอน reset password เพื่อเช็คว่า token นี้ถูกต้องไหม และเช็คส่า token นี้ผ่านการ verify OTP มาแล้วหรือยัง และหมดเวลาไปแล้วหรือยัง
      * @param token
      * @return เมลล์ที่เกี่ยวข้องกับ OTP นี้กลับไปเป็น String
      */
    @Transactional
    public String validateResetToken(String token) {

        EmailOtp otp = otpRepository
            .findByTokenAndUsedFalse(token)
            .orElseThrow(() -> new RuntimeException("TOKEN_INVALID"));

        if (!otp.isVerified()) {
            throw new RuntimeException("OTP_NOT_VERIFIED");
        }

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("TOKEN_EXPIRED");
        }

        return otp.getEmail();
    }
    // ------------------ invalidate Token ------------------
    /**
     * ใช้หลังจาก reset password สำเร็จ เพื่อบอกว่า token นี้ใช้แล้ว
     * @param token  เป็น string ที่ใช้ในการระบุ OTP ที่ต้องการจะ บอกว่าใช้ไปแล้ว
     */
    @Transactional
    public void invalidateToken(String token) {
        otpRepository.findByTokenAndUsedFalse(token)
            .ifPresent(otp -> otp.setUsed(true));
    }
}
