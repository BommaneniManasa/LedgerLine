package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.domain.AuditAction;
import com.snovasys.ledgerline.domain.AuditEvent;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.repository.AuditEventRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Writes audit events. There are two ways to write one:
 * - record(...)            joins the caller's transaction, so a financial change and its audit row
 *                          commit (or roll back) together;
 * - recordIndependent(...) uses its own transaction, so "something was rejected / denied / failed"
 *                          is kept even though the business transaction rolled back.
 * This class has no update or delete method, and the database blocks them as well.
 */
@Service
public class AuditService {

    private static final int MAX_VALUE_LENGTH = 200;
    private static final int MAX_ENTRIES = 20;
    /** Metadata keys containing these words are dropped, so secrets cannot end up in the audit trail by mistake. */
    private static final Set<String> FORBIDDEN_KEY_PARTS =
            Set.of("password", "token", "secret", "jwt", "authorization", "hash");

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(AuditAction action, AuditOutcome outcome, AuditActor actor,
                       String targetType, String targetId, Map<String, ?> metadata) {
        save(action, outcome, actor, targetType, targetId, metadata);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(AuditAction action, AuditOutcome outcome, AuditActor actor,
                                  String targetType, String targetId, Map<String, ?> metadata) {
        save(action, outcome, actor, targetType, targetId, metadata);
    }

    private void save(AuditAction action, AuditOutcome outcome, AuditActor actor,
                      String targetType, String targetId, Map<String, ?> metadata) {
        AuditEvent event = new AuditEvent(
                actor.userId(),
                limit(actor.email(), 254),
                actor.role(),
                action.name(),
                outcome,
                targetType,
                limit(targetId, 64),
                currentIp(),
                toJson(metadata));
        repository.save(event);
    }

    private static String currentIp() {
        // getRemoteAddr() is the real TCP peer. X-Forwarded-For is NOT trusted because a client can forge it.
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return limit(attrs.getRequest().getRemoteAddr(), 45);
        }
        return null;
    }

    /** Builds a small JSON object by hand: sensitive keys removed, values length-limited and escaped. */
    static String toJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return metadata.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .filter(e -> !isSensitiveKey(e.getKey()))
                .limit(MAX_ENTRIES)
                .map(e -> "\"" + escape(e.getKey()) + "\":\"" + escape(limit(String.valueOf(e.getValue()), MAX_VALUE_LENGTH)) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        return FORBIDDEN_KEY_PARTS.stream().anyMatch(lower::contains);
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
