package com.snovasys.ledgerline.web.dto;

import com.snovasys.ledgerline.domain.Account;
import com.snovasys.ledgerline.domain.AccountStatus;
import com.snovasys.ledgerline.domain.AccountType;
import com.snovasys.ledgerline.domain.EntryType;
import com.snovasys.ledgerline.domain.LedgerEntry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record CreateAccountRequest(@NotBlank @Size(min = 3, max = 3) String currency) {
    }

    public record StatusRequest(@NotNull AccountStatus status) {
    }

    /** What a customer sees about their own account. */
    public record AccountView(UUID id, String accountNumber, String currency, BigDecimal balance,
                              AccountStatus status, Instant createdAt) {

        public static AccountView from(Account a) {
            return new AccountView(a.getId(), a.getAccountNumber(), a.getCurrency(), a.getBalance(),
                    a.getStatus(), a.getCreatedAt());
        }
    }

    /** What admins and auditors see (includes owner id and account type). */
    public record AccountAdminView(UUID id, String accountNumber, AccountType accountType, UUID ownerUserId,
                                   String currency, BigDecimal balance, AccountStatus status, Instant createdAt) {

        public static AccountAdminView from(Account a) {
            return new AccountAdminView(a.getId(), a.getAccountNumber(), a.getAccountType(), a.getUserId(),
                    a.getCurrency(), a.getBalance(), a.getStatus(), a.getCreatedAt());
        }
    }

    public record LedgerEntryView(UUID id, UUID transactionId, UUID accountId, EntryType entryType,
                                  BigDecimal amount, String currency, BigDecimal balanceAfter, Instant createdAt) {

        public static LedgerEntryView from(LedgerEntry e) {
            return new LedgerEntryView(e.getId(), e.getTransactionId(), e.getAccountId(), e.getEntryType(),
                    e.getAmount(), e.getCurrency(), e.getBalanceAfter(), e.getCreatedAt());
        }
    }
}
