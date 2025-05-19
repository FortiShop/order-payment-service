package org.fortishop.orderpaymentservice.dto.event;

import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private Long orderId;
    private Long memberId;
    private BigDecimal totalPrice;
    private List<OrderItemInfo> items;
    private String createdAt;
    private String traceId;

    public static OrderCreatedEvent of(org.fortishop.orderpaymentservice.domain.Order order, String traceId) {
        return OrderCreatedEvent.builder()
                .orderId(order.getId())
                .memberId(order.getMemberId())
                .totalPrice(order.getTotalPrice())
                .createdAt(order.getCreatedAt().toString())
                .traceId(traceId)
                .items(order.getOrderItems().stream()
                        .map(i -> new OrderItemInfo(
                                i.getProductId(),
                                i.getQuantity(),
                                i.getPrice()))
                        .toList())
                .build();
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemInfo {
        private Long productId;
        private int quantity;
        private BigDecimal price;
    }
}
