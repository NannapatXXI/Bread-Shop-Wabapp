package com.breadShop.XXI.Util;

import java.util.Objects;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP และ User-Agent ของคนที่เรียก API — ใช้บันทึก log เท่านั้น ใช้ที่เดียวทั้งโปรเจกต์
 *
 * IP มาจาก getRemoteAddr() — ไม่เชื่อ X-Forwarded-For จาก client เพราะปลอมได้
 * ถ้าอยู่หลัง reverse proxy ให้ตั้ง server.forward-headers-strategy=native
 */
public final class RequestInfo {

    private RequestInfo() {}

    public static String clientIp(HttpServletRequest req) {
        return req.getRemoteAddr();
    }

    public static String userAgent(HttpServletRequest req) {
        return Objects.requireNonNullElse(req.getHeader("User-Agent"), "");
    }

    /** IP ของ request ปัจจุบัน — null ถ้าไม่ได้ถูกเรียกจาก HTTP request (เช่น scheduler หรือ test) */
    public static String currentClientIp() {
        HttpServletRequest req = currentRequest();
        return req != null ? clientIp(req) : null;
    }

    /** User-Agent ของ request ปัจจุบัน — null ถ้าไม่ได้ถูกเรียกจาก HTTP request */
    public static String currentUserAgent() {
        HttpServletRequest req = currentRequest();
        return req != null ? req.getHeader("User-Agent") : null;
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest()
                : null;
    }
}
