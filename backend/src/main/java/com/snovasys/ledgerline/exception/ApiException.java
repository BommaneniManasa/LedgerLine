package com.snovasys.ledgerline.exception;

import org.springframework.http.HttpStatus;

/** Base class for errors we raise on purpose. The message is safe to show to the client. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }

    public String getCode() { return code; }
}
