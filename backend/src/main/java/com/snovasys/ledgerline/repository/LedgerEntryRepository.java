package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.domain.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Append-only: only save and read methods are declared. */
public interface LedgerEntryRepository extends Repository<LedgerEntry, UUID> {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByTransactionIdOrderByEntryTypeAsc(UUID transactionId);

    Page<LedgerEntry> findByAccountId(UUID accountId, Pageable pageable);
}
