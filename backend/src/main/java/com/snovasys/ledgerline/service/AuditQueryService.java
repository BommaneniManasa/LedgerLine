package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.domain.AuditAction;
import com.snovasys.ledgerline.domain.AuditEvent;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.repository.AuditEventRepository;
import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.web.dto.AdminDtos.AuditEventView;
import com.snovasys.ledgerline.web.dto.PageResponse;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Read-only access to the audit trail for ADMIN and AUDITOR. */
@Service
public class AuditQueryService {

    private final AuditEventRepository repository;
    private final AuditService auditService;

    public AuditQueryService(AuditEventRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    public PageResponse<AuditEventView> list(AuthUser caller, String action, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<AuditEvent> result = (action == null || action.isBlank())
                ? repository.findAll(pageable)
                : repository.findByAction(action.trim().toUpperCase(), pageable);
        // Reading the audit trail is itself recorded (in its own transaction).
        auditService.recordIndependent(AuditAction.AUDIT_LOG_VIEWED, AuditOutcome.SUCCESS, AuditActor.of(caller),
                "AUDIT_LOG", null, Map.of("page", String.valueOf(page), "filter", action == null ? "" : action));
        return PageResponse.of(result, AuditEventView::from);
    }
}
