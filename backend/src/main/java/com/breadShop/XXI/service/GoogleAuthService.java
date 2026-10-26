package com.breadShop.XXI.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.breadShop.XXI.dto.AuthenticationResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.UserRepository;

/**
 * Login ด้วย Google (OAuth 2.0 authorization code)
 *   1. getGoogleLoginUrl → ผู้ใช้ไปเลือกบัญชีที่ Google
 *   2. Google ส่ง code กลับมา → handleGoogleCallback แลกเป็นอีเมล → สมัครให้ถ้ายังไม่มีบัญชี → ออก token ของเรา
 */
@Service
public class GoogleAuthService {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String tokenUri;
    private final String userInfoUri;

    private final RestTemplate restTemplate = new RestTemplate();
    private final UserRepository      userRepository;
    private final PasswordEncoder     passwordEncoder;
    private final JwtService          jwtService;
    private final RefreshTokenService refreshTokenService;

    public GoogleAuthService(@Value("${google.clientId}") String clientId,
                             @Value("${google.clientSecret}") String clientSecret,
                             @Value("${google.redirectUri}") String redirectUri,
                             @Value("${google.tokenUri}") String tokenUri,
                             @Value("${google.userInfoUri}") String userInfoUri,
                             UserRepository userRepository, PasswordEncoder passwordEncoder,
                             JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.clientId            = clientId;
        this.clientSecret        = clientSecret;
        this.redirectUri         = redirectUri;
        this.tokenUri            = tokenUri;
        this.userInfoUri         = userInfoUri;
        this.userRepository      = userRepository;
        this.passwordEncoder     = passwordEncoder;
        this.jwtService          = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /** @param state ค่าสุ่มที่ต้องกลับมาตรงกันตอน callback (กัน login CSRF — ดู AuthController) */
    public String getGoogleLoginUrl(String state) {
        return "https://accounts.google.com/o/oauth2/auth"
                + "?client_id="    + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code&scope=email%20profile&prompt=select_account"
                + "&state="        + encode(state);
    }

    public AuthenticationResponse handleGoogleCallback(String code) {
        String email = fetchGoogleEmail(code);

        User user = userRepository.findByEmail(email).orElseGet(() -> registerGoogleUser(email));

        // บัญชีที่ admin แบนไว้ login ผ่าน Google ไม่ได้เช่นกัน
        if (!user.isActive()) {
            throw new RuntimeException("ACCOUNT_DISABLED");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole().toUpperCase(Locale.ROOT))
                .build();

        return new AuthenticationResponse(jwtService.generateToken(userDetails), refreshTokenService.create(user),
                user.getUsername(), user.getEmail());
    }

    /** code → access token ของ Google → อีเมลของผู้ใช้ */
    @SuppressWarnings("unchecked")
    private String fetchGoogleEmail(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        Map<String, Object> token = restTemplate.postForObject(tokenUri, form, Map.class);
        String googleAccessToken = (String) Objects.requireNonNull(token, "Google token response is empty").get("access_token");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(googleAccessToken);
        Map<String, Object> userInfo = restTemplate
                .exchange(userInfoUri, HttpMethod.GET, new HttpEntity<>(headers), Map.class).getBody();
        return (String) Objects.requireNonNull(userInfo, "Google userinfo response is empty").get("email");
    }

    private User registerGoogleUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(email);
        // บัญชี Google ไม่ใช้รหัสผ่าน — ใส่ hash ของค่าสุ่มที่ไม่มีใครรู้ (login ด้วยรหัสผ่านไม่ได้)
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setProvider("google");
        user.setRole("USER");
        return userRepository.save(user);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
