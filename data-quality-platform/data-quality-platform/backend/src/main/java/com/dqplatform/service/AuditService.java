package com.dqplatform.service;

import com.dqplatform.entity.AuditLog;
import com.dqplatform.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(String username, String action, String entityType, String entityId, String details) {
        repository.save(AuditLog.builder()
                .username(username).action(action).entityType(entityType)
                .entityId(entityId).details(details).createdAt(LocalDateTime.now()).build());
    }
}
