package org.fortishop.orderpaymentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.fortishop.orderpaymentservice.domain.Order;
import org.fortishop.orderpaymentservice.domain.OrderStatus;
import org.fortishop.orderpaymentservice.domain.Payment;
import org.fortishop.orderpaymentservice.domain.PaymentStatus;
import org.fortishop.orderpaymentservice.dto.response.PaymentResponse;
import org.fortishop.orderpaymentservice.kafka.PaymentEventProducer;
import org.fortishop.orderpaymentservice.respository.OrderRepository;
import org.fortishop.orderpaymentservice.respository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @InjectMocks
    private PaymentServiceImpl paymentService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentEventProducer paymentEventProducer;

    @Test
    @DisplayName("결제 요청에 성공하고 Kafka 이벤트를 발행한다")
    void manualPayment_success() {
        Order order = Order.builder().id(1L).memberId(100L).totalPrice(BigDecimal.valueOf(3000))
                .status(OrderStatus.ORDERED).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any())).thenAnswer(i -> {
            Payment p = i.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 999L);
            return p;
        });

        paymentService.manualPayment(1L, "CARD");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(paymentEventProducer).sendPaymentCompleted(any());
        verify(paymentEventProducer).sendPointChanged(any());
        verify(paymentEventProducer).sendDeliveryStarted(any());
    }

    @Test
    @DisplayName("존재하지 않는 주문에 대해 결제를 시도하면 예외가 발생한다")
    void manualPayment_fail_orderNotFound() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.manualPayment(1L, "CARD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("주문 없음");
    }

    @Test
    @DisplayName("이미 결제된 주문에 대해 결제를 시도하면 예외가 발생한다")
    void manualPayment_fail_duplicate() {
        when(orderRepository.findById(1L)).thenReturn(
                Optional.of(Order.builder().id(1L).status(OrderStatus.ORDERED).build()));
        when(paymentRepository.findByOrderId(1L)).thenReturn(
                Optional.of(Payment.builder()
                        .id(1L)
                        .orderId(123L)
                        .paidAmount(BigDecimal.valueOf(1000))
                        .method("CARD")
                        .paymentStatus(PaymentStatus.SUCCESS)
                        .requestedAt(LocalDateTime.now())
                        .build())
        );

        assertThatThrownBy(() -> paymentService.manualPayment(1L, "CARD"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 결제 요청이 처리된 주문입니다.");
    }

    @Test
    @DisplayName("결제 상세 조회에 성공한다")
    void getPayment_success() {
        Payment payment = Payment.builder().id(1L).orderId(99L).paidAmount(BigDecimal.valueOf(2000)).method("CARD")
                .paymentStatus(PaymentStatus.SUCCESS).requestedAt(LocalDateTime.now()).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        PaymentResponse res = paymentService.getPayment(1L);

        assertThat(res.getOrderId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("존재하지 않는 결제를 조회하면 예외가 발생한다")
    void getPayment_notFound() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("주문 ID로 결제 상태를 조회한다")
    void getStatusByOrderId_found() {
        when(paymentRepository.findByOrderId(1L)).thenReturn(
                Optional.of(Payment.builder().paymentStatus(PaymentStatus.SUCCESS).build()));

        assertThat(paymentService.getStatusByOrderId(1L)).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("결제 정보가 없으면 상태는 NOT_REQUESTED를 반환한다")
    void getStatusByOrderId_notFound() {
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        assertThat(paymentService.getStatusByOrderId(1L)).isEqualTo("NOT_REQUESTED");
    }

    @Test
    @DisplayName("결제를 취소하면 상태가 FAILED로 변경된다")
    void cancelPayment_success() {
        Payment payment = Payment.builder().id(1L).paymentStatus(PaymentStatus.SUCCESS).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        paymentService.cancelPayment(1L);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("이미 실패한 결제를 취소하면 예외가 발생한다")
    void cancelPayment_alreadyFailed() {
        Payment payment = Payment.builder().id(1L).paymentStatus(PaymentStatus.FAILED).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelPayment(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("실패한 결제를 재시도하면 SUCCESS로 상태가 변경된다")
    void retryPayment_success() {
        Payment payment = Payment.builder().id(1L).paymentStatus(PaymentStatus.FAILED).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        paymentService.retryPayment(1L);

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("이미 성공한 결제를 재시도하면 예외가 발생한다")
    void retryPayment_alreadySuccess() {
        Payment payment = Payment.builder().id(1L).paymentStatus(PaymentStatus.SUCCESS).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.retryPayment(1L))
                .isInstanceOf(IllegalStateException.class);
    }
}
