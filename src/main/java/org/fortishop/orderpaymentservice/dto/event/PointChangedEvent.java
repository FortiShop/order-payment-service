package org.fortishop.orderpaymentservice.dto.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointChangedEvent {
    private Long memberId;
    private Long orderId;
    private String changeType;
    private BigDecimal amount;
    private String reason;
    private String timestamp;
    private String traceId;
}
