package com.breadShop.XXI.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.breadShop.XXI.dto.log.AuditLogResponse;
import com.breadShop.XXI.entity.AuditLog;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.repository.AuditLogRepository;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repo;

    public AuditLogService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void log(User actor, String action, String entityType, String entityId,
                    String oldValue, String newValue, String details) {
        try {
            String actorName = actor != null ? actor.getUsername() : "system";
            String actorRole = actor != null ? actor.getRole() : "SYSTEM";
            repo.save(new AuditLog(actor, actorName, actorRole, action,
                                   entityType, entityId, oldValue, newValue, details));
        } catch (Exception e) {
            log.error("Failed to save AuditLog action={} entity={}/{}", action, entityType, entityId, e);
        }
    }

    public Page<AuditLogResponse> search(
            Integer actorId, String action, String entityType, String keyword,
            LocalDateTime start, LocalDateTime end, int page, int size) {
        return repo.search(actorId, action, entityType, keyword, start, end, PageRequest.of(page, size))
                   .map(AuditLogResponse::from);
    }

    public long total() {
        return repo.count();
    }
}
