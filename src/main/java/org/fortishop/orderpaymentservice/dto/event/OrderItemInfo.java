package org.fortishop.orderpaymentservice.dto.event;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.fortishop.orderpaymentservice.domain.OrderItem;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemInfo {
    private Long productId;
    private int quantity;
    private BigDecimal price;

    public static OrderItemInfo of(OrderItem orderItem) {
        return OrderItemInfo.builder()
                .productId(orderItem.getProductId())
                .quantity(orderItem.getQuantity())
                .price(orderItem.getPrice())
                .build();
    }
}
