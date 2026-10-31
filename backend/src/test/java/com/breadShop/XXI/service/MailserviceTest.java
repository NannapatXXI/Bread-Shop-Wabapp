package com.breadShop.XXI.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
class MailserviceTest {

    private static final String SHOP_MAIL = "peakpung.shop@example.com";

    @Mock
    private JavaMailSender mailSender;

    private Mailservice mailservice;

    @BeforeEach
    void setUp() {
        mailservice = new Mailservice(mailSender, SHOP_MAIL);
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    // ───────────── OTP: หน้าตาต้องเป็นเมลของร้าน ไม่ใช่รหัสโดด ๆ (ไม่งั้นลง Spam) ─────────────

    @Test
    void otp_isSentFromTheShopName_toTheRequester() {
        mailservice.sendOtpEmail("alice@example.com", "123456");

        SimpleMailMessage sent = sentMessage();
        assertThat(sent.getFrom()).isEqualTo("PeakPung by Mom Hmee <" + SHOP_MAIL + ">");
        assertThat(sent.getTo()).containsExactly("alice@example.com");
        assertThat(sent.getSubject()).startsWith("[PeakPung]");
    }

    @Test
    void otp_bodyExplainsWhy_expiry_andWhatToDoIfNotRequested() {
        mailservice.sendOtpEmail("alice@example.com", "123456");

        assertThat(sentMessage().getText())
                .contains("123456")
                .contains("PeakPung by Mom Hmee")
                .contains("ตั้งรหัสผ่านใหม่")
                .contains("5 นาที")
                .contains("ห้ามบอกรหัสนี้กับผู้อื่น")
                .contains("หากคุณไม่ได้ขอตั้งรหัสผ่านใหม่");
    }

    @Test
    void otp_success_isLogged_withoutTheCode(CapturedOutput output) {
        mailservice.sendOtpEmail("alice@example.com", "123456");

        assertThat(output).contains("Mail sent: kind=OTP");
        assertThat(output).doesNotContain("123456").doesNotContain("alice@example.com");
    }

    @Test
    void otp_smtpFailure_isLoggedWithReason_andDoesNotThrow(CapturedOutput output) {
        doThrow(new MailAuthenticationException("535 Username and Password not accepted"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> mailservice.sendOtpEmail("alice@example.com", "123456")).doesNotThrowAnyException();

        assertThat(output).contains("Mail failed: kind=OTP, reason=MailAuthenticationException")
                .contains("535");
        assertThat(output).doesNotContain("123456");
    }

    // ───────────── ต้อนรับ ─────────────

    @Test
    void welcome_isSentFromTheShopName_withUsername() {
        mailservice.sendWelcomeEmail("user@breadshop.com", "Peak");

        SimpleMailMessage sent = sentMessage();
        assertThat(sent.getFrom()).isEqualTo("PeakPung by Mom Hmee <" + SHOP_MAIL + ">");
        assertThat(sent.getTo()).containsExactly("user@breadshop.com");
        assertThat(sent.getSubject()).startsWith("[PeakPung]");
        assertThat(sent.getText()).contains("Peak");
    }

    @Test
    void welcome_smtpFailure_doesNotBreakRegistration(CapturedOutput output) {
        doThrow(new MailAuthenticationException("535")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> mailservice.sendWelcomeEmail("user@breadshop.com", "Peak")).doesNotThrowAnyException();

        assertThat(output).contains("Mail failed: kind=WELCOME");
    }
}
