package com.breadShop.XXI.service;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.breadShop.XXI.dto.log.AuditLogResponse;
import com.breadShop.XXI.entity.AuditLog;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.AuditLogRepository;

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
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository repo;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(repo);
    }

    @Test
    void log_withActor_shouldSaveWithActorNameAndRole() {
        User actor = new User();
        ReflectionTestUtils.setField(actor, "id", 1);
        actor.setUsername("peak");
        actor.setRole("ADMIN");

        auditLogService.log(actor, "CREATE", "Product", "10", "old", "new", "details");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertEquals("peak", saved.getActorName());
        assertEquals("ADMIN", saved.getActorRole());
        assertEquals("CREATE", saved.getAction());
    }

    @Test
    void log_withNullActor_shouldSaveWithSystemDefaults() {
        auditLogService.log(null, "DELETE", "Product", "10", null, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertEquals("system", saved.getActorName());
        assertEquals("SYSTEM", saved.getActorRole());
    }

    @Test
    void log_whenRepoThrows_shouldNotPropagateException() {
        doThrow(new RuntimeException("db down")).when(repo).save(any());

        assertDoesNotThrow(() -> auditLogService.log(null, "CREATE", "Product", "1", null, null, null));
    }

    @Test
    void search_shouldReturnMappedPage() {
        AuditLog entity = new AuditLog(null, "system", "SYSTEM", "CREATE", "Product", "1", null, null, "d");
        when(repo.search(isNull(), eq("CREATE"), isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<AuditLogResponse> result = auditLogService.search(null, "CREATE", null, null, null, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertEquals("CREATE", result.getContent().get(0).action());
    }

    @Test
    void total_shouldReturnRepoCount() {
        when(repo.count()).thenReturn(42L);

        assertEquals(42L, auditLogService.total());
    }
}
