package com.snovasys.ledgerline.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String code, String message) {
        super(HttpStatus.UNAUTHORIZED, code, message);
    }

    public UnauthorizedException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }
}
