package com.breadShop.XXI.config;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * กันเดารหัสผ่าน / ยิง OTP รัว: POST ได้ไม่เกิน MAX_ATTEMPTS ครั้งต่อนาทีต่อ IP เกิน → 429
 * path ที่ใช้กำหนดไว้ใน WebConfig ที่เดียว
 *
 * IP = req.getRemoteAddr() เท่านั้น — ห้ามเชื่อ X-Forwarded-For จาก client (เปลี่ยน header ทุกครั้งก็หลบได้)
 * ถ้าอยู่หลัง reverse proxy ให้ตั้ง server.forward-headers-strategy=native แทน
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int  MAX_ATTEMPTS = 10;
    private static final long WINDOW_MS    = 60_000L;

    /** หน้าต่างเวลาของแต่ละ IP: เริ่มนับเมื่อไหร่ และนับได้กี่ครั้งแล้ว */
    private record Window(long startMs, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull Object handler)
            throws IOException {
        if (!"POST".equalsIgnoreCase(req.getMethod())) return true;

        long now = System.currentTimeMillis();
        // compute ทำงานแบบ atomic ต่อ key — สอง request จาก IP เดียวกันพร้อมกันนับไม่หาย
        Window w = windows.compute(req.getRemoteAddr(), (ip, old) ->
            old == null || now - old.startMs() > WINDOW_MS ? new Window(now, 1) : new Window(old.startMs(), old.count() + 1));

        if (w.count() <= MAX_ATTEMPTS) return true;

        long retryAfterSec = (WINDOW_MS - (now - w.startMs())) / 1000;
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setHeader("Retry-After", String.valueOf(retryAfterSec));
        res.getWriter().write("{\"success\":false,\"message\":\"พยายามเข้าสู่ระบบบ่อยเกินไป กรุณารอ "
            + retryAfterSec + " วินาที\"}");
        return false;
    }

    /** ล้างหน้าต่างที่หมดเวลาแล้วทุกนาที — map ไม่โตไปเรื่อย ๆ */
    @Scheduled(fixedRate = WINDOW_MS)
    public void evictExpired() {
        evictExpired(System.currentTimeMillis());
    }

    void evictExpired(long now) {
        windows.values().removeIf(w -> now - w.startMs() > WINDOW_MS);
    }

    int trackedClients() {
        return windows.size();
    }
}
