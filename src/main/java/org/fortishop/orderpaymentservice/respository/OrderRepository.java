package org.fortishop.orderpaymentservice.respository;

import java.util.List;
import org.fortishop.orderpaymentservice.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByMemberId(Long memberId);
}
