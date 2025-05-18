package org.fortishop.orderpaymentservice.dto.request;

public record PaymentRequest(
        String method // "CARD" or "CASH"
) {
}
