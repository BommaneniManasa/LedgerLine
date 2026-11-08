package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.domain.IdempotencyRecord;
import com.snovasys.ledgerline.exception.BadRequestException;
import com.snovasys.ledgerline.exception.BusinessRuleException;
import com.snovasys.ledgerline.exception.ConflictException;
import com.snovasys.ledgerline.repository.IdempotencyRecordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes financial requests safe to retry.
 * claim() runs INSIDE the same database transaction as the money movement:
 *  - first request:  the key row is inserted; if the money movement later fails, the row is rolled back too,
 *                    so the client can retry with the same key;
 *  - retry:          the unique (user_id, key) constraint says "already used" and we return the original result;
 *  - concurrent retry: PostgreSQL makes the second insert wait until the first transaction finishes.
 */
@Service
public class IdempotencyService {

    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * @return empty if this is a new request (go ahead and process it), or the id of the transaction
     *         that the same request already produced.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<UUID> claim(UUID userId, String key, String requestHash) {
        validateKey(key);
        if (repository.claim(userId, key, requestHash) == 1) {
            return Optional.empty();
        }
        IdempotencyRecord existing = repository.findByUserIdAndIdempotencyKey(userId, key)
                .orElseThrow(() -> new ConflictException("IDEMPOTENCY_IN_PROGRESS",
                        "A request with this key is still being processed"));
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used for a different request");
        }
        if (existing.getTransactionId() == null) {
            throw new ConflictException("IDEMPOTENCY_IN_PROGRESS", "A request with this key is still being processed");
        }
        return Optional.of(existing.getTransactionId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void attachTransaction(UUID userId, String key, UUID transactionId) {
        repository.attachTransaction(userId, key, transactionId);
    }

    static void validateKey(String key) {
        if (key == null || !KEY_FORMAT.matcher(key).matches()) {
            throw new BadRequestException("INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must be 8-100 characters: letters, digits, '-' or '_'");
        }
    }

    /** SHA-256 of the request fields: detects the same key being reused with a different payload. */
    public static String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
