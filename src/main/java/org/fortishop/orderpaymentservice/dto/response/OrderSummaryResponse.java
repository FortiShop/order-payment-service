package org.fortishop.orderpaymentservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.fortishop.orderpaymentservice.domain.Order;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@AllArgsConstructor
public class OrderSummaryResponse {

    private Long orderId;
    private Long memberId;
    private String status;
    private BigDecimal totalPrice;
    private LocalDateTime createdAt;

    public static OrderSummaryResponse of(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getMemberId(),
                order.getStatus().name(),
                order.getTotalPrice(),
                order.getCreatedAt()
        );
    }
}
