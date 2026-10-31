package com.breadShop.XXI.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.breadShop.XXI.config.SecurityConfig;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.OrderRepository;
import com.breadShop.XXI.repository.UserRepository;
import com.breadShop.XXI.service.CustomerService;
import com.breadShop.XXI.service.JwtService;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;

/**
 * ฐานของเทส controller: ใช้ SecurityConfig + JwtAuthenticationFilter + JwtService ตัวจริง
 * (กฎสิทธิ์ชุดเดียวกับ production) แล้ว mock เฉพาะ service/repository ด้านหลัง
 * token ในเทสเซ็นด้วย secret ที่สุ่มใหม่ทุกครั้ง — ไม่ใช้ secret จริงของระบบ
 */
@Import({ SecurityConfig.class, JwtService.class, CustomerService.class, ControllerTestSupport.ProductionLikeServletPath.class })
abstract class ControllerTestSupport {

    private static final String TEST_JWT_SECRET = randomBase64Key();

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("app.jwtSecret", () -> TEST_JWT_SECRET);
        registry.add("app.jwtExpirationMs", () -> "900000");
        registry.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-client-secret");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtService jwtService;

    @MockitoBean
    protected UserRepository userRepository;

    // CustomerService ตัวจริงต้องใช้ — ไม่ได้ใช้ในเทสส่วนใหญ่
    @MockitoBean
    protected OrderRepository orderRepository;

    // ตัวจริง (หา user จาก userRepository ที่ mock ไว้) หรือ mock ถ้าเทสลูกประกาศ @MockitoBean CustomerService
    @Autowired
    private CustomerService customerService;

    protected Cookie tokenFor(String email, String role) {
        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername(email).password("unused").roles(role).build();
        return new Cookie("access_token", jwtService.generateToken(details));
    }

    /** body JSON ของ request — ใช้คู่กับ .with(json("{...}")) */
    protected static RequestPostProcessor json(String body) {
        return request -> {
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            return request;
        };
    }

    protected Cookie userToken(String email) {
        return tokenFor(email, "USER");
    }

    protected Cookie adminToken(String email) {
        return tokenFor(email, "ADMIN");
    }

    /** token ที่ลายเซ็นถูกต้องแต่หมดอายุไปแล้ว */
    protected Cookie expiredToken(String email) {
        return new Cookie("access_token", signedToken(email, TEST_JWT_SECRET, new Date(System.currentTimeMillis() - 60_000)));
    }

    /** token ที่เซ็นด้วย key อื่น — จำลองคนปลอม token ขึ้นเองเป็น ADMIN */
    protected Cookie forgedAdminToken(String email) {
        return new Cookie("access_token", signedToken(email, randomBase64Key(), new Date(System.currentTimeMillis() + 60_000)));
    }

    private static String signedToken(String email, String base64Key, Date expiresAt) {
        return Jwts.builder()
                .claims(Map.of("roles", List.of(Map.of("authority", "ROLE_ADMIN"))))
                .subject(email)
                .issuedAt(new Date(expiresAt.getTime() - 120_000))
                .expiration(expiresAt)
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Key)), Jwts.SIG.HS256)
                .compact();
    }

    /** สร้าง User พร้อม id และให้ userRepository.findByEmail คืน user นี้ */
    protected User stubUser(int id, String email, String role) {
        User user = new User("user" + id, email, "hashed", role);
        ReflectionTestUtils.setField(user, "id", id);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        if (Mockito.mockingDetails(customerService).isMock()) {
            when(customerService.getByEmail(email)).thenReturn(user);
        }
        return user;
    }

    /**
     * Tomcat + DispatcherServlet ที่ map "/" จะตั้ง servletPath = path เต็มของ request
     * แต่ MockMvc ปล่อยว่าง — JwtAuthenticationFilter.shouldNotFilter อ่าน servletPath
     * จึงต้องตั้งให้เหมือน production ไม่งั้นเทสจะไม่ตรงกับพฤติกรรมจริง
     */
    @TestConfiguration
    static class ProductionLikeServletPath {
        @Bean
        MockMvcBuilderCustomizer servletPathLikeTomcat() {
            return builder -> builder.defaultRequest(get("/").with(request -> {
                request.setServletPath(request.getRequestURI());
                request.setPathInfo(null);
                return request;
            }));
        }
    }

    private static String randomBase64Key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
