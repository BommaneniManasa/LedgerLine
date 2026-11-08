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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** A completed money movement. Immutable: the database also rejects UPDATE/DELETE. */
@Entity
@Immutable
@Table(name = "financial_transactions")
public class FinancialTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    /** Account that is debited. */
    @Column(nullable = false)
    private UUID sourceAccountId;

    /** Account that is credited. */
    @Column(nullable = false)
    private UUID destinationAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    private String description;

    @Column(nullable = false)
    private UUID initiatedBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected FinancialTransaction() {
        // required by JPA
    }

    public FinancialTransaction(String reference, TransactionType type, TransactionStatus status,
                                UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount,
                                String currency, String description, UUID initiatedBy) {
        this.reference = reference;
        this.type = type;
        this.status = status;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.initiatedBy = initiatedBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public TransactionType getType() { return type; }
    public TransactionStatus getStatus() { return status; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public UUID getInitiatedBy() { return initiatedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
