package com.breadShop.XXI.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * เปิด @Async — ใช้ส่งอีเมล OTP เบื้องหลัง
 * request ส่ง OTP จึงตอบเร็วเท่ากันไม่ว่าอีเมลจะมีบัญชีหรือไม่ (เดาจากเวลาตอบไม่ได้)
 * ใช้ thread pool applicationTaskExecutor ที่ Spring Boot สร้างให้
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
