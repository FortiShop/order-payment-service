package org.fortishop.orderpaymentservice.service;

import org.fortishop.orderpaymentservice.dto.response.PaymentResponse;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentService {

    void manualPayment(Long orderId, String method);

    @Transactional(readOnly = true)
    PaymentResponse getPayment(Long paymentId);

    @Transactional(readOnly = true)
    String getStatusByOrderId(Long orderId);

    @Transactional
    void cancelPayment(Long paymentId);

    @Transactional
    void retryPayment(Long paymentId);
}
