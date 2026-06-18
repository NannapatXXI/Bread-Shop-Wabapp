package com.breadShop.XXI.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.entity.EmailOtp;

// Repository สำหรับจัดการข้อมูล EmailOtp โดยใช้ Spring Data JPA | reviewed by peak
public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

   // ใช้ตอน verify OTP
   Optional<EmailOtp> findByTokenAndUsedFalse(String token);

   // ใช้ตอน reset password หลัง verify ผ่านแล้ว
   Optional<EmailOtp> findByTokenAndUsedTrue(String token);

   // กัน spam / resend
   void deleteByEmailAndPurposeAndUsedFalse(String email, String purpose);

   // cleanup OTP หมดอายุ
   void deleteByExpiresAtBefore(LocalDateTime time);

   @Modifying
    @Transactional
    @Query("DELETE FROM EmailOtp e WHERE e.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
