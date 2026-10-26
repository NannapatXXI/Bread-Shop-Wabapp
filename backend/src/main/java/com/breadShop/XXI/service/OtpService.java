package com.breadShop.XXI.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.breadShop.XXI.dto.OtpResult;
import com.breadShop.XXI.entity.EmailOtp;
import com.breadShop.XXI.repository.EmailOtpRepository;

/**
 * OTP 6 หลักทางอีเมล — DB เก็บแค่ hash ของ OTP, ใช้ได้ 5 นาที, กรอกผิดได้ 5 ครั้ง
 * token (UUID) คือตัวระบุว่ากำลังยืนยัน OTP ชุดไหน และใช้ต่อในขั้น reset password
 * error ส่งเป็น code (OTP_EXPIRED, OTP_INVALID, ...) — GlobalExceptionHandler แปลเป็นข้อความ
 */
@Service
public class OtpService {

    private static final int OTP_TTL_MINUTES = 5;
    private static final int MAX_ATTEMPTS    = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailOtpRepository otpRepository;
    private final PasswordEncoder    passwordEncoder;

    public OtpService(EmailOtpRepository otpRepository, PasswordEncoder passwordEncoder) {
        this.otpRepository   = otpRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** @return token + OTP ตัวจริง (OTP ตัวจริงใช้ส่งอีเมลเท่านั้น ไม่ได้เก็บ) */
    @Transactional
    public OtpResult generateOtp(String email, String purpose) {
        String otp   = String.valueOf(100000 + RANDOM.nextInt(900000));
        String token = UUID.randomUUID().toString();

        EmailOtp entity = new EmailOtp();
        entity.setEmail(email);
        entity.setPurpose(purpose);
        entity.setToken(token);
        entity.setOtpHash(passwordEncoder.encode(otp));
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_TTL_MINUTES));
        entity.setUsed(false);
        entity.setAttemptCount(0);
        otpRepository.save(entity);

        return new OtpResult(token, otp);
    }

    /**
     * ตรวจ OTP — ถูก: ทำเครื่องหมาย verified ไว้ให้ reset password ต่อ
     * noRollbackFor: จำนวนครั้งที่ผิด/การล็อกต้องถูกบันทึก แม้จะโยน exception ออกไป
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public void verifyOtp(String token, String otpInput) {
        EmailOtp otp = otpRepository.findByTokenAndUsedFalse(token)
                .orElseThrow(() -> new RuntimeException("OTP_NOT_FOUND"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otp.setUsed(true);
            otpRepository.save(otp);
            throw new RuntimeException("OTP_EXPIRED");
        }

        if (!passwordEncoder.matches(otpInput, otp.getOtpHash())) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            boolean locked = otp.getAttemptCount() >= MAX_ATTEMPTS;
            if (locked) {
                otp.setUsed(true);
            }
            otpRepository.save(otp);
            throw new RuntimeException(locked ? "OTP_LOCKED" : "OTP_INVALID");
        }

        otp.setVerified(true);
        otpRepository.save(otp);
    }

    /** ใช้ตอน reset password — token ต้องผ่าน verifyOtp แล้วและยังไม่หมดอายุ @return อีเมลเจ้าของ token */
    @Transactional
    public String validateResetToken(String token) {
        EmailOtp otp = otpRepository.findByTokenAndUsedFalse(token)
                .orElseThrow(() -> new RuntimeException("TOKEN_INVALID"));

        if (!otp.isVerified()) {
            throw new RuntimeException("OTP_NOT_VERIFIED");
        }
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("TOKEN_EXPIRED");
        }
        return otp.getEmail();
    }

    /** reset password เสร็จแล้ว — token นี้ใช้ซ้ำไม่ได้ */
    @Transactional
    public void invalidateToken(String token) {
        otpRepository.findByTokenAndUsedFalse(token).ifPresent(otp -> otp.setUsed(true));
    }
}
