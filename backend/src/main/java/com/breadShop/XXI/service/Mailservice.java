package com.breadShop.XXI.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

//สำหรับการส่งอีเมล เช่น OTP และอีเมลต้อนรับ  reviewd by peak
@Service
public class Mailservice {

    private final JavaMailSender mailSender; //เป็น Interface ของ Spring  ทำหน้าที่ส่ง Email ผ่าน SMTP()SMTP (Simple Mail Transfer Protocol)
   

    // Constructor Injection (ปลอดภัย + Production)
    public Mailservice(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }
    
    /**
     * ส่งอีเมล OTP สำหรับรีเซ็ตรหัสผ่าน โดยรับพารามิเตอร์ที่อยู่ผู้รับและรหัส OTP ที่จะส่งไปในเนื้อหาอีเมล
     * @param to ที่อยู่อีเมลของผู้รับ OTP
     * @param otp รหัส OTP ที่จะส่งไปในเนื้อหาอีเมล โดยจะแสดงข้อความว่า "รหัส OTP ของคุณคือ: {otp} รหัสนี้มีอายุ 5 นาที" เพื่อแจ้งผู้ใช้ถึงรหัส OTP และระยะเวลาที่สามารถใช้งานได้
     */
    public void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("OTP สำหรับรีเซ็ตรหัสผ่าน");
        message.setText(
                "รหัส OTP ของคุณคือ: " + otp +
                "\nรหัสนี้มีอายุ 5 นาที"
        );
        System.out.println("กำลังส่ง OTP ไปที่อีเมล: " + to);

        mailSender.send(message);
    }

    /**
     * ส่งอีเมลต้อนรับหลังจากที่ผู้ใช้สมัครสมาชิกสำเร็จ โดยรับพารามิเตอร์ที่อยู่ผู้รับและชื่อผู้ใช้ที่จะนำไปใช้ในเนื้อหาอีเมล
     * @param to ที่อยู่อีเมลของผู้รับอีเมลต้อนรับ
     * @param username ชื่อผู้ใช้ที่จะนำไปใช้ในเนื้อหาอีเมล โดยจะแสดงข้อความต้อนรับที่มีชื่อผู้ใช้ เช่น "สวัสดี {username}! ขอบคุณที่สมัครสมาชิกกับเรา เราหวังว่าคุณจะมีประสบการณ์ที่ดีกับการช็อปปิ้งขนมปังและเบเกอรี่ของเรา! ขอให้มีวันที่ดี! ทีมงาน BreadShop XXI" เพื่อสร้างความประทับใจและความรู้สึกเป็นส่วนหนึ่งของชุมชนของร้าน BreadShop XXI
     */
    public void sendWelcomeEmail(String to, String username) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("ยินดีต้อนรับสู่ PeakPung By MomHmee ");
        message.setText(
                "สวัสดี " + username + "!\n\n" +
                "ขอบคุณที่สมัครสมาชิกกับเรา เราหวังว่าคุณจะมีประสบการณ์ที่ดีกับการช็อปปิ้งขนมปังและเบเกอรี่ของเรา!\n\n" +
                "ขอให้มีวันที่ดี!\n" +
                "ทีมงาน PeakPung By MomHmee"
        );
        System.out.println("กำลังส่งอีเมลต้อนรับไปที่: " + to);

        mailSender.send(message);
    }
    


}
