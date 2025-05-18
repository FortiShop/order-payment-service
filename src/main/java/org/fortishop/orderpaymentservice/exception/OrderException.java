package org.fortishop.orderpaymentservice.exception;

import org.fortishop.orderpaymentservice.global.exception.BaseException;
import org.fortishop.orderpaymentservice.global.exception.BaseExceptionType;

public class OrderException extends BaseException {
    private final BaseExceptionType exceptionType;

    public OrderException(BaseExceptionType exceptionType) {
        this.exceptionType = exceptionType;
    }

    @Override
    public BaseExceptionType getExceptionType() {
        return exceptionType;
    }
}
