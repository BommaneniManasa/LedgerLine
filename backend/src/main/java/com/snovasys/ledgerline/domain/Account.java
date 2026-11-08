package com.snovasys.ledgerline.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    /** Owner. Null only for SYSTEM accounts. */
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Version
    private long version;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Account() {
        // required by JPA
    }

    public Account(String accountNumber, UUID userId, AccountType accountType, String currency) {
        this.accountNumber = accountNumber;
        this.userId = userId;
        this.accountType = accountType;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Money leaves the account. Callers must lock the row and check funds first. */
    public void debit(BigDecimal amount) {
        balance = balance.subtract(amount);
    }

    /** Money enters the account. */
    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public boolean isSystem() {
        return accountType == AccountType.SYSTEM;
    }

    public UUID getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public UUID getUserId() { return userId; }
    public AccountType getAccountType() { return accountType; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(AccountStatus status) { this.status = status; }
}
