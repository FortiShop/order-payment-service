package org.fortishop.orderpaymentservice.global.exception;

import org.springframework.http.HttpStatus;

public interface BaseExceptionType {
    String getErrorCode();

    String getErrorMessage();

    HttpStatus getHttpStatus();
}
