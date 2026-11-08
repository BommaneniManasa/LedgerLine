package com.snovasys.ledgerline.web;

import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.service.AuditQueryService;
import com.snovasys.ledgerline.web.dto.AdminDtos.AuditEventView;
import com.snovasys.ledgerline.web.dto.PageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN and AUDITOR may READ the audit trail. There is deliberately no POST/PUT/PATCH/DELETE here,
 * and the database refuses UPDATE/DELETE/TRUNCATE on the table anyway.
 */
@RestController
@RequestMapping("/api/audit-events")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public PageResponse<AuditEventView> list(@AuthenticationPrincipal AuthUser user,
                                             @RequestParam(required = false) String action,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return auditQueryService.list(user, action, Paging.page(page), Paging.size(size));
    }
}
