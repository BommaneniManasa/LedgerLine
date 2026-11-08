package com.snovasys.ledgerline.exception;

import org.springframework.http.HttpStatus;

/** Also used instead of 403 when a customer asks for someone else's resource (does not reveal it exists). */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
