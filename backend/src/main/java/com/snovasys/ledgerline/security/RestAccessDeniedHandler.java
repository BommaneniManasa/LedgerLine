package com.snovasys.ledgerline.security;

import com.snovasys.ledgerline.domain.AuditAction;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.service.AuditActor;
import com.snovasys.ledgerline.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Logged-in user hit an endpoint their role may not use: record it in the audit trail and answer 403. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final AuditService auditService;

    public RestAccessDeniedHandler(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        AuditActor actor = CurrentUser.get().map(AuditActor::of).orElse(AuditActor.anonymous(null));
        auditService.recordIndependent(AuditAction.AUTHZ_DENIED, AuditOutcome.DENIED, actor,
                "ENDPOINT", null, Map.of("method", request.getMethod(), "path", request.getRequestURI()));

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":403,\"code\":\"FORBIDDEN\","
                + "\"message\":\"You do not have permission to perform this action\"}";
        response.getWriter().write(body);
    }
}
