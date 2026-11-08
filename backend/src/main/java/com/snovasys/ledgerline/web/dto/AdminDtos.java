package com.snovasys.ledgerline.web.dto;

import com.snovasys.ledgerline.domain.AuditEvent;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.domain.Role;
import com.snovasys.ledgerline.domain.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank @Size(max = 100) String fullName,
            @NotNull Role role) {
    }

    public record UserStatusRequest(@NotNull UserStatus status) {
    }

    public record UserRoleRequest(@NotNull Role role) {
    }

    public record AuditEventView(UUID id, Instant occurredAt, UUID actorUserId, String actorEmail, String actorRole,
                                 String action, AuditOutcome outcome, String targetType, String targetId,
                                 String ipAddress, String metadata) {

        public static AuditEventView from(AuditEvent e) {
            return new AuditEventView(e.getId(), e.getOccurredAt(), e.getActorUserId(), e.getActorEmail(),
                    e.getActorRole(), e.getAction(), e.getOutcome(), e.getTargetType(), e.getTargetId(),
                    e.getIpAddress(), e.getMetadata());
        }
    }

    public record AccountMismatch(UUID accountId, String accountNumber, BigDecimal storedBalance,
                                  BigDecimal ledgerBalance) {
    }

    /** Result of re-checking the whole ledger from scratch. */
    public record IntegrityReport(boolean balanced, BigDecimal totalDebits, BigDecimal totalCredits,
                                  List<UUID> unbalancedTransactions, List<AccountMismatch> mismatchedAccounts,
                                  Instant checkedAt) {
    }
}
