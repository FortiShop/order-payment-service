package org.fortishop.orderpaymentservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fortishop.orderpaymentservice.domain.OrderStatus;
import org.fortishop.orderpaymentservice.dto.event.InventoryFailedEvent;
import org.fortishop.orderpaymentservice.dto.event.InventoryReservedEvent;
import org.fortishop.orderpaymentservice.respository.OrderRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final OrderRepository orderRepository;

    @KafkaListener(topics = "inventory.reserved", groupId = "order-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleReserved(InventoryReservedEvent event) {
        log.info("재고 확보 성공: orderId = {}, traceId={} 결제는 프론트에서 수동 요청 예정", event.getOrderId(), event.getTraceId());
        // 결제 창으로 넘어가도록 하면 좋음. 실제 프론트에 재고 확보를 성공했다는 내용을 전달해야함.
    }

    @KafkaListener(
            topics = "inventory.failed",
            groupId = "order-group",
            containerFactory = "inventoryFailedKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleFailed(InventoryFailedEvent event, Acknowledgment ack) {
        log.warn("재고 확보 실패: orderId = {}, traceId={}, reason = {}", event.getOrderId(), event.getTraceId(),
                event.getReason());
        try {
            orderRepository.findById(event.getOrderId()).ifPresent(order -> {
                order.updateStatus(OrderStatus.FAILED);
                log.info("변경 후 상태: {}", order.getStatus());
            });
            ack.acknowledge();
        } catch (Exception e) {
            log.error("처리 중 예외 발생: {}", e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(topics = "inventory.failed.dlq", groupId = "order-dlq-group")
    public void handleDlq(InventoryFailedEvent event) {
        log.error("[DLQ 메시지 확인] inventory.failed 처리 실패 : {}", event);
        // slack 또는 이메일로 개발자, 관리자에게 알림
    }
}
