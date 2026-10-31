package com.breadShop.XXI.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * ส่งอีเมลของร้าน (OTP, ต้อนรับ) ผ่าน SMTP ตามค่า spring.mail.*
 *
 * เนื้อหามีชื่อร้าน บอกเหตุผลที่ได้รับ และบอกว่าถ้าไม่ได้ขอให้ทำอย่างไร —
 * เมลสั้น ๆ ที่มีแต่รหัสหน้าตาเหมือน phishing จึงถูก Gmail ย้ายไป Spam
 *
 * ส่งไม่สำเร็จ → บันทึก log แล้วจบ ไม่ throw (ไม่ให้การสมัครสมาชิกพังเพราะเมลต้อนรับ)
 * log ไม่มีรหัส OTP (ข้อความ error จาก SMTP อาจมีอีเมลผู้รับ — log อยู่ฝั่ง server เท่านั้น)
 */
@Service
public class Mailservice {

    private static final Logger log = LoggerFactory.getLogger(Mailservice.class);

    static final String SHOP_NAME = "PeakPung by Mom Hmee";

    private final JavaMailSender mailSender;
    private final String from;

    public Mailservice(JavaMailSender mailSender, @Value("${spring.mail.username:}") String fromAddress) {
        this.mailSender = mailSender;
        // "ชื่อร้าน <อีเมล>" — ผู้รับเห็นชื่อร้านแทนอีเมลเปล่า ๆ
        this.from = SHOP_NAME + " <" + fromAddress + ">";
    }

    /** ส่งเบื้องหลัง — request ตอบเร็วเท่ากันไม่ว่าอีเมลจะมีบัญชีหรือไม่ (ดู AsyncConfig) */
    @Async
    public void sendOtpEmail(String to, String otp) {
        send("OTP", to, "[PeakPung] รหัสยืนยันสำหรับตั้งรหัสผ่านใหม่", """
                สวัสดีค่ะ

                เราได้รับคำขอตั้งรหัสผ่านใหม่สำหรับบัญชี {shop} ของคุณ
                รหัสยืนยันของคุณคือ

                    {otp}

                รหัสนี้ใช้ได้ 5 นาที และใช้ได้ครั้งเดียว
                ห้ามบอกรหัสนี้กับผู้อื่น ทางร้านจะไม่ขอรหัสนี้จากคุณไม่ว่าทางใด

                หากคุณไม่ได้ขอตั้งรหัสผ่านใหม่ ไม่ต้องทำอะไร รหัสผ่านเดิมของคุณยังใช้ได้ตามปกติ

                ขอบคุณค่ะ
                {shop}
                """.replace("{shop}", SHOP_NAME).replace("{otp}", otp));
    }

    public void sendWelcomeEmail(String to, String username) {
        send("WELCOME", to, "[PeakPung] ยินดีต้อนรับสู่ " + SHOP_NAME, """
                สวัสดีคุณ {username}

                ขอบคุณที่สมัครสมาชิกกับ {shop}
                ขนมอบใหม่ทุกวันจากครัวของแม่หมี สั่งล่วงหน้าออนไลน์ได้เลย

                หากคุณไม่ได้สมัครสมาชิกเอง ตอบกลับอีเมลนี้เพื่อแจ้งทางร้านได้

                ขอบคุณค่ะ
                {shop}
                """.replace("{shop}", SHOP_NAME).replace("{username}", username));
    }

    private void send(String kind, String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
            log.info("Mail sent: kind={}", kind);
        } catch (MailException e) {
            // เช่น AuthenticationFailedException = App Password ผิด / MailSendException = ผู้รับหรือ SMTP ปฏิเสธ
            log.error("Mail failed: kind={}, reason={}: {}", kind, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
