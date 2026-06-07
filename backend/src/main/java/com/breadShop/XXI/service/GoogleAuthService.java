package com.breadShop.XXI.service;

import java.time.LocalDateTime; // 1. Import เพิ่ม
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.breadShop.XXI.dto.AuthenticationResponse;
import com.breadShop.XXI.entity.RefreshToken;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.UserRepository;



//สำหรับการสมัครใน google reviewed by peak
@Service
public class GoogleAuthService {

    @Value("${google.clientId}")
    private String clientId;

    @Value("${google.clientSecret}")
    private String clientSecret;

    @Value("${google.redirectUri}")
    private String redirectUri;

    @Value("${google.tokenUri}")
    private String tokenUri;

    @Value("${google.userInfoUri}")
    private String userInfoUri;

    private final RestTemplate restTemplate = new RestTemplate();
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public GoogleAuthService(UserRepository userRepository, JwtService jwtService,
                             RefreshTokenService refreshTokenService) {
        this.userRepository      = userRepository;
        this.jwtService          = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    public String getGoogleLoginUrl() {
        return "https://accounts.google.com/o/oauth2/auth?client_id="
                + clientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code&scope=email%20profile"+
                "&prompt=select_account" ;
                
    }

    public AuthenticationResponse handleGoogleCallback(String code) {
        // 1. ขอ access token
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenResponse = restTemplate.postForObject(tokenUri, params, Map.class);

        String googleAccessToken = (String) tokenResponse.get("access_token");

        // 2. ดึงข้อมูล user
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(googleAccessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> profileResponse = restTemplate.exchange(userInfoUri, HttpMethod.GET, entity, Map.class);

        Map<String, Object> userInfo = profileResponse.getBody();
        String email = (String) userInfo.get("email");

        // 3. สมัคร user ใหม่ถ้ายังไม่มี หรือดึง user เดิมมา
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setUsername(email);
                    // เก็บ BCrypt hash ของ random UUID — ไม่มีใครรู้ password นี้
                    // ป้องกัน BCryptPasswordEncoder warning และ plain-text leak
                    newUser.setPassword(new BCryptPasswordEncoder().encode(
                            java.util.UUID.randomUUID().toString()));
                    newUser.setProvider("google");
                    newUser.setRole("USER");
                    return userRepository.save(newUser);
                });

        // --- ส่วนที่เพิ่ม: บันทึกเวลา Login ---
        user.setLastLoginAt(LocalDateTime.now()); // อัปเดตเวลาปัจจุบัน
        userRepository.save(user);                // บันทึกลง Database
        // ---------------------------------

        // 4. แปลงเป็น UserDetails สำหรับ JWT
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .roles(user.getRole() == null ? "USER" : user.getRole().toUpperCase())
                .build();

        // 5. สร้าง access token + refresh token (เหมือน login ปกติ)
        String accessToken = jwtService.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.create(user);

        return new AuthenticationResponse(
                accessToken,
                refreshToken.getToken(),
                user.getUsername(),
                user.getEmail()
        );
    }
}