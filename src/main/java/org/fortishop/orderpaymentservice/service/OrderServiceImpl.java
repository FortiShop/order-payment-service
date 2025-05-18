package org.fortishop.orderpaymentservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.fortishop.orderpaymentservice.domain.Order;
import org.fortishop.orderpaymentservice.domain.OrderItem;
import org.fortishop.orderpaymentservice.domain.OrderStatus;
import org.fortishop.orderpaymentservice.dto.event.OrderCreatedEvent;
import org.fortishop.orderpaymentservice.dto.request.OrderItemRequest;
import org.fortishop.orderpaymentservice.dto.request.OrderRequest;
import org.fortishop.orderpaymentservice.dto.response.OrderResponse;
import org.fortishop.orderpaymentservice.dto.response.OrderSummaryResponse;
import org.fortishop.orderpaymentservice.exception.OrderException;
import org.fortishop.orderpaymentservice.exception.OrderExceptionType;
import org.fortishop.orderpaymentservice.kafka.OrderEventProducer;
import org.fortishop.orderpaymentservice.respository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;

    @Override
    @Transactional
    public Long createOrder(OrderRequest request) {
        Order order = Order.builder()
                .memberId(request.memberId())
                .totalPrice(request.totalPrice())
                .status(OrderStatus.ORDERED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        for (OrderItemRequest item : request.items()) {
            OrderItem orderItem = OrderItem.builder()
                    .productId(item.productId())
                    .quantity(item.quantity())
                    .price(item.price())
                    .build();
            order.addItem(orderItem);
        }

        Order savedOrder = orderRepository.save(order);

        OrderCreatedEvent event = OrderCreatedEvent.of(savedOrder, UUID.randomUUID().toString());
        orderEventProducer.send(event);

        return savedOrder.getId();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionType.ORDER_NOT_FOUND));
        return OrderResponse.of(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderSummaryResponse::of)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> getOrdersByMember(Long memberId) {
        return orderRepository.findByMemberId(memberId).stream()
                .map(OrderSummaryResponse::of)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionType.ORDER_NOT_FOUND));
        if (order.getStatus().isFinal()) {
            throw new IllegalStateException("결제된 주문은 취소할 수 없습니다.");
        }
        order.updateStatus(OrderStatus.CANCELLED);
    }

    @Override
    @Transactional
    public void changeStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionType.ORDER_NOT_FOUND));
        order.updateStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public void resendOrderCreatedEvent(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderExceptionType.ORDER_NOT_FOUND));
        OrderCreatedEvent event = OrderCreatedEvent.of(order, UUID.randomUUID().toString());
        orderEventProducer.send(event);
    }
}
