package com.breadShop.XXI.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.breadShop.XXI.dto.log.UserActivityLogResponse;
import com.breadShop.XXI.entity.User;
import com.breadShop.XXI.entity.UserActivityLog;
import com.breadShop.XXI.repository.UserActivityLogRepository;

@Service
public class UserActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(UserActivityLogService.class);

    private final UserActivityLogRepository repo;

    public UserActivityLogService(UserActivityLogRepository repo) {
        this.repo = repo;
    }

    public void log(User user, String username, String action,
                    String ipAddress, String userAgent, String details, String status) {
        try {
            repo.save(new UserActivityLog(user, username, action, ipAddress, userAgent, details, status));
        } catch (Exception e) {
            log.error("Failed to save UserActivityLog action={} user={}", action, username, e);
        }
    }

    public void logSuccess(User user, String action, String ipAddress, String userAgent, String details) {
        log(user, user != null ? user.getUsername() : "anonymous", action, ipAddress, userAgent, details, "SUCCESS");
    }

    public void logFailure(String username, String action, String ipAddress, String userAgent, String details) {
        log(null, username, action, ipAddress, userAgent, details, "FAILURE");
    }

    public Page<UserActivityLogResponse> search(
            Integer userId, String action, String status, String keyword,
            LocalDateTime start, LocalDateTime end, int page, int size) {
        return repo.search(userId, action, status, keyword, start, end, PageRequest.of(page, size))
                   .map(UserActivityLogResponse::from);
    }

    public long totalCount() {
        return repo.count();
    }

    public long countByAction(String action) {
        return repo.countByAction(action);
    }

    public long countActiveUsersToday() {
        return repo.countActiveUsersSince(LocalDateTime.now().toLocalDate().atStartOfDay());
    }
}
