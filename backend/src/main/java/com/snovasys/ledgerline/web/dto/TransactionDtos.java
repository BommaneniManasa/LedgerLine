package com.snovasys.ledgerline.web.dto;

import com.snovasys.ledgerline.domain.TransactionStatus;
import com.snovasys.ledgerline.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    /** Deposit or withdrawal on one of the caller's own accounts. */
    public record MoneyRequest(
            @NotNull UUID accountId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @Size(max = 200) String description) {
    }

    public record TransferRequest(
            @NotNull UUID sourceAccountId,
            @NotBlank @Pattern(regexp = "^[A-Z0-9-]{6,20}$", message = "invalid account number") String destinationAccountNumber,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @Size(max = 200) String description) {
    }

    /** direction is "IN" / "OUT" from the caller's point of view (null for auditors). */
    public record TransactionView(UUID id, String reference, TransactionType type, TransactionStatus status,
                                  BigDecimal amount, String currency, String description,
                                  String sourceAccountNumber, String destinationAccountNumber,
                                  String direction, Instant createdAt) {
    }

    /** replayed = true when the idempotency key had already been used and the original result is returned. */
    public record TransactionResult(TransactionView transaction, boolean replayed) {
    }
}
