package com.breadShop.XXI.service;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import com.breadShop.XXI.dto.log.UserActivityLogResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserActivityLog;
import com.breadShop.XXI.repository.UserActivityLogRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActivityLogServiceTest {

    @Mock
    private UserActivityLogRepository repo;

    private UserActivityLogService service;

    @BeforeEach
    void setUp() {
        service = new UserActivityLogService(repo);
    }

    @Test
    void log_shouldSaveActivity() {
        User user = new User();
        user.setUsername("peak");

        service.logSuccess(user, "LOGIN", "127.0.0.1", "chrome", "details");

        ArgumentCaptor<UserActivityLog> captor = ArgumentCaptor.forClass(UserActivityLog.class);
        verify(repo).save(captor.capture());
        assertEquals("LOGIN", captor.getValue().getAction());
        assertEquals("SUCCESS", captor.getValue().getStatus());
    }

    @Test
    void log_whenRepoThrows_shouldNotPropagateException() {
        doThrow(new RuntimeException("db down")).when(repo).save(any());

        assertDoesNotThrow(() -> service.logSuccess(null, "LOGIN", "ip", "ua", "d"));
    }

    @Test
    void logSuccess_withUser_shouldUseUsernameAndSuccessStatus() {
        User user = new User();
        user.setUsername("peak");

        service.logSuccess(user, "LOGIN", "127.0.0.1", "chrome", "details");

        ArgumentCaptor<UserActivityLog> captor = ArgumentCaptor.forClass(UserActivityLog.class);
        verify(repo).save(captor.capture());
        assertEquals("peak", captor.getValue().getUsername());
        assertEquals("SUCCESS", captor.getValue().getStatus());
    }

    @Test
    void logSuccess_withNullUser_shouldUseAnonymous() {
        service.logSuccess(null, "LOGIN", "127.0.0.1", "chrome", "details");

        ArgumentCaptor<UserActivityLog> captor = ArgumentCaptor.forClass(UserActivityLog.class);
        verify(repo).save(captor.capture());
        assertEquals("anonymous", captor.getValue().getUsername());
    }

    @Test
    void search_shouldReturnMappedPage() {
        UserActivityLog entity = new UserActivityLog(null, "peak", "LOGIN", "127.0.0.1", "ua", "d", "SUCCESS");
        when(repo.search(isNull(), eq("LOGIN"), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<UserActivityLogResponse> result = service.search(null, "LOGIN", null, null, null, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertEquals("LOGIN", result.getContent().get(0).action());
    }

    @Test
    void totalCount_shouldReturnRepoCount() {
        when(repo.count()).thenReturn(50L);

        assertEquals(50L, service.totalCount());
    }

    @Test
    void countByAction_shouldDelegateToRepo() {
        when(repo.countByAction("LOGIN")).thenReturn(9L);

        assertEquals(9L, service.countByAction("LOGIN"));
    }

    @Test
    void countActiveUsersToday_shouldDelegateToRepoWithStartOfDay() {
        when(repo.countActiveUsersSince(any(LocalDateTime.class))).thenReturn(3L);

        assertEquals(3L, service.countActiveUsersToday());
        verify(repo).countActiveUsersSince(any(LocalDateTime.class));
    }
}
