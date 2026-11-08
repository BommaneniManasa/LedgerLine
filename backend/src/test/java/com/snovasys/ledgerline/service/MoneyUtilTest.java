package com.snovasys.ledgerline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.snovasys.ledgerline.exception.BusinessRuleException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyUtilTest {

    private static final BigDecimal MAX = new BigDecimal("1000000.00");

    @Test
    void acceptsValidAmountAndFixesScaleToTwo() {
        assertEquals(new BigDecimal("10.50"), MoneyUtil.normalize(new BigDecimal("10.5"), MAX));
        assertEquals(new BigDecimal("10.00"), MoneyUtil.normalize(new BigDecimal("10"), MAX));
        assertEquals(new BigDecimal("0.01"), MoneyUtil.normalize(new BigDecimal("0.01"), MAX));
    }

    @Test
    void rejectsNullZeroAndNegative() {
        assertThrows(BusinessRuleException.class, () -> MoneyUtil.normalize(null, MAX));
        assertThrows(BusinessRuleException.class, () -> MoneyUtil.normalize(BigDecimal.ZERO, MAX));
        assertThrows(BusinessRuleException.class, () -> MoneyUtil.normalize(new BigDecimal("-5.00"), MAX));
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> MoneyUtil.normalize(new BigDecimal("10.005"), MAX));
        assertEquals("INVALID_AMOUNT", ex.getCode());
    }

    @Test
    void trailingZerosBeyondTwoDecimalsAreHarmless() {
        assertEquals(new BigDecimal("10.00"), MoneyUtil.normalize(new BigDecimal("10.00000"), MAX));
    }

    @Test
    void rejectsAmountAboveMaximum() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> MoneyUtil.normalize(new BigDecimal("1000000.01"), MAX));
        assertEquals("AMOUNT_TOO_LARGE", ex.getCode());
        assertEquals(MAX, MoneyUtil.normalize(MAX, MAX));
    }
}
