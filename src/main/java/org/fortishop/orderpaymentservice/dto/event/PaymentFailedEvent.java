package org.fortishop.orderpaymentservice.dto.event;

import lombok.Builder;

@Builder
public record PaymentFailedEvent(
        Long orderId,
        String reason,
        String timestamp,
        String traceId
) {
}
