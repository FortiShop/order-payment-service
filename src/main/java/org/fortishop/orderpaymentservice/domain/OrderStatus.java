package org.fortishop.orderpaymentservice.domain;

public enum OrderStatus {
    ORDERED, PAID, CANCELLED, FAILED;
    
    public boolean isFinal() {
        return this == PAID || this == CANCELLED || this == FAILED;
    }
}
