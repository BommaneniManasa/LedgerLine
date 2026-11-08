package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.repository.AccountRepository;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Creates unpredictable account numbers like LL123456789012 (SecureRandom, not a counter). */
@Component
public class AccountNumberGenerator {

    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final AccountRepository accountRepository;

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String next() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = format(random.nextLong(1_000_000_000_000L));
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        // The UNIQUE constraint in the database is the final guard; this is practically unreachable.
        throw new IllegalStateException("Could not generate a unique account number");
    }

    static String format(long twelveDigits) {
        return String.format("LL%012d", twelveDigits);
    }
}
