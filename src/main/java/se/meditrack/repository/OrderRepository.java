package se.meditrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.meditrack.entity.Order;
import se.meditrack.enums.OrderStatus;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByIdAndCareUnitId(Long id, Long careUnitId);

    List<Order> findAllByCareUnitIdOrderByCreatedAtDesc(Long careUnitId);

    List<Order> findAllByCareUnitIdAndStatus(Long careUnitId, OrderStatus status);
}