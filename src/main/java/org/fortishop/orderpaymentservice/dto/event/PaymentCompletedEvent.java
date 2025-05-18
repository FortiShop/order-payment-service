package org.fortishop.orderpaymentservice.dto.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import org.fortishop.orderpaymentservice.domain.Payment;

@Builder
public record PaymentCompletedEvent(
        Long orderId,
        Long paymentId,
        BigDecimal paidAmount,
        String method,
        String timestamp,
        String traceId
) {
    public static PaymentCompletedEvent of(Payment payment, String traceId) {
        return PaymentCompletedEvent.builder()
                .orderId(payment.getOrderId())
                .paymentId(payment.getId())
                .paidAmount(payment.getPaidAmount())
                .method(payment.getMethod())
                .timestamp(LocalDateTime.now().toString())
                .traceId(traceId)
                .build();
    }
}
