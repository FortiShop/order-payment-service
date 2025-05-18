package org.fortishop.orderpaymentservice.service;

import java.util.List;
import org.fortishop.orderpaymentservice.domain.OrderStatus;
import org.fortishop.orderpaymentservice.dto.request.OrderRequest;
import org.fortishop.orderpaymentservice.dto.response.OrderResponse;
import org.fortishop.orderpaymentservice.dto.response.OrderSummaryResponse;

public interface OrderService {
    Long createOrder(OrderRequest request);

    OrderResponse getOrder(Long orderId);

    List<OrderSummaryResponse> getAllOrders();

    List<OrderSummaryResponse> getOrdersByMember(Long memberId);

    void cancelOrder(Long orderId);

    void changeStatus(Long orderId, OrderStatus status);

    void resendOrderCreatedEvent(Long orderId);
}
