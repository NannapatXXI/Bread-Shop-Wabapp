package com.breadShop.XXI.service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.breadShop.XXI.entity.RefreshToken;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp(){
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
    }

    @Test
    void create_shouldDeleteOldTokensAndSaveNewOne() {
            // Arrange
            User user = new User();
            // (ดู field ของ User ว่าต้อง set อะไรไหม หรือ new เปล่าๆ พอ)

            // Act
            String plainToken = refreshTokenService.create(user);
            
            // Assert
            verify(refreshTokenRepository).deleteAllByUser(user);

            ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class); // ดักเอาค่าออกมาจาก class
            verify(refreshTokenRepository).save(captor.capture()); //เอาไว้ดูว่า method นั้นทำงานหรือป่าว
            RefreshToken saved = captor.getValue();

            assertEquals(saved.getUser(),user);
            assertNotEquals(saved.getToken(),plainToken);

    }

    @Test
    void validate_whenTokenNotFound_shouldThrowInvalid(){
        when(refreshTokenRepository.findByTokenAndRevokedFalse(anyString()))
            .thenReturn(Optional.empty());
    
        // Act + Assert
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> refreshTokenService.validate("some-plain-token"));
        assertEquals("INVALID_REFRESH_TOKEN", ex.getMessage());
    }
    
    @Test
    void validate_whenTokenExpired_shouldThrowExpiredAndRevoke(){
        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
    
        when(refreshTokenRepository.findByTokenAndRevokedFalse(anyString()))
            .thenReturn(Optional.of(expiredToken));
    
        // Act + Assert
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> refreshTokenService.validate("some-plain-token"));
        assertEquals("REFRESH_TOKEN_EXPIRED", ex.getMessage());
    
        assertTrue(expiredToken.isRevoked());
        verify(refreshTokenRepository).save(expiredToken);
    }

    @Test
    void validate_whenTokenValid_shouldReturnToken(){
        RefreshToken validToken = new RefreshToken();
        validToken.setExpiresAt(LocalDateTime.now().plusMinutes(5));

        when(refreshTokenRepository.findByTokenAndRevokedFalse(anyString()))
            .thenReturn(Optional.of(validToken));
            
        RefreshToken result = refreshTokenService.validate("some-plain-token");

        assertEquals(validToken, result);   // ได้ entity ตัวเดิมกลับมา

        verify(refreshTokenRepository, never()).save(any());   // ไม่มีการ save เลย เพราะไม่มีอะไรเปลี่ยน
    }
    @Test
    void revokeAll_deletesEveryTokenOfTheUser() {
        User user = new User();
        refreshTokenService.revokeAll(user);
        verify(refreshTokenRepository).deleteAllByUser(user);
    }

    @Test
    void revoke_shouldMarkTokenAsRevokedAndSave(){
        RefreshToken revokeToken = new RefreshToken();
        revokeToken.setRevoked(false);
    
        refreshTokenService.revoke(revokeToken);   // Act — ไม่ต้องรับค่าอะไรกลับมา
    
        assertTrue(revokeToken.isRevoked());       // เช็คว่า field เปลี่ยนเป็น true แล้ว
        verify(refreshTokenRepository).save(revokeToken);
    }

   @Test
    void rotate_shouldDeleteOldTokenAndSaveNewOne() {
        // Arrange
        User user = new User();
        RefreshToken oldToken = new RefreshToken();
        oldToken.setUser(user);
        oldToken.setToken("old-hash-value");

        // Act
        String newPlainToken = refreshTokenService.rotate(oldToken);

        // Assert
        assertNotNull(newPlainToken);

        verify(refreshTokenRepository).deleteByToken("old-hash-value");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken savedNewToken = captor.getValue();

        assertEquals(user, savedNewToken.getUser());
        assertNotEquals(oldToken.getToken(), savedNewToken.getToken());
        assertNotEquals(newPlainToken, savedNewToken.getToken());
    }
}
