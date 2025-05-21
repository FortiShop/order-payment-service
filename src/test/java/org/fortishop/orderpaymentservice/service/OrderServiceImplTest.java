package org.fortishop.orderpaymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.fortishop.orderpaymentservice.domain.Order;
import org.fortishop.orderpaymentservice.domain.OrderItem;
import org.fortishop.orderpaymentservice.domain.OrderStatus;
import org.fortishop.orderpaymentservice.dto.event.OrderCreatedEvent;
import org.fortishop.orderpaymentservice.dto.request.OrderItemRequest;
import org.fortishop.orderpaymentservice.dto.request.OrderRequest;
import org.fortishop.orderpaymentservice.dto.response.OrderResponse;
import org.fortishop.orderpaymentservice.exception.OrderException;
import org.fortishop.orderpaymentservice.kafka.OrderEventProducer;
import org.fortishop.orderpaymentservice.respository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @InjectMocks
    private OrderServiceImpl orderService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderEventProducer orderEventProducer;

    @Test
    @DisplayName("주문 생성에 성공하고 Kafka 이벤트를 발행한다")
    void createOrder_success() {
        OrderRequest request = new OrderRequest(1L,
                List.of(new OrderItemRequest(10L, 2, BigDecimal.valueOf(1000))),
                BigDecimal.valueOf(2000), "서울특별시");
        Order saved = Order.builder()
                .id(100L)
                .memberId(1L)
                .totalPrice(request.totalPrice())
                .address(request.address())
                .status(OrderStatus.ORDERED)
                .createdAt(LocalDateTime.now())
                .build();

        when(orderRepository.save(any())).thenReturn(saved);

        Long result = orderService.createOrder(request);

        assertThat(result).isEqualTo(100L);
        verify(orderEventProducer).send(any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("주문 상세 조회에 성공한다")
    void getOrder_success() {
        Order order = Order.builder()
                .id(1L)
                .memberId(1L)
                .address("서울특별시")
                .status(OrderStatus.ORDERED)
                .createdAt(LocalDateTime.now())
                .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderResponse res = orderService.getOrder(1L);

        assertThat(res.getOrderId()).isEqualTo(1L);
        assertThat(res.getStatus()).isEqualTo("ORDERED");
    }

    @Test
    @DisplayName("존재하지 않는 주문을 조회하면 예외가 발생한다")
    void getOrder_notFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(99L))
                .isInstanceOf(OrderException.class);
    }

    @Test
    @DisplayName("전체 주문 목록을 조회한다")
    void getAllOrders_success() {
        when(orderRepository.findAll()).thenReturn(List.of(Order.builder()
                .id(1L)
                .status(OrderStatus.ORDERED)
                .createdAt(LocalDateTime.now())
                .build()));
        assertThat(orderService.getAllOrders()).hasSize(1);
    }

    @Test
    @DisplayName("회원별 주문 목록을 조회한다")
    void getOrdersByMember_success() {
        when(orderRepository.findByMemberId(1L)).thenReturn(List.of(Order.builder()
                .id(1L)
                .status(OrderStatus.ORDERED)
                .createdAt(LocalDateTime.now())
                .build()));
        assertThat(orderService.getOrdersByMember(1L)).hasSize(1);
    }

    @Test
    @DisplayName("주문 취소에 성공한다")
    void cancelOrder_success() {
        Order order = Order.builder()
                .id(1L)
                .status(OrderStatus.ORDERED)
                .createdAt(LocalDateTime.now())
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 완료된 주문은 취소할 수 없다")
    void cancelOrder_alreadyFinalStatus() {
        for (OrderStatus status : List.of(OrderStatus.PAID, OrderStatus.FAILED, OrderStatus.CANCELLED)) {
            Order order = Order.builder()
                    .id(1L)
                    .status(status)
                    .createdAt(LocalDateTime.now())
                    .build();
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("취소할 수 없습니다");
        }
    }

    @Test
    @DisplayName("주문 상태를 변경한다")
    void changeStatus_success() {
        Order order = Order.builder()
                .id(1L)
                .status(OrderStatus.ORDERED)
                .createdAt(LocalDateTime.now())
                .build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.changeStatus(1L, OrderStatus.PAID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("존재하지 않는 주문의 상태 변경 시 예외가 발생한다")
    void changeStatus_notFound() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.changeStatus(1L, OrderStatus.CANCELLED))
                .isInstanceOf(OrderException.class);
    }

    @Test
    @DisplayName("order.created Kafka 이벤트를 재전송한다")
    void resendEvent_success() {
        Order order = Order.builder()
                .id(1L)
                .memberId(1L)
                .status(OrderStatus.ORDERED)
                .totalPrice(BigDecimal.valueOf(1000))
                .orderItems(List.of(OrderItem.builder()
                        .productId(10L)
                        .quantity(1)
                        .price(BigDecimal.valueOf(1000))
                        .build()))
                .createdAt(LocalDateTime.now())
                .build();

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        orderService.resendOrderCreatedEvent(1L);

        verify(orderEventProducer).send(any(OrderCreatedEvent.class));
    }
}
