package org.fortishop.orderpaymentservice.dto.event;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import org.fortishop.orderpaymentservice.domain.Order;

@Builder
public record OrderCreatedEvent(
        Long orderId,
        Long memberId,
        BigDecimal totalPrice,
        List<OrderItemInfo> items,
        String createdAt,
        String traceId
) {
    public static OrderCreatedEvent of(Order order, String traceId) {
        return OrderCreatedEvent.builder()
                .orderId(order.getId())
                .memberId(order.getMemberId())
                .totalPrice(order.getTotalPrice())
                .createdAt(order.getCreatedAt().toString())
                .traceId(traceId)
                .items(order.getOrderItems().stream()
                        .map(i -> new OrderCreatedEvent.OrderItemInfo(
                                i.getProductId(), i.getQuantity(), i.getPrice()))
                        .toList())
                .build();
    }

    public record OrderItemInfo(
            Long productId,
            int quantity,
            BigDecimal price
    ) {
    }
}
