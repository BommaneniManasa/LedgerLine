package com.snovasys.ledgerline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.snovasys.ledgerline.domain.IdempotencyRecord;
import com.snovasys.ledgerline.exception.BadRequestException;
import com.snovasys.ledgerline.exception.BusinessRuleException;
import com.snovasys.ledgerline.exception.ConflictException;
import com.snovasys.ledgerline.repository.IdempotencyRecordRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class IdempotencyServiceTest {

    private static final String KEY = "key-12345678";

    private IdempotencyRecordRepository repository;
    private IdempotencyService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(IdempotencyRecordRepository.class);
        service = new IdempotencyService(repository);
    }

    private IdempotencyRecord record(String hash, UUID transactionId) {
        IdempotencyRecord r = BeanUtilsHelper.newRecord();
        ReflectionTestUtils.setField(r, "userId", userId);
        ReflectionTestUtils.setField(r, "idempotencyKey", KEY);
        ReflectionTestUtils.setField(r, "requestHash", hash);
        ReflectionTestUtils.setField(r, "transactionId", transactionId);
        return r;
    }

    @Test
    void firstRequestWithAKeyIsProcessed() {
        when(repository.claim(userId, KEY, "h1")).thenReturn(1);
        assertTrue(service.claim(userId, KEY, "h1").isEmpty());
    }

    @Test
    void retryWithSameKeyAndSamePayloadReturnsOriginalTransaction() {
        UUID txId = UUID.randomUUID();
        when(repository.claim(userId, KEY, "h1")).thenReturn(0);
        when(repository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.of(record("h1", txId)));
        assertEquals(Optional.of(txId), service.claim(userId, KEY, "h1"));
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejected() {
        when(repository.claim(userId, KEY, "h2")).thenReturn(0);
        when(repository.findByUserIdAndIdempotencyKey(userId, KEY))
                .thenReturn(Optional.of(record("h1", UUID.randomUUID())));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> service.claim(userId, KEY, "h2"));
        assertEquals("IDEMPOTENCY_KEY_REUSED", ex.getCode());
    }

    @Test
    void keyWithoutFinishedTransactionIsReportedAsInProgress() {
        when(repository.claim(userId, KEY, "h1")).thenReturn(0);
        when(repository.findByUserIdAndIdempotencyKey(userId, KEY)).thenReturn(Optional.of(record("h1", null)));
        assertThrows(ConflictException.class, () -> service.claim(userId, KEY, "h1"));
    }

    @Test
    void malformedKeysAreRejected() {
        assertThrows(BadRequestException.class, () -> service.claim(userId, null, "h"));
        assertThrows(BadRequestException.class, () -> service.claim(userId, "short", "h"));
        assertThrows(BadRequestException.class, () -> service.claim(userId, "has spaces in it!", "h"));
        assertThrows(BadRequestException.class, () -> service.claim(userId, "x".repeat(101), "h"));
    }

    @Test
    void fingerprintIsStableAndSensitiveToEveryField() {
        String a = IdempotencyService.fingerprint("TRANSFER", "s", "d", "10.00", "INR", "");
        assertEquals(a, IdempotencyService.fingerprint("TRANSFER", "s", "d", "10.00", "INR", ""));
        assertEquals(64, a.length());
        assertNotEquals(a, IdempotencyService.fingerprint("TRANSFER", "s", "d", "10.01", "INR", ""));
        assertNotEquals(a, IdempotencyService.fingerprint("TRANSFER", "s", "x", "10.00", "INR", ""));
        assertNotEquals(a, IdempotencyService.fingerprint("DEPOSIT", "s", "d", "10.00", "INR", ""));
    }

    /** IdempotencyRecord has a protected constructor (JPA); this creates one via reflection for tests. */
    static final class BeanUtilsHelper {
        static IdempotencyRecord newRecord() {
            try {
                var ctor = IdempotencyRecord.class.getDeclaredConstructor();
                ctor.setAccessible(true);
                return ctor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
