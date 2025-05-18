package org.fortishop.orderpaymentservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.orderpaymentservice.domain.OrderStatus;
import org.fortishop.orderpaymentservice.dto.event.InventoryFailedEvent;
import org.fortishop.orderpaymentservice.dto.event.InventoryReservedEvent;
import org.fortishop.orderpaymentservice.respository.OrderRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = "inventory.reserved", groupId = "order-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleReserved(InventoryReservedEvent event) {
        log.info("재고 확보 성공: orderId = {}, 결제는 프론트에서 수동 요청 예정", event.orderId());
    }

    @KafkaListener(topics = "inventory.failed", groupId = "order-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleFailed(InventoryFailedEvent event) {
        log.warn("재고 확보 실패: orderId = {}, reason = {}", event.orderId(), event.reason());

        orderRepository.findById(event.orderId()).ifPresent(order -> {
            order.updateStatus(OrderStatus.FAILED);
        });
    }
}

