package org.fortishop.orderpaymentservice.dto.event;

import lombok.Builder;

@Builder
public record DeliveryStartedEvent(
        Long orderId,
        Long deliveryId,
        String trackingNumber,
        String company,
        String startedAt,
        String traceId
) {
}
