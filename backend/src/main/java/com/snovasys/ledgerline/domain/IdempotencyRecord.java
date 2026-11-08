package com.snovasys.ledgerline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Rows are inserted with native SQL (ON CONFLICT DO NOTHING); this entity is used for reading. */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private String requestHash;

    private UUID transactionId;

    @Column(nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
        // required by JPA
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public UUID getTransactionId() { return transactionId; }
    public Instant getCreatedAt() { return createdAt; }
}
