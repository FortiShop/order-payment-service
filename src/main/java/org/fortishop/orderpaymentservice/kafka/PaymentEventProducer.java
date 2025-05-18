package org.fortishop.orderpaymentservice.kafka;

import lombok.RequiredArgsConstructor;
import org.fortishop.orderpaymentservice.dto.event.DeliveryStartedEvent;
import org.fortishop.orderpaymentservice.dto.event.PaymentCompletedEvent;
import org.fortishop.orderpaymentservice.dto.event.PaymentFailedEvent;
import org.fortishop.orderpaymentservice.dto.event.PointChangedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendPaymentCompleted(PaymentCompletedEvent event) {
        kafkaTemplate.send("payment.completed", event.orderId().toString(), event);
    }

    public void sendPaymentFailed(PaymentFailedEvent event) {
        kafkaTemplate.send("payment.failed", event.orderId().toString(), event);
    }

    public void sendPointChanged(PointChangedEvent event) {
        kafkaTemplate.send("point.changed", event.memberId().toString(), event);
    }

    public void sendDeliveryStarted(DeliveryStartedEvent event) {
        kafkaTemplate.send("delivery.started", event.orderId().toString(), event);
    }

}

