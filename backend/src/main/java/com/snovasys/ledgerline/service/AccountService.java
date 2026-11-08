package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.config.AppProperties;
import com.snovasys.ledgerline.domain.Account;
import com.snovasys.ledgerline.domain.AccountStatus;
import com.snovasys.ledgerline.domain.AccountType;
import com.snovasys.ledgerline.domain.AuditAction;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.domain.Role;
import com.snovasys.ledgerline.domain.User;
import com.snovasys.ledgerline.domain.UserStatus;
import com.snovasys.ledgerline.exception.BusinessRuleException;
import com.snovasys.ledgerline.exception.NotFoundException;
import com.snovasys.ledgerline.repository.AccountRepository;
import com.snovasys.ledgerline.repository.LedgerEntryRepository;
import com.snovasys.ledgerline.repository.UserRepository;
import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.web.dto.AccountDtos.LedgerEntryView;
import com.snovasys.ledgerline.web.dto.PageResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountNumberGenerator numberGenerator;
    private final AuditService auditService;
    private final AppProperties properties;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository,
                          LedgerEntryRepository ledgerEntryRepository, AccountNumberGenerator numberGenerator,
                          AuditService auditService, AppProperties properties) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.numberGenerator = numberGenerator;
        this.auditService = auditService;
        this.properties = properties;
    }

    /** Opens a customer account for ownerId. The owner row is locked so the per-user limit cannot be bypassed in parallel. */
    @Transactional
    public Account createAccount(UUID ownerId, String currency, AuditActor actor) {
        String normalizedCurrency = normalizeCurrency(currency);
        User owner = userRepository.findLockedById(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (owner.getRole() != Role.CUSTOMER) {
            throw new BusinessRuleException("NOT_A_CUSTOMER", "Accounts can only be opened for customers");
        }
        if (owner.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("USER_NOT_ACTIVE", "The user is not active");
        }
        long open = accountRepository.countByUserIdAndStatusNot(ownerId, AccountStatus.CLOSED);
        if (open >= properties.money().maxAccountsPerUser()) {
            throw new BusinessRuleException("ACCOUNT_LIMIT_REACHED", "Maximum number of accounts reached");
        }
        Account account = accountRepository.save(
                new Account(numberGenerator.next(), ownerId, AccountType.CUSTOMER, normalizedCurrency));
        auditService.record(AuditAction.ACCOUNT_CREATED, AuditOutcome.SUCCESS, actor,
                "ACCOUNT", account.getId().toString(),
                Map.of("accountNumber", account.getAccountNumber(), "currency", normalizedCurrency,
                        "ownerId", ownerId.toString()));
        return account;
    }

    @Transactional(readOnly = true)
    public List<Account> listOwned(AuthUser caller) {
        return accountRepository.findByUserIdOrderByCreatedAtAsc(caller.id());
    }

    /**
     * THE ownership check (IDOR protection). Customers can only reach accounts where account.userId == their id.
     * For someone else's account we answer 404 (not 403) so the id's existence is not revealed,
     * and we write an AUTHZ_DENIED audit event in its own transaction.
     */
    @Transactional(readOnly = true)
    public Account requireOwned(AuthUser caller, UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (account.isSystem() || !caller.id().equals(account.getUserId())) {
            auditService.recordIndependent(AuditAction.AUTHZ_DENIED, AuditOutcome.DENIED, AuditActor.of(caller),
                    "ACCOUNT", accountId.toString(), Map.of("reason", "not_owner"));
            throw new NotFoundException("Account not found");
        }
        return account;
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryView> ledgerFor(AuthUser caller, UUID accountId, int page, int size) {
        requireOwned(caller, accountId);
        return PageResponse.of(
                ledgerEntryRepository.findByAccountId(accountId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                LedgerEntryView::from);
    }

    /** Admin only (URL rule + this method is only reachable from AdminController). */
    @Transactional
    public Account setStatus(AuthUser admin, UUID accountId, AccountStatus newStatus) {
        Account account = accountRepository.findLockedById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (account.isSystem()) {
            throw new BusinessRuleException("SYSTEM_ACCOUNT", "System accounts cannot be changed");
        }
        AccountStatus old = account.getStatus();
        if (old == AccountStatus.CLOSED) {
            throw new BusinessRuleException("ACCOUNT_CLOSED", "A closed account cannot be reopened");
        }
        if (newStatus == AccountStatus.CLOSED && account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleException("BALANCE_NOT_ZERO", "Only accounts with a zero balance can be closed");
        }
        account.setStatus(newStatus);
        auditService.record(AuditAction.ACCOUNT_STATUS_CHANGED, AuditOutcome.SUCCESS, AuditActor.of(admin),
                "ACCOUNT", accountId.toString(), Map.of("from", old.name(), "to", newStatus.name()));
        return account;
    }

    public String normalizeCurrency(String currency) {
        String c = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!properties.money().supportedCurrencies().contains(c)) {
            throw new BusinessRuleException("UNSUPPORTED_CURRENCY", "Currency is not supported");
        }
        return c;
    }
}
