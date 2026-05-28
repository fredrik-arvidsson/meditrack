package se.meditrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.meditrack.entity.StockMovement;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findAllByCareUnitIdAndStockItemIdOrderByCreatedAtDesc(Long careUnitId, Long stockItemId);
}