package com.breadShop.XXI.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MailserviceTest {

    @Mock
    private JavaMailSender mailSender;

    private Mailservice mailservice;

    @BeforeEach
    void setUp() {
        mailservice = new Mailservice(mailSender);
    }

    @Test
    void sendOtpEmail_shouldBuildAndSendMessageWithOtp() {
        mailservice.sendOtpEmail("user@breadshop.com", "123456");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("user@breadshop.com");
        assertThat(sent.getText()).contains("123456");
    }

    @Test
    void sendWelcomeEmail_shouldBuildAndSendMessageWithUsername() {
        mailservice.sendWelcomeEmail("user@breadshop.com", "Peak");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("user@breadshop.com");
        assertThat(sent.getText()).contains("Peak");
    }
}
