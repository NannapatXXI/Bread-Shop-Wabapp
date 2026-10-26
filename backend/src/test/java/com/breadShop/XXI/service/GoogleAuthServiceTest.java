package com.breadShop.XXI.service;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.breadShop.XXI.dto.AuthenticationResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    private static final String CLIENT_ID = "client-id-123";
    private static final String REDIRECT_URI = "http://localhost:8080/api/v1/auth/google/callback";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private RestTemplate restTemplate;

    private GoogleAuthService googleAuthService;

    @BeforeEach
    void setUp() {
        googleAuthService = new GoogleAuthService(CLIENT_ID, "secret", REDIRECT_URI, TOKEN_URI, USERINFO_URI,
                userRepository, new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),
                jwtService, refreshTokenService);

        restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(googleAuthService, "restTemplate", restTemplate);
    }

    private void stubGoogleHttpCalls(String email) {
        Map<String, Object> tokenResponse = Map.of("access_token", "google-access-token");
        when(restTemplate.postForObject(eq(TOKEN_URI), any(), eq(Map.class)))
                .thenReturn(tokenResponse);

        Map<String, Object> userInfo = Map.of("email", email);
        when(restTemplate.exchange(eq(USERINFO_URI), eq(HttpMethod.GET), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(userInfo));
    }

    @Test
    void getGoogleLoginUrl_shouldContainClientIdAndRedirectUri() {
        String url = googleAuthService.getGoogleLoginUrl("st4te_-x");

        assertThat(url).contains(CLIENT_ID);
        assertThat(url).contains(java.net.URLEncoder.encode(REDIRECT_URI, java.nio.charset.StandardCharsets.UTF_8));
        assertThat(url).startsWith("https://accounts.google.com/o/oauth2/auth");
        assertThat(url).contains("&state=st4te_-x");
    }

    @Test
    void handleGoogleCallback_whenNewUser_shouldCreateUserAndReturnTokens() {
        stubGoogleHttpCalls("newuser@breadshop.com");
        when(userRepository.findByEmail("newuser@breadshop.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-access-token");
        when(refreshTokenService.create(any(User.class))).thenReturn("refresh-token-value");

        AuthenticationResponse response = googleAuthService.handleGoogleCallback("auth-code");

        assertEquals("jwt-access-token", response.accessToken());
        assertEquals("refresh-token-value", response.refreshToken());
        assertEquals("newuser@breadshop.com", response.email());
        assertEquals("newuser@breadshop.com", response.username());

        verify(userRepository, times(2)).save(any(User.class)); // once in orElseGet to create, once more for lastLoginAt update
    }

    @Test
    void handleGoogleCallback_whenExistingUserWithRole_shouldUseUppercasedRole() {
        stubGoogleHttpCalls("existing@breadshop.com");

        User existingUser = new User();
        existingUser.setEmail("existing@breadshop.com");
        existingUser.setUsername("existing@breadshop.com");
        existingUser.setPassword("hashed");
        existingUser.setRole("admin");

        when(userRepository.findByEmail("existing@breadshop.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-access-token");
        when(refreshTokenService.create(any(User.class))).thenReturn("refresh-token-value");

        AuthenticationResponse response = googleAuthService.handleGoogleCallback("auth-code");

        assertEquals("existing@breadshop.com", response.email());

        ArgumentCaptor<UserDetails> captor = ArgumentCaptor.forClass(UserDetails.class);
        verify(jwtService).generateToken(captor.capture());
        assertThat(captor.getValue().getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_ADMIN");

        assertThat(existingUser.getLastLoginAt()).isNotNull();
    }

    @Test
    void handleGoogleCallback_whenExistingUserWithNullRole_shouldDefaultToUserRole() {
        stubGoogleHttpCalls("norole@breadshop.com");

        User existingUser = new User();
        existingUser.setEmail("norole@breadshop.com");
        existingUser.setUsername("norole@breadshop.com");
        existingUser.setPassword("hashed");

        when(userRepository.findByEmail("norole@breadshop.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-access-token");
        when(refreshTokenService.create(any(User.class))).thenReturn("refresh-token-value");

        googleAuthService.handleGoogleCallback("auth-code");

        ArgumentCaptor<UserDetails> captor = ArgumentCaptor.forClass(UserDetails.class);
        verify(jwtService).generateToken(captor.capture());
        assertThat(captor.getValue().getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }

    @Test
    void handleGoogleCallback_bannedAccount_isRefused_withoutIssuingTokens() {
        stubGoogleHttpCalls("banned@breadshop.com");
        User banned = new User();
        banned.setEmail("banned@breadshop.com");
        banned.setUsername("banned");
        banned.setPassword("hashed");
        banned.setRole("USER");
        banned.setActive(false);
        when(userRepository.findByEmail("banned@breadshop.com")).thenReturn(Optional.of(banned));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> googleAuthService.handleGoogleCallback("auth-code"))
                .hasMessage("ACCOUNT_DISABLED");

        verify(jwtService, never()).generateToken(any(UserDetails.class));
        verify(refreshTokenService, never()).create(any(User.class));
    }
}
