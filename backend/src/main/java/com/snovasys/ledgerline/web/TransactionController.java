package com.snovasys.ledgerline.web;

import com.snovasys.ledgerline.domain.AuditAction;
import com.snovasys.ledgerline.domain.AuditOutcome;
import com.snovasys.ledgerline.domain.TransactionType;
import com.snovasys.ledgerline.exception.ApiException;
import com.snovasys.ledgerline.security.AuthUser;
import com.snovasys.ledgerline.service.AuditActor;
import com.snovasys.ledgerline.service.AuditService;
import com.snovasys.ledgerline.service.TransactionService;
import com.snovasys.ledgerline.web.dto.PageResponse;
import com.snovasys.ledgerline.web.dto.TransactionDtos.MoneyRequest;
import com.snovasys.ledgerline.web.dto.TransactionDtos.TransactionResult;
import com.snovasys.ledgerline.web.dto.TransactionDtos.TransactionView;
import com.snovasys.ledgerline.web.dto.TransactionDtos.TransferRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** CUSTOMER only. Every money-moving endpoint REQUIRES an Idempotency-Key header. */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TransactionService transactionService;
    private final AuditService auditService;

    public TransactionController(TransactionService transactionService, AuditService auditService) {
        this.transactionService = transactionService;
        this.auditService = auditService;
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionView> deposit(@AuthenticationPrincipal AuthUser user,
                                                   @RequestHeader(IDEMPOTENCY_HEADER) String key,
                                                   @Valid @RequestBody MoneyRequest request) {
        return run(user, TransactionType.DEPOSIT, () -> transactionService.deposit(user, key, request));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionView> withdraw(@AuthenticationPrincipal AuthUser user,
                                                    @RequestHeader(IDEMPOTENCY_HEADER) String key,
                                                    @Valid @RequestBody MoneyRequest request) {
        return run(user, TransactionType.WITHDRAWAL, () -> transactionService.withdraw(user, key, request));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionView> transfer(@AuthenticationPrincipal AuthUser user,
                                                    @RequestHeader(IDEMPOTENCY_HEADER) String key,
                                                    @Valid @RequestBody TransferRequest request) {
        return run(user, TransactionType.TRANSFER, () -> transactionService.transfer(user, key, request));
    }

    @GetMapping
    public PageResponse<TransactionView> history(@AuthenticationPrincipal AuthUser user,
                                                 @RequestParam(required = false) UUID accountId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return transactionService.history(user, accountId, Paging.page(page), Paging.size(size));
    }

    @GetMapping("/{id}")
    public TransactionView get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return transactionService.getOne(user, id);
    }

    /**
     * 201 for a new transaction, 200 + "Idempotent-Replayed: true" when the key was already used.
     * A rejected request (insufficient funds, ...) is written to the audit trail in its own transaction,
     * after the money transaction has been rolled back.
     */
    private ResponseEntity<TransactionView> run(AuthUser user, TransactionType type, Supplier<TransactionResult> action) {
        try {
            TransactionResult result = action.get();
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED);
            if (result.replayed()) {
                builder.header("Idempotent-Replayed", "true");
            }
            return builder.body(result.transaction());
        } catch (ApiException e) {
            auditService.recordIndependent(AuditAction.TRANSACTION_REJECTED, AuditOutcome.FAILURE,
                    AuditActor.of(user), "TRANSACTION", null,
                    Map.of("type", type.name(), "code", e.getCode()));
            throw e;
        }
    }
}
