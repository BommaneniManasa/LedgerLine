package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.exception.BusinessRuleException;
import java.math.BigDecimal;

/** Money rules in one place. Money is always BigDecimal with exactly 2 decimals, never double/float. */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    /**
     * Validates an amount and returns it with scale 2.
     * Rejects null, zero, negative, more than 2 decimals, and anything above the configured maximum.
     */
    public static BigDecimal normalize(BigDecimal amount, BigDecimal max) {
        if (amount == null) {
            throw new BusinessRuleException("INVALID_AMOUNT", "Amount is required");
        }
        if (amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_AMOUNT", "Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("INVALID_AMOUNT", "Amount can have at most 2 decimal places");
        }
        BigDecimal normalized = amount.setScale(2);
        if (normalized.compareTo(max) > 0) {
            throw new BusinessRuleException("AMOUNT_TOO_LARGE", "Amount exceeds the maximum allowed per transaction");
        }
        return normalized;
    }
}
