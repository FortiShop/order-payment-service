package org.fortishop.orderpaymentservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.orderpaymentservice.domain.Order;
import org.fortishop.orderpaymentservice.domain.OrderStatus;
import org.fortishop.orderpaymentservice.domain.Payment;
import org.fortishop.orderpaymentservice.domain.PaymentStatus;
import org.fortishop.orderpaymentservice.dto.event.DeliveryStartedEvent;
import org.fortishop.orderpaymentservice.dto.event.PaymentCompletedEvent;
import org.fortishop.orderpaymentservice.dto.event.PaymentFailedEvent;
import org.fortishop.orderpaymentservice.dto.event.PointChangedEvent;
import org.fortishop.orderpaymentservice.dto.response.PaymentResponse;
import org.fortishop.orderpaymentservice.kafka.PaymentEventProducer;
import org.fortishop.orderpaymentservice.respository.OrderRepository;
import org.fortishop.orderpaymentservice.respository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer paymentEventProducer;

    @Override
    @Transactional
    public void manualPayment(Long orderId, String method) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 없음"));

        if (order.getStatus() == OrderStatus.PAID) {
            throw new IllegalStateException("이미 결제 완료된 주문입니다.");
        }

        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.FAILED) {
            throw new IllegalStateException("결제가 불가능한 주문 상태입니다: " + order.getStatus());
        }

        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            throw new IllegalStateException("이미 결제 요청이 처리된 주문입니다.");
        }

        String traceId = order.getTraceId();

        try {
            Payment payment = paymentRepository.save(
                    Payment.builder()
                            .orderId(order.getId())
                            .paidAmount(order.getTotalPrice())
                            .method(method)
                            .paymentStatus(PaymentStatus.SUCCESS)
                            .build()
            );

            order.updateStatus(OrderStatus.PAID);

            paymentEventProducer.sendPaymentCompleted(PaymentCompletedEvent.of(payment, traceId));

            paymentEventProducer.sendPointChanged(PointChangedEvent.builder()
                    .memberId(order.getMemberId())
                    .orderId(order.getId())
                    .changeType("SAVE")
                    .amount(order.getTotalPrice().multiply(new BigDecimal("0.1")))
                    .reason("주문 적립금 지급")
                    .timestamp(LocalDateTime.now().toString())
                    .traceId(traceId)
                    .build());

            paymentEventProducer.sendDeliveryStarted(DeliveryStartedEvent.builder()
                    .orderId(order.getId())
                    .deliveryId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE)
                    .trackingNumber(UUID.randomUUID().toString().substring(0, 12))
                    .company("임시")
                    .startedAt(LocalDateTime.now().toString())
                    .traceId(traceId)
                    .build());

        } catch (Exception e) {
            log.error("결제 실패", e);
            order.updateStatus(OrderStatus.FAILED);

            paymentEventProducer.sendPaymentFailed(PaymentFailedEvent.builder()
                    .orderId(order.getId())
                    .reason("결제 시스템 오류")
                    .timestamp(LocalDateTime.now().toString())
                    .traceId(traceId)
                    .build());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보가 존재하지 않습니다."));
        return PaymentResponse.of(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public String getStatusByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(p -> p.getPaymentStatus().name())
                .orElse("NOT_REQUESTED");
    }

    @Override
    @Transactional
    public void cancelPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보가 존재하지 않습니다."));

        if (payment.getPaymentStatus() == PaymentStatus.FAILED) {
            throw new IllegalStateException("이미 실패한 결제입니다.");
        }
        payment.updateStatus(PaymentStatus.FAILED);
    }

    @Override
    @Transactional
    public void retryPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제 정보가 존재하지 않습니다."));

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("이미 성공한 결제입니다.");
        }
        payment.updateStatus(PaymentStatus.SUCCESS);
    }
}
