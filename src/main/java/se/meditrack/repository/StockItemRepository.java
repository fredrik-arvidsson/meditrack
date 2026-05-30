package se.meditrack.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.meditrack.entity.StockItem;

import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findByIdAndCareUnitId(Long id, Long careUnitId);

    @Query("SELECT s FROM StockItem s WHERE s.careUnit.id = :careUnitId ORDER BY s.medication.name ASC")
    List<StockItem> findAllByCareUnitId(@Param("careUnitId") Long careUnitId);

    /**
     * Hämtar StockItem MED pessimistic write-lås (SELECT ... FOR UPDATE).
     * Används vid justering i StockService för att serialisera saldouppdatering
     * och förhindra lost update vid samtidiga skrivningar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.id = :id AND s.careUnit.id = :careUnitId")
    Optional<StockItem> findByIdAndCareUnitIdForUpdate(@Param("id") Long id,
                                                       @Param("careUnitId") Long careUnitId);

    /**
     * Hämtar StockItem för ett läkemedel inom en vårdenhet, med pessimistic
     * write-lås. Används av OrderService vid leverans — där har vi
     * medication-id (från orderraden), inte stockItem-id.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockItem s WHERE s.medication.id = :medicationId AND s.careUnit.id = :careUnitId")
    Optional<StockItem> findByMedicationAndCareUnitForUpdate(@Param("medicationId") Long medicationId,
                                                             @Param("careUnitId") Long careUnitId);

    /**
     * StockItems under sin threshold — för varningar om lågt lager.
     */
    @Query("SELECT s FROM StockItem s WHERE s.careUnit.id = :careUnitId AND s.quantity < s.threshold")
    List<StockItem> findBelowThreshold(@Param("careUnitId") Long careUnitId);
}