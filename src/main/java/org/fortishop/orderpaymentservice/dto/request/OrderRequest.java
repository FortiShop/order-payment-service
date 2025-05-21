package org.fortishop.orderpaymentservice.dto.request;

import java.math.BigDecimal;
import java.util.List;

public record OrderRequest(Long memberId, List<OrderItemRequest> items, BigDecimal totalPrice, String address) {
}
