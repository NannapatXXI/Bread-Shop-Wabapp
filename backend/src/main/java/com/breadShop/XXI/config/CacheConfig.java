package com.breadShop.XXI.config;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Cache strategy สำหรับ Log System:
 *
 *  ปัญหา: SystemLogService อ่านไฟล์ log ทั้งหมดทุก request
 *          → ไฟล์ 100MB + 100 req/min = อ่าน 10 GB/min
 *
 *  แก้: Cache ผลลัพธ์ที่ aggregate แล้วไว้ใน memory (Caffeine)
 *        → อ่านไฟล์แค่ครั้งเดียว แล้วเสิร์ฟ cached result จนหมด TTL
 *
 *  แต่ละ cache มี TTL ต่างกันตาม "ความถี่ที่ข้อมูลเปลี่ยน":
 *    logTrend      5 min   ─ hourly chart ของวันนี้ เปลี่ยนบ่อย
 *    errorTrend   30 min   ─ daily chart ย้อน 7 วัน เปลี่ยนช้า
 *    logLevelCount 5 min   ─ summary card (error/warn count)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(Arrays.asList(
            buildCache("logTrend",      5,  200),
            buildCache("errorTrend",   30,  100),
            buildCache("logLevelCount", 5,   50)
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, int ttlMinutes, int maxSize) {
        return new CaffeineCache(name,
            Caffeine.newBuilder()
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .recordStats()   // เปิดไว้ดู hit/miss ผ่าน actuator ในอนาคต
                .build()
        );
    }
}
