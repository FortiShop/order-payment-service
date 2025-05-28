package org.fortishop.orderpaymentservice.service;

import org.fortishop.orderpaymentservice.dto.response.PaymentResponse;

public interface PaymentService {
    void manualPayment(Long orderId, String method);

    PaymentResponse getPayment(Long paymentId);

    String getStatusByOrderId(Long orderId);

    void cancelPayment(Long paymentId);

    void retryPayment(Long paymentId);
}
