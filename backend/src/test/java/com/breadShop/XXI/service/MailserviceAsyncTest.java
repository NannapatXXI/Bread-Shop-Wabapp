package com.breadShop.XXI.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.breadShop.XXI.config.AsyncConfig;

/**
 * ส่ง OTP ต้องไม่ทำให้ request รอ SMTP — ไม่งั้นเวลาตอบของ "อีเมลที่มีบัญชี" (ส่งเมลจริง)
 * กับ "อีเมลที่ไม่มีบัญชี" (ไม่ส่ง) ต่างกันจนเดาได้ว่าอีเมลไหนสมัครไว้
 */
@SpringJUnitConfig({AsyncConfig.class, Mailservice.class})
class MailserviceAsyncTest {

    @Autowired private Mailservice mailservice;
    @MockitoBean private JavaMailSender mailSender;

    @Test
    void sendOtpEmail_returnsImmediately_andSendsOnAnotherThread() throws Exception {
        CountDownLatch smtpRelease = new CountDownLatch(1);
        CountDownLatch sent = new CountDownLatch(1);
        AtomicReference<String> senderThread = new AtomicReference<>();
        doAnswer(inv -> {
            smtpRelease.await(5, TimeUnit.SECONDS); // จำลอง SMTP ที่ช้า
            senderThread.set(Thread.currentThread().getName());
            sent.countDown();
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));

        long start = System.nanoTime();
        mailservice.sendOtpEmail("alice@example.com", "123456");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(elapsedMs).isLessThan(1_000);  // ไม่ได้รอ SMTP
        assertThat(sent.getCount()).isEqualTo(1); // ตอน method คืนค่า เมลยังไม่ถูกส่ง

        smtpRelease.countDown();
        assertThat(sent.await(5, TimeUnit.SECONDS)).isTrue(); // แต่สุดท้ายส่งจริง
        assertThat(senderThread.get()).isNotEqualTo(Thread.currentThread().getName());
    }
}
