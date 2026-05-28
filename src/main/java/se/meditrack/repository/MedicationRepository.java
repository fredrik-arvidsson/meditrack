package se.meditrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.meditrack.entity.Medication;

import java.util.List;
import java.util.Optional;

public interface MedicationRepository extends JpaRepository<Medication, Long> {

    Optional<Medication> findByIdAndCareUnitId(Long id, Long careUnitId);

    List<Medication> findAllByCareUnitIdAndActiveTrue(Long careUnitId);

    List<Medication> findAllByCareUnitId(Long careUnitId);
}
