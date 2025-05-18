package org.fortishop.orderpaymentservice.respository;

import java.util.Optional;
import org.fortishop.orderpaymentservice.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);
}
