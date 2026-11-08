package com.snovasys.ledgerline.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.snovasys.ledgerline.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsStrongEnoughPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("correct-horse-9"));
    }

    @Test
    void rejectsShortPasswordOrNull() {
        assertThrows(BadRequestException.class, () -> PasswordPolicy.validate("abc12"));
        assertThrows(BadRequestException.class, () -> PasswordPolicy.validate(null));
    }

    @Test
    void rejectsPasswordWithoutDigitOrWithoutLetter() {
        assertThrows(BadRequestException.class, () -> PasswordPolicy.validate("onlyletterspassword"));
        assertThrows(BadRequestException.class, () -> PasswordPolicy.validate("1234567890123"));
    }

    @Test
    void rejectsPasswordLongerThanBcryptLimit() {
        assertThrows(BadRequestException.class, () -> PasswordPolicy.validate("a1".repeat(40)));
    }
}
