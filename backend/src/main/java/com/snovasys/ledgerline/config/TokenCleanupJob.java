package com.snovasys.ledgerline.config;

import com.snovasys.ledgerline.repository.RevokedTokenRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Removes revoked-token rows whose token has expired anyway (an expired token is rejected by its exp claim). */
@Component
public class TokenCleanupJob {

    private final RevokedTokenRepository repository;

    public TokenCleanupJob(RevokedTokenRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purgeExpired() {
        repository.deleteExpired(Instant.now());
    }
}
