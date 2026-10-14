package com.breadShop.XXI.service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.breadShop.XXI.dto.OtpResult;
import com.breadShop.XXI.entity.EmailOtp;
import com.breadShop.XXI.repository.EmailOtpRepository;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private EmailOtpRepository otpRepository;

    @Mock
    private PasswordEncoder passwordEncoder;   // ปลอมตัวนี้ด้วย เพราะ OtpService เรียกใช้จริง

    private OtpService otpService;

    //รับตาม constructor ของ class
    @BeforeEach
    void setUp() {
      
        otpService = new OtpService(otpRepository, passwordEncoder);
    }

    // ═══════════════════════════════════════
    //  generateOtp
    // ═══════════════════════════════════════

    @Test
    void generateOtp_shouldReturnTokenAndSixDigitOtp() {
        
        // Arrange: บอกว่าเวลามีคนเรียก encode(อะไรก็ได้) ให้ตอบกลับ string ปลอมๆ นี้
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-otp");

        // Act
        OtpResult result = otpService.generateOtp("test@breadshop.com", "REGISTER");

        // Assert
        assertNotNull(result.token());
        assertNotNull(result.plainOtp());
        assertEquals(6, result.plainOtp().length());               // otp ต้องยาว 6 หลัก
        assertTrue(result.plainOtp().matches("\\d{6}"));            // ต้องเป็นตัวเลขล้วน

        // ตรวจว่า save() ถูกเรียกจริง และข้อมูลที่ save ถูกต้อง
        // ArgumentCaptor = "ดักจับ" argument ที่ถูกส่งเข้า method mock ไว้ดูภายหลัง
        ArgumentCaptor<EmailOtp> captor = ArgumentCaptor.forClass(EmailOtp.class);
        verify(otpRepository).save(captor.capture());

        EmailOtp saved = captor.getValue();
        assertEquals("test@breadshop.com", saved.getEmail());
        assertEquals("REGISTER", saved.purPose());
        assertFalse(saved.isUsed());
        assertEquals(0, saved.getAttemptCount());
    }

    // ═══════════════════════════════════════
    //  verifyOtp
    // ═══════════════════════════════════════

    @Test
    void verifyOtp_whenTokenNotFound_shouldThrowOtpNotFound() {
        when(otpRepository.findByTokenAndUsedFalse("bad-token"))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> otpService.verifyOtp("bad-token", "123456"));

        assertEquals("OTP_NOT_FOUND", ex.getMessage());
    }

    @Test
    void verifyOtp_whenExpired_shouldMarkUsedAndThrowOtpExpired() {
        EmailOtp expired = new EmailOtp();
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));   // หมดอายุไปแล้ว 1 นาทีก่อน

        when(otpRepository.findByTokenAndUsedFalse("token"))
                .thenReturn(Optional.of(expired));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> otpService.verifyOtp("token", "123456"));

        assertEquals("OTP_EXPIRED", ex.getMessage());
        assertTrue(expired.isUsed());                    // ต้องถูก mark ว่าใช้แล้ว
        verify(otpRepository).save(expired);
    }

    @Test
    void verifyOtp_whenWrongCodeAndAttemptsUnder5_shouldThrowOtpInvalid() {
        EmailOtp otp = new EmailOtp();
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));   // ยังไม่หมดอายุ
        otp.setAttemptCount(2);
        otp.setOtpHash("hashed");

        when(otpRepository.findByTokenAndUsedFalse("token")).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> otpService.verifyOtp("token", "wrong"));

        assertEquals("OTP_INVALID", ex.getMessage());
        assertEquals(3, otp.getAttemptCount());          // เพิ่มจาก 2 เป็น 3
        assertFalse(otp.isUsed());                       // ยังไม่ถูก lock
    }

    @Test
    void verifyOtp_whenWrongCodeAndAttemptsReach5_shouldThrowOtpLocked() {
        EmailOtp otp = new EmailOtp();
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setAttemptCount(4);                          // ครั้งนี้จะกลายเป็นครั้งที่ 5
        otp.setOtpHash("hashed");

        when(otpRepository.findByTokenAndUsedFalse("token")).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> otpService.verifyOtp("token", "wrong"));

        assertEquals("OTP_LOCKED", ex.getMessage());
        assertEquals(5, otp.getAttemptCount());
        assertTrue(otp.isUsed());                        // ต้อง lock แล้ว
    }

    @Test
    void verifyOtp_whenCorrectCode_shouldMarkVerified() {
        EmailOtp otp = new EmailOtp();
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setOtpHash("hashed");

        when(otpRepository.findByTokenAndUsedFalse("token")).thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);

        assertDoesNotThrow(() -> otpService.verifyOtp("token", "123456"));

        assertTrue(otp.isVerified());
        verify(otpRepository).save(otp);
    }

    // ═══════════════════════════════════════
    //  validateResetToken
    // ═══════════════════════════════════════

    @Test
    void validateResetToken_whenTokenNotFound_shouldThrowTokenInvalid() {
        when(otpRepository.findByTokenAndUsedFalse("bad")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> otpService.validateResetToken("bad"));

        assertEquals("TOKEN_INVALID", ex.getMessage());
    }

    @Test
    void validateResetToken_whenNotVerified_shouldThrowOtpNotVerified() {
        EmailOtp otp = new EmailOtp();
        otp.setVerified(false);

        when(otpRepository.findByTokenAndUsedFalse("token")).thenReturn(Optional.of(otp));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> otpService.validateResetToken("token"));

        assertEquals("OTP_NOT_VERIFIED", ex.getMessage());
    }

    @Test
    void validateResetToken_whenVerifiedButExpired_shouldThrowTokenExpired() {
        EmailOtp otp = new EmailOtp();
        otp.setVerified(true);
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(otpRepository.findByTokenAndUsedFalse("token")).thenReturn(Optional.of(otp));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> otpService.validateResetToken("token"));

        assertEquals("TOKEN_EXPIRED", ex.getMessage());
    }

    @Test
    void validateResetToken_whenVerifiedAndNotExpired_shouldReturnEmail() {
        EmailOtp otp = new EmailOtp();
        otp.setVerified(true);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setEmail("test@breadshop.com");

        when(otpRepository.findByTokenAndUsedFalse("token")).thenReturn(Optional.of(otp));

        String email = otpService.validateResetToken("token");

        assertEquals("test@breadshop.com", email);
    }

    // ═══════════════════════════════════════
    //  invalidateToken
    // ═══════════════════════════════════════

    @Test
    void invalidateToken_whenTokenFound_shouldMarkUsed() {
        EmailOtp otp = new EmailOtp();
        otp.setUsed(false);

        when(otpRepository.findByTokenAndUsedFalse("token")).thenReturn(Optional.of(otp));

        otpService.invalidateToken("token");

        assertTrue(otp.isUsed());
        // หมายเหตุ: ไม่ต้อง verify(save) เพราะโค้ดจริงไม่ได้เรียก save() เลย!
        // มันทำงานได้เพราะ JPA "dirty checking" — ถ้าอยู่ใน @Transactional
        // การแก้ field ของ entity ที่ดึงมาจาก DB จะถูกบันทึกอัตโนมัติตอน transaction commit
    }

    @Test
    void invalidateToken_whenTokenNotFound_shouldDoNothing() {
        when(otpRepository.findByTokenAndUsedFalse("missing")).thenReturn(Optional.empty());

        // แค่เรียกแล้วไม่ throw error ก็ถือว่าผ่าน
        assertDoesNotThrow(() -> otpService.invalidateToken("missing"));
    }
}