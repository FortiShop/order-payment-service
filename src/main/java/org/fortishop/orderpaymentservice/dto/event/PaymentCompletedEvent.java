package org.fortishop.orderpaymentservice.dto.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {
    private Long orderId;
    private Long paymentId;
    private BigDecimal paidAmount;
    private String method;
    private String timestamp;
    private String traceId;

    public static PaymentCompletedEvent of(org.fortishop.orderpaymentservice.domain.Payment payment, String traceId) {
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
