package org.fortishop.orderpaymentservice.exception;

import org.fortishop.orderpaymentservice.global.exception.BaseExceptionType;
import org.springframework.http.HttpStatus;

public enum OrderExceptionType implements BaseExceptionType {
    ORDER_NOT_FOUND("O001", "일치하는 주문이 존재하지 않습니다.", HttpStatus.NOT_FOUND);

    private final String errorCode;
    private final String errorMessage;
    private final HttpStatus httpStatus;

    OrderExceptionType(String errorCode, String errorMessage, HttpStatus httpStatus) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getErrorCode() {
        return this.errorCode;
    }

    @Override
    public String getErrorMessage() {
        return this.errorMessage;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }
}
