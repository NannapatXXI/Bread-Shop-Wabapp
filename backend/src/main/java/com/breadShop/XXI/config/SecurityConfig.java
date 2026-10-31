package com.breadShop.XXI.config;

import java.util.Locale;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import com.breadShop.XXI.repository.UserRepository;

@Configuration
public class SecurityConfig {

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // โหลด user จาก DB — ที่นี่แค่หา user ไม่ตัดสินเรื่องถูกแบน
    // เช็คแบนทำใน AuthService หลังรหัสผ่านถูกแล้ว: คนที่ไม่รู้รหัสจะเห็นแค่ "รหัสผิด"
    // เดาไม่ได้ว่าบัญชีนี้มีจริงหรือถูกแบน (user enumeration)
    @Bean
    public UserDetailsService userDetailsService() {
        return login -> userRepository.findByUsername(login)
                .or(() -> userRepository.findByEmail(login))
                .map(user -> org.springframework.security.core.userdetails.User
                        .withUsername(user.getEmail())
                        .password(user.getPassword())
                        .roles(user.getRole().toUpperCase(Locale.ROOT))
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    // ไม่ต้องสร้าง AuthenticationProvider เอง — Spring สร้าง DaoAuthenticationProvider
    // จาก UserDetailsService + PasswordEncoder ด้านล่างให้อัตโนมัติ
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of("http://localhost:3000"));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            config.setAllowCredentials(true);
            return config;
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthFilter
    ) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // ไม่ได้ login (ไม่มี/ไม่ผ่าน access token) → 401 เพื่อให้ frontend ลอง /auth/refresh ได้
            // ส่วน login แล้วแต่ไม่มีสิทธิ์ ยังเป็น 403 ตามค่าเริ่มต้นของ Spring Security
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                    "{\"success\":false,\"message\":\"กรุณาเข้าสู่ระบบ\",\"data\":null}");
            }))
            .authorizeHttpRequests(auth -> auth

            // preflight
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

            // ---------- Public ----------
            .requestMatchers(
                "/api/v1/auth/login",
                "/api/v1/auth/logout",
                "/api/v1/auth/register",
                "/api/v1/auth/refresh",
                "/api/v1/auth/google/**",
                "/api/v1/auth/otp",
                "/api/v1/auth/otp/verification",
                "/api/v1/auth/password",
                "/api/v1/products/**",   // ** รวม /api/v1/products ด้วย
                "/api/v1/storefront",    // รูปปก + สินค้าแนะนำ (หน้าแรกเปิดได้โดยไม่ login)
                "/uploads/**"
            ).permitAll()

            // ---------- Monitoring ----------
            // อยู่บน management port (8081) เท่านั้น — port 8080 ไม่มี /actuator ให้เรียก
            .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()

            // ---------- User (ข้อมูลของตัวเอง — ทั้งลูกค้าและ admin) ----------
            .requestMatchers("/api/v1/users/me/**").authenticated()

            // ---------- Notifications ----------
            .requestMatchers("/api/v1/notifications/**").authenticated()

            // ---------- Promotions ----------
            // ลูกค้าดูส่วนลดของโค้ดได้ ที่เหลือ (ดู/สร้าง/เปิดปิด/ลบ) admin เท่านั้น — /discount ต้องมาก่อน
            .requestMatchers(HttpMethod.GET, "/api/v1/promotions/discount").authenticated()
            .requestMatchers("/api/v1/promotions/**").hasRole("ADMIN")

            // ---------- Admin ----------
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

            // ---------- Others ----------
            .anyRequest().authenticated()
        )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
