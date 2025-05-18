package org.fortishop.orderpaymentservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.fortishop.orderpaymentservice.domain.Payment;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED, force = true)
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private Long orderId;
    private String status;
    private BigDecimal paidAmount;
    private String method;
    private LocalDateTime requestedAt;

    public static PaymentResponse of(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getPaymentStatus().name(),
                payment.getPaidAmount(),
                payment.getMethod(),
                payment.getRequestedAt()
        );
    }
}
