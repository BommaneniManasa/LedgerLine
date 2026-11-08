package com.snovasys.ledgerline.service;

import com.snovasys.ledgerline.exception.BadRequestException;
import java.nio.charset.StandardCharsets;

/** Password rules. bcrypt only reads the first 72 bytes, so longer passwords are refused instead of silently cut. */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.length() < 10) {
            throw new BadRequestException("WEAK_PASSWORD", "Password must be at least 10 characters long");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("WEAK_PASSWORD", "Password must be at most 72 bytes long");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BadRequestException("WEAK_PASSWORD", "Password must contain at least one letter and one digit");
        }
    }
}
