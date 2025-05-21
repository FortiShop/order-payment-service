package org.fortishop.orderpaymentservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.fortishop.orderpaymentservice.domain.Order;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@AllArgsConstructor
public class OrderResponse {

    private Long orderId;
    private Long memberId;
    private String status;
    private BigDecimal totalPrice;
    private String address;
    private LocalDateTime createdAt;
    private List<OrderItemResponse> items;

    public static OrderResponse of(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getMemberId(),
                order.getStatus().name(),
                order.getTotalPrice(),
                order.getAddress(),
                order.getCreatedAt(),
                order.getOrderItems().stream()
                        .map(OrderItemResponse::of)
                        .toList()
        );
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
    @AllArgsConstructor
    public static class OrderItemResponse {
        private Long productId;
        private int quantity;
        private BigDecimal price;

        public static OrderItemResponse of(org.fortishop.orderpaymentservice.domain.OrderItem item) {
            return new OrderItemResponse(
                    item.getProductId(),
                    item.getQuantity(),
                    item.getPrice()
            );
        }
    }
}
