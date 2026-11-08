package com.snovasys.ledgerline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Who did what to which target, and how it ended. Append-only (also enforced by DB triggers). */
@Entity
@Immutable
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Instant occurredAt;

    private UUID actorUserId;
    private String actorEmail;
    private String actorRole;

    @Column(nullable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditOutcome outcome;

    private String targetType;
    private String targetId;
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    protected AuditEvent() {
        // required by JPA
    }

    public AuditEvent(UUID actorUserId, String actorEmail, String actorRole, String action,
                      AuditOutcome outcome, String targetType, String targetId,
                      String ipAddress, String metadata) {
        this.actorUserId = actorUserId;
        this.actorEmail = actorEmail;
        this.actorRole = actorRole;
        this.action = action;
        this.outcome = outcome;
        this.targetType = targetType;
        this.targetId = targetId;
        this.ipAddress = ipAddress;
        this.metadata = metadata;
    }

    @PrePersist
    void onCreate() {
        occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getActorUserId() { return actorUserId; }
    public String getActorEmail() { return actorEmail; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public AuditOutcome getOutcome() { return outcome; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getIpAddress() { return ipAddress; }
    public String getMetadata() { return metadata; }
}
