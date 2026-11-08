package com.snovasys.ledgerline.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.snovasys.ledgerline.repository.AccountRepository;
import org.junit.jupiter.api.Test;

class AccountNumberGeneratorTest {

    @Test
    void formatsTwelveDigitsWithLeadingZeros() {
        assertEquals("LL000000000042", AccountNumberGenerator.format(42));
        assertEquals(14, AccountNumberGenerator.format(999_999_999_999L).length());
    }

    @Test
    void generatedNumbersMatchTheExpectedPattern() {
        AccountRepository repository = mock(AccountRepository.class);
        when(repository.existsByAccountNumber(anyString())).thenReturn(false);
        String number = new AccountNumberGenerator(repository).next();
        assertTrue(number.matches("LL\\d{12}"), number);
    }

    @Test
    void retriesWhenANumberIsAlreadyTaken() {
        AccountRepository repository = mock(AccountRepository.class);
        when(repository.existsByAccountNumber(anyString())).thenReturn(true, false);
        String number = new AccountNumberGenerator(repository).next();
        assertTrue(number.matches("LL\\d{12}"), number);
    }
}
