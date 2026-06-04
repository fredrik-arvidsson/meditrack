package se.meditrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.meditrack.entity.Medication;
import se.meditrack.enums.MedicationForm;

import java.util.List;
import java.util.Optional;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    Optional<Medication> findByIdAndCareUnitId(Long id, Long careUnitId);

    List<Medication> findAllByCareUnitIdAndActiveTrue(Long careUnitId);

    List<Medication> findAllByCareUnitId(Long careUnitId);

    /**
     * Sök och filtrera, tenant-scopad och bara aktiva läkemedel.
     * Alla filter är valfria (NULL = ignorera):
     *   - q matchar namn ELLER ATC-kod (case-insensitive delsträng)
     *   - form matchar exakt om angiven
     * Filtreringen sker i databasen, inte i minnet, så det skalar med antalet poster.
     */
    @Query("""
            SELECT m FROM Medication m
            WHERE m.careUnit.id = :careUnitId
              AND m.active = true
              AND (:q IS NULL OR LOWER(m.name) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(m.atcCode) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:form IS NULL OR m.form = :form)
            ORDER BY m.name ASC
            """)
    List<Medication> search(@Param("careUnitId") Long careUnitId,
                            @Param("q") String q,
                            @Param("form") MedicationForm form);
}