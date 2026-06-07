package com.breadShop.XXI.config;

import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.breadShop.XXI.service.ApiRequestLogService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * บันทึกทุก HTTP request ที่เข้า /api/** ลง api_request_logs
 *
 * แนวคิด:
 *   preHandle  → จด start time ไว้ใน ThreadLocal (ทำงานก่อน controller)
 *   afterCompletion → คำนวณ duration แล้ว save ลง DB (ทำงานหลัง response ส่งไปแล้ว)
 *
 * ทำไมใช้ ThreadLocal:
 *   แต่ละ request วิ่งบน thread ของตัวเอง ThreadLocal เก็บค่าแยกต่างหากต่อ thread
 *   จึงไม่มีปัญหา concurrent request ชนกัน
 */
@Component
public class ApiLoggingInterceptor implements HandlerInterceptor {

    // regex แทน numeric segment ใน path ด้วย {id} เช่น /api/v1/orders/123 → /api/v1/orders/{id}
    private static final Pattern ID_PATTERN = Pattern.compile("/\\d+");

    private static final ThreadLocal<Long> START_TIME = new ThreadLocal<>();

    private final ApiRequestLogService apiRequestLogService;

    public ApiLoggingInterceptor(ApiRequestLogService apiRequestLogService) {
        this.apiRequestLogService = apiRequestLogService;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        START_TIME.set(System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        Long start = START_TIME.get();
        START_TIME.remove();  // ป้องกัน memory leak — ต้อง remove ทุกครั้ง

        if (start == null) return;

        long durationMs = System.currentTimeMillis() - start;
        String method   = req.getMethod();
        String uri      = req.getRequestURI();
        String endpoint = ID_PATTERN.matcher(uri).replaceAll("/{id}");
        int    status   = res.getStatus();

        String userEmail = null;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                userEmail = auth.getName();
            }
        } catch (Exception ignored) {}

        apiRequestLogService.save(method, endpoint, uri, status, durationMs,
                                  userEmail, req.getRemoteAddr());
    }
}
