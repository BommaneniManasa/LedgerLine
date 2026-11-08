package com.snovasys.ledgerline.exception;

import org.springframework.http.HttpStatus;

/** The request is well-formed but breaks a business rule (insufficient funds, wrong currency, ...). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
