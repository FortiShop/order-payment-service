package org.fortishop.orderpaymentservice.dto.request;

import java.math.BigDecimal;

public record OrderItemRequest(Long productId, int quantity, BigDecimal price) {
}
