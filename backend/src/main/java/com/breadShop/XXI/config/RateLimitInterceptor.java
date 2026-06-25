package com.breadShop.XXI.config;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate limiting สำหรับ /api/v1/auth/login
 * อนุญาต MAX_ATTEMPTS ครั้งต่อ WINDOW_MS มิลลิวินาทีต่อ IP
 * ถ้าเกิน → 429 Too Many Requests
 *
 * ทำไมต้องมี: ป้องกัน brute-force password โดย attacker วน loop ทดสอบ password
 * ConcurrentHashMap + synchronized เพื่อ thread-safe กับ concurrent requests
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int    MAX_ATTEMPTS = 10;
    private static final long   WINDOW_MS    = 60_000L; // 1 นาที

    private static final String LOGIN_PATH   = "/api/v1/auth/login";
    private static final String SEND_OTP     = "/api/v1/auth/send-OTP-mail";

    // IP → [attemptCount, windowStartEpochMs]
    private final Map<String, long[]> loginAttempts = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
            throws IOException {

        String uri = req.getRequestURI();
        if (!LOGIN_PATH.equals(uri) && !SEND_OTP.equals(uri)) return true;
        if (!"POST".equalsIgnoreCase(req.getMethod())) return true;

        String ip = getClientIp(req);
        long now = Instant.now().toEpochMilli();

        synchronized (loginAttempts) {
            long[] bucket = loginAttempts.get(ip);

            if (bucket == null || now - bucket[1] > WINDOW_MS) {
                loginAttempts.put(ip, new long[]{ 1, now });
                return true;
            }

            bucket[0]++;
            if (bucket[0] > MAX_ATTEMPTS) {
                long retryAfterSec = (WINDOW_MS - (now - bucket[1])) / 1000;
                res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                res.setHeader("Retry-After", String.valueOf(retryAfterSec));
                res.getWriter().write(
                    "{\"success\":false,\"message\":\"พยายามเข้าสู่ระบบบ่อยเกินไป กรุณารอ "
                    + retryAfterSec + " วินาที\"}"
                );
                return false;
            }
        }
        return true;
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
