package org.fortishop.orderpaymentservice.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.fortishop.orderpaymentservice.domain.OrderStatus;
import org.fortishop.orderpaymentservice.dto.request.OrderRequest;
import org.fortishop.orderpaymentservice.dto.response.OrderResponse;
import org.fortishop.orderpaymentservice.dto.response.OrderSummaryResponse;
import org.fortishop.orderpaymentservice.global.Responder;
import org.fortishop.orderpaymentservice.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        Long orderId = orderService.createOrder(request);
        return Responder.success(orderId);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<?> getOrder(@PathVariable(name = "orderId") Long orderId) {
        OrderResponse order = orderService.getOrder(orderId);
        return Responder.success(order);
    }

    @GetMapping
    public ResponseEntity<?> getOrders(@RequestParam(name = "memberId", required = false) Long memberId) {
        List<OrderSummaryResponse> orders = (memberId != null)
                ? orderService.getOrdersByMember(memberId)
                : orderService.getAllOrders();
        return Responder.success(orders);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable(name = "orderId") Long orderId) {
        orderService.cancelOrder(orderId);
        return Responder.success("주문이 취소되었습니다.");
    }

    @PutMapping("/{orderId}/status")
    public ResponseEntity<?> changeStatus(@PathVariable(name = "orderId") Long orderId,
                                          @RequestParam(name = "status") OrderStatus status) {
        orderService.changeStatus(orderId, status);
        return Responder.success("상태가 변경되었습니다.");
    }

    @PostMapping("/{orderId}/resend-event")
    public ResponseEntity<?> resendEvent(@PathVariable(name = "orderId") Long orderId) {
        orderService.resendOrderCreatedEvent(orderId);
        return Responder.success("Kafka 이벤트 재전송 완료");
    }
}
