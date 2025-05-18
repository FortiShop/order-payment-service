package org.fortishop.orderpaymentservice.dto.event;

public record InventoryReservedEvent(
        Long orderId,
        boolean reserved,
        String timestamp,
        String traceId
) {
}
