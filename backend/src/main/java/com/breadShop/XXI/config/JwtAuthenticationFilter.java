package com.breadShop.XXI.config;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import com.breadShop.XXI.service.JwtService;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * อ่าน JWT จาก cookie access_token แล้วบอก Spring Security ว่าเป็นใคร (email + role จาก token ไม่ยิง DB)
 *
 *   ไม่มี token        → ผ่านไปแบบไม่ login (SecurityConfig ตัดสินว่า path นั้นต้อง login ไหม)
 *   token หมดอายุ      → 401 JWT_EXPIRED ทันที (frontend จะเรียก /auth/refresh)
 *   token ปลอม/เสีย    → ผ่านไปแบบไม่ login (path ที่ต้อง login จะได้ 401)
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    // path สาธารณะ — ข้ามการอ่าน token เพื่อไม่ให้ cookie ที่หมดอายุทำให้ login/refresh/ดูสินค้าพัง
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh",
        "/api/v1/auth/otp",
        "/api/v1/auth/otp/verification",
        "/api/v1/auth/password",
        "/api/v1/products"
    );

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return PUBLIC_PATHS.contains(path) || path.startsWith("/api/v1/products/");
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        Cookie cookie = WebUtils.getCookie(request, ACCESS_TOKEN_COOKIE);
        if (cookie != null) {
            try {
                // parse ครั้งเดียว: ตรวจลายเซ็น + วันหมดอายุ (หมดอายุ → ExpiredJwtException)
                String email = jwtService.extractEmail(cookie.getValue());
                String role  = jwtService.extractRole(cookie.getValue());
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role))));

            } catch (ExpiredJwtException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"JWT_EXPIRED\",\"message\":\"Token หมดอายุ\"}");
                return;

            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
