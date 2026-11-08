package com.snovasys.ledgerline.repository;

import com.snovasys.ledgerline.domain.AuditEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Append-only: there is intentionally no delete or update method here. */
public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    AuditEvent save(AuditEvent event);

    Page<AuditEvent> findAll(Pageable pageable);

    Page<AuditEvent> findByAction(String action, Pageable pageable);
}
