package org.fortishop.orderpaymentservice.dto.event;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record PointChangedEvent(
        Long memberId,
        Long orderId,
        String changeType, // SAVE
        BigDecimal amount,
        String reason,
        String timestamp,
        String traceId
) {
}
