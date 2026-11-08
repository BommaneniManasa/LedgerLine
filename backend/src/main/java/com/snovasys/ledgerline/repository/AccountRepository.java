package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.domain.Account;
import com.snovasys.ledgerline.domain.AccountStatus;
import com.snovasys.ledgerline.domain.AccountType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByUserId(UUID userId);

    long countByUserIdAndStatusNot(UUID userId, AccountStatus status);

    Optional<Account> findByAccountTypeAndCurrency(AccountType accountType, String currency);

    /**
     * Locks the given accounts (SELECT ... FOR UPDATE) in ascending id order.
     * Always locking in the same order means two opposite transfers (A->B and B->A)
     * can never deadlock each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id in :ids order by a.id")
    List<Account> lockAllById(@Param("ids") Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findLockedById(@Param("id") UUID id);
}
