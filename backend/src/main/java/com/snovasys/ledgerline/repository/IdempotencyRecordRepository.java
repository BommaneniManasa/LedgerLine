package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.domain.IdempotencyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Atomically claims (user, key). Returns 1 if this request is the first one, 0 if the key already exists.
     * If another transaction is still inserting the same key, PostgreSQL makes this statement WAIT until
     * that transaction commits or rolls back - which is exactly what makes concurrent retries safe.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO idempotency_records (id, user_id, idempotency_key, request_hash, created_at) "
            + "VALUES (gen_random_uuid(), :userId, :key, :hash, now()) "
            + "ON CONFLICT (user_id, idempotency_key) DO NOTHING", nativeQuery = true)
    int claim(@Param("userId") UUID userId, @Param("key") String key, @Param("hash") String hash);

    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Modifying(flushAutomatically = true)
    @Query("update IdempotencyRecord r set r.transactionId = :txId "
            + "where r.userId = :userId and r.idempotencyKey = :key")
    int attachTransaction(@Param("userId") UUID userId, @Param("key") String key, @Param("txId") UUID txId);
}
