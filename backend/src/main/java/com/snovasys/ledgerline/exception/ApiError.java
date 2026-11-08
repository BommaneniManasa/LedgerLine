package com.snovasys.ledgerline.exception;

import java.time.Instant;
import java.util.Map;

/** The only error shape the API ever returns. No stack traces, no SQL, no class names. */
public record ApiError(Instant timestamp, int status, String code, String message, String path,
                       Map<String, String> fieldErrors) {
}
