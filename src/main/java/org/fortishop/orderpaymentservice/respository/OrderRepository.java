package org.fortishop.orderpaymentservice.respository;

import java.util.Optional;
import org.fortishop.orderpaymentservice.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByMemberId(Long memberId);
}
