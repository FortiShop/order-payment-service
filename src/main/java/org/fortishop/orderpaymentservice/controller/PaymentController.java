package org.fortishop.orderpaymentservice.controller;

import lombok.RequiredArgsConstructor;
import org.fortishop.orderpaymentservice.dto.request.PaymentRequest;
import org.fortishop.orderpaymentservice.dto.response.PaymentResponse;
import org.fortishop.orderpaymentservice.global.Responder;
import org.fortishop.orderpaymentservice.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/{orderId}")
    public ResponseEntity<?> requestPayment(@PathVariable(name = "orderId") Long orderId,
                                            @RequestBody PaymentRequest request) {
        paymentService.manualPayment(orderId, request.method());
        return Responder.success("결제 요청이 완료되었습니다.");
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<?> getPayment(@PathVariable(name = "paymentId") Long paymentId) {
        PaymentResponse payment = paymentService.getPayment(paymentId);
        return Responder.success(payment);
    }

    @GetMapping("/order/{orderId}/status")
    public ResponseEntity<?> getPaymentStatus(@PathVariable(name = "orderId") Long orderId) {
        String status = paymentService.getStatusByOrderId(orderId);
        return Responder.success(status);
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable(name = "paymentId") Long paymentId) {
        paymentService.cancelPayment(paymentId);
        return Responder.success("결제가 취소되었습니다.");
    }

    @PostMapping("/{paymentId}/retry")
    public ResponseEntity<?> retry(@PathVariable(name = "paymentId") Long paymentId) {
        paymentService.retryPayment(paymentId);
        return Responder.success("결제가 재시도되었습니다.");
    }

    @GetMapping("/methods")
    public ResponseEntity<?> getMethods() {
        return Responder.success(new String[]{"CARD", "CASH"});
    }
}
