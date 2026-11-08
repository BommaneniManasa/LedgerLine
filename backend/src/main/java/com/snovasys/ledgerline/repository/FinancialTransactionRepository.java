package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.domain.FinancialTransaction;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Deliberately extends the bare Repository (not JpaRepository) so that no delete/update
 * methods exist for financial transactions at all.
 */
public interface FinancialTransactionRepository extends Repository<FinancialTransaction, UUID> {

    FinancialTransaction save(FinancialTransaction transaction);

    Optional<FinancialTransaction> findById(UUID id);

    Page<FinancialTransaction> findAll(Pageable pageable);

    @Query("select t from FinancialTransaction t "
            + "where t.sourceAccountId in :accountIds or t.destinationAccountId in :accountIds")
    Page<FinancialTransaction> findInvolving(@Param("accountIds") Collection<UUID> accountIds, Pageable pageable);
}
