
package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.config.AppProperties;
import com.snovasys.ledgerline.domain.Account;
import com.snovasys.ledgerline.domain.AccountStatus;
import com.snovasys.ledgerline.domain.AccountType;
import com.snovasys.ledgerline.domain.AuditAction;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.domain.EntryType;
import com.snovasys.ledgerline.domain.FinancialTransaction;
import com.snovasys.ledgerline.domain.LedgerEntry;
import com.snovasys.ledgerline.domain.TransactionStatus;
import com.snovasys.ledgerline.domain.TransactionType;
import com.snovasys.ledgerline.exception.BusinessRuleException;
import com.snovasys.ledgerline.exception.NotFoundException;
import com.snovasys.ledgerline.repository.AccountRepository;
import com.snovasys.ledgerline.repository.FinancialTransactionRepository;
import com.snovasys.ledgerline.repository.LedgerEntryRepository;
import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.web.dto.PageResponse;
import com.snovasys.ledgerline.web.dto.TransactionDtos.MoneyRequest;
import com.snovasys.ledgerline.web.dto.TransactionDtos.TransactionResult;
import com.snovasys.ledgerline.web.dto.TransactionDtos.TransactionView;
import com.snovasys.ledgerline.web.dto.TransactionDtos.TransferRequest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All money movement lives here. Every public write method is ONE database transaction:
 * claim idempotency key -> lock accounts -> validate -> update balances -> transaction row
 * -> debit entry + credit entry -> audit event -> commit. Any exception rolls back everything.
 */
@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final FinancialTransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyService idempotencyService;
    private final AccountService accountService;
    private final AuditService auditService;
    private final AppProperties properties;
    private final EntityManager entityManager;

    public TransactionService(AccountRepository accountRepository,
                              FinancialTransactionRepository transactionRepository,
                              LedgerEntryRepository ledgerEntryRepository,
                              IdempotencyService idempotencyService,
                              AccountService accountService,
                              AuditService auditService,
                              AppProperties properties,
                              EntityManager entityManager) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyService = idempotencyService;
        this.accountService = accountService;
        this.auditService = auditService;
        this.properties = properties;
        this.entityManager = entityManager;
    }

    // ---------------------------------------------------------------- deposit / withdraw / transfer

    @Transactional
    public TransactionResult deposit(AuthUser caller, String idempotencyKey, MoneyRequest request) {
        Account own = accountService.requireOwned(caller, request.accountId());

        BigDecimal amount = MoneyUtil.normalize(
                request.amount(), properties.money().maxTransactionAmount());

        String currency = accountService.normalizeCurrency(request.currency());
        Account systemAccount = systemAccountFor(currency);

        String hash = IdempotencyService.fingerprint(
                "DEPOSIT",
                own.getId().toString(),
                "",
                amount.toPlainString(),
                currency,
                nullToEmpty(request.description()));

        // Detach previously loaded accounts so the lock query obtains fresh entity versions.
        entityManager.detach(own);
        entityManager.detach(systemAccount);

        // Money leaves the clearing account and enters the customer account.
        return execute(
                caller,
                idempotencyKey,
                hash,
                TransactionType.DEPOSIT,
                systemAccount.getId(),
                own.getId(),
                amount,
                currency,
                request.description(),
                AuditAction.DEPOSIT);
    }

    @Transactional
    public TransactionResult withdraw(AuthUser caller, String idempotencyKey, MoneyRequest request) {
        Account own = accountService.requireOwned(caller, request.accountId());

        BigDecimal amount = MoneyUtil.normalize(
                request.amount(), properties.money().maxTransactionAmount());

        String currency = accountService.normalizeCurrency(request.currency());
        Account systemAccount = systemAccountFor(currency);

        String hash = IdempotencyService.fingerprint(
                "WITHDRAWAL",
                own.getId().toString(),
                "",
                amount.toPlainString(),
                currency,
                nullToEmpty(request.description()));

        // Detach previously loaded accounts so the lock query obtains fresh entity versions.
        entityManager.detach(own);
        entityManager.detach(systemAccount);

        // Money leaves the customer account and enters the clearing account.
        return execute(
                caller,
                idempotencyKey,
                hash,
                TransactionType.WITHDRAWAL,
                own.getId(),
                systemAccount.getId(),
                amount,
                currency,
                request.description(),
                AuditAction.WITHDRAWAL);
    }

    @Transactional
    public TransactionResult transfer(AuthUser caller, String idempotencyKey, TransferRequest request) {
        Account source = accountService.requireOwned(caller, request.sourceAccountId());

        BigDecimal amount = MoneyUtil.normalize(
                request.amount(), properties.money().maxTransactionAmount());

        String currency = accountService.normalizeCurrency(request.currency());

        Account destination = accountRepository.findByAccountNumber(
                        request.destinationAccountNumber())
                .filter(a -> a.getAccountType() == AccountType.CUSTOMER)
                .orElseThrow(() -> new BusinessRuleException(
                        "DESTINATION_UNAVAILABLE",
                        "Destination account is not available"));

        if (destination.getId().equals(source.getId())) {
            throw new BusinessRuleException(
                    "SAME_ACCOUNT",
                    "Source and destination must be different accounts");
        }

        String hash = IdempotencyService.fingerprint(
                "TRANSFER",
                source.getId().toString(),
                destination.getId().toString(),
                amount.toPlainString(),
                currency,
                nullToEmpty(request.description()));

        // Detach previously loaded accounts before acquiring pessimistic locks.
        entityManager.detach(source);
        entityManager.detach(destination);

        return execute(
                caller,
                idempotencyKey,
                hash,
                TransactionType.TRANSFER,
                source.getId(),
                destination.getId(),
                amount,
                currency,
                request.description(),
                AuditAction.TRANSFER);
    }

    /** The shared, atomic core. sourceId is DEBITED, destinationId is CREDITED. */
    private TransactionResult execute(
            AuthUser caller,
            String idempotencyKey,
            String requestHash,
            TransactionType type,
            UUID sourceId,
            UUID destinationId,
            BigDecimal amount,
            String currency,
            String description,
            AuditAction auditAction) {

        // 1. Idempotency: a retry of an already-completed request returns the original result.
        Optional<UUID> previous = idempotencyService.claim(
                caller.id(), idempotencyKey, requestHash);

        if (previous.isPresent()) {
            FinancialTransaction original = transactionRepository.findById(previous.get())
                    .orElseThrow(() -> new NotFoundException("Transaction not found"));

            return new TransactionResult(toView(original, Set.of()), true);
        }

        // 2. Lock both accounts in ID order to prevent opposite-transfer deadlocks.
        List<Account> locked = accountRepository.lockAllById(
                List.of(sourceId, destinationId));

        Account source = find(locked, sourceId);
        Account destination = find(locked, destinationId);

        // 3. Validate against the locked account rows.
        if (!currency.equals(source.getCurrency())
                || !currency.equals(destination.getCurrency())) {
            throw new BusinessRuleException(
                    "CURRENCY_MISMATCH",
                    "Currency does not match the account currency");
        }

        requireActive(source);
        requireActive(destination);

        if (!source.isSystem()
                && source.getBalance().compareTo(amount) < 0) {
            throw new BusinessRuleException(
                    "INSUFFICIENT_FUNDS",
                    "Insufficient funds");
        }

        // 4. Move the money.
        source.debit(amount);
        destination.credit(amount);

        // 5. Save transaction and exactly one debit and one credit entry.
        FinancialTransaction transaction = transactionRepository.save(
                new FinancialTransaction(
                        newReference(),
                        type,
                        TransactionStatus.COMPLETED,
                        sourceId,
                        destinationId,
                        amount,
                        currency,
                        cleanDescription(description),
                        caller.id()));

        ledgerEntryRepository.save(
                new LedgerEntry(
                        transaction.getId(),
                        sourceId,
                        EntryType.DEBIT,
                        amount,
                        currency,
                        source.getBalance()));

        ledgerEntryRepository.save(
                new LedgerEntry(
                        transaction.getId(),
                        destinationId,
                        EntryType.CREDIT,
                        amount,
                        currency,
                        destination.getBalance()));

        // 6. Attach idempotency record and write audit event in the same transaction.
        idempotencyService.attachTransaction(
                caller.id(), idempotencyKey, transaction.getId());

        auditService.record(
                auditAction,
                AuditOutcome.SUCCESS,
                AuditActor.of(caller),
                "TRANSACTION",
                transaction.getId().toString(),
                Map.of(
                        "reference", transaction.getReference(),
                        "amount", amount.toPlainString(),
                        "currency", currency));

        // 7. Commit happens when this method returns.
        Map<UUID, Account> accounts = Map.of(
                sourceId, source,
                destinationId, destination);

        return new TransactionResult(
                toView(transaction, accounts, Set.of(), caller.id()),
                false);
    }

    // ---------------------------------------------------------------- reading

    /** Customer history: only transactions that touch the caller's own accounts. */
    @Transactional(readOnly = true)
    public PageResponse<TransactionView> history(
            AuthUser caller, UUID accountId, int page, int size) {

        List<Account> mine = accountService.listOwned(caller);

        Set<UUID> myIds = mine.stream()
                .map(Account::getId)
                .collect(Collectors.toSet());

        List<UUID> filter;

        if (accountId != null) {
            accountService.requireOwned(caller, accountId);
            filter = List.of(accountId);
        } else {
            filter = List.copyOf(myIds);
        }

        if (filter.isEmpty()) {
            return PageResponse.empty(page, size);
        }

        Page<FinancialTransaction> result = transactionRepository.findInvolving(
                filter,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<UUID, Account> accounts = loadAccounts(result.getContent());

        return PageResponse.of(
                result,
                t -> toView(t, accounts, myIds, caller.id()));
    }

    @Transactional(readOnly = true)
    public TransactionView getOne(AuthUser caller, UUID transactionId) {
        FinancialTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));

        Set<UUID> myIds = accountService.listOwned(caller).stream()
                .map(Account::getId)
                .collect(Collectors.toSet());

        if (!myIds.contains(transaction.getSourceAccountId())
                && !myIds.contains(transaction.getDestinationAccountId())) {

            auditService.recordIndependent(
                    AuditAction.AUTHZ_DENIED,
                    AuditOutcome.DENIED,
                    AuditActor.of(caller),
                    "TRANSACTION",
                    transactionId.toString(),
                    Map.of("reason", "not_participant"));

            throw new NotFoundException("Transaction not found");
        }

        return toView(
                transaction,
                loadAccounts(List.of(transaction)),
                myIds,
                caller.id());
    }

    /** All transactions (auditor view). */
    @Transactional(readOnly = true)
    public PageResponse<TransactionView> listAll(int page, int size) {
        Page<FinancialTransaction> result = transactionRepository.findAll(
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<UUID, Account> accounts = loadAccounts(result.getContent());

        return PageResponse.of(
                result,
                t -> toView(t, accounts, Set.of(), null));
    }

    // ---------------------------------------------------------------- helpers

    private Account systemAccountFor(String currency) {
        return accountRepository.findByAccountTypeAndCurrency(
                        AccountType.SYSTEM, currency)
                .orElseThrow(() -> new BusinessRuleException(
                        "UNSUPPORTED_CURRENCY",
                        "Currency is not supported"));
    }

    private static Account find(List<Account> accounts, UUID id) {
        return accounts.stream()
                .filter(account -> account.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    private static void requireActive(Account account) {
        if (!account.isSystem()
                && account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "ACCOUNT_NOT_ACTIVE",
                    "The account is not active");
        }
    }

    private Map<UUID, Account> loadAccounts(
            List<FinancialTransaction> transactions) {

        Set<UUID> ids = new HashSet<>();

        for (FinancialTransaction transaction : transactions) {
            ids.add(transaction.getSourceAccountId());
            ids.add(transaction.getDestinationAccountId());
        }

        Map<UUID, Account> map = new HashMap<>();

        accountRepository.findAllById(ids)
                .forEach(account -> map.put(account.getId(), account));

        return map;
    }

    private TransactionView toView(
            FinancialTransaction transaction,
            Set<UUID> myAccountIds) {

        return toView(
                transaction,
                loadAccounts(List.of(transaction)),
                myAccountIds,
                null);
    }

    private TransactionView toView(
            FinancialTransaction transaction,
            Map<UUID, Account> accounts,
            Set<UUID> myAccountIds,
            UUID callerId) {

        Account source = accounts.get(transaction.getSourceAccountId());
        Account destination = accounts.get(transaction.getDestinationAccountId());

        String direction = null;

        if (callerId != null) {
            boolean outgoing = source != null
                    && callerId.equals(source.getUserId());

            boolean incoming = destination != null
                    && callerId.equals(destination.getUserId());

            direction = outgoing ? "OUT" : incoming ? "IN" : null;
        }

        return new TransactionView(
                transaction.getId(),
                transaction.getReference(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getDescription(),
                source == null ? null : source.getAccountNumber(),
                destination == null ? null : destination.getAccountNumber(),
                direction,
                transaction.getCreatedAt());
    }

    private static String newReference() {
        return "TXN-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase();
    }

    private static String cleanDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        return description.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
