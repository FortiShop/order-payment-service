package org.fortishop.orderpaymentservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStartedEvent {
    private Long orderId;
    private Long deliveryId;
    private String trackingNumber;
    private String company;
    private String startedAt;
    private String traceId;
}
