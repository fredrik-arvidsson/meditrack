package se.meditrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.meditrack.entity.CareUnit;

import java.util.Optional;

public interface CareUnitRepository extends JpaRepository<CareUnit, Long> {

    Optional<CareUnit> findByExternalId(String externalId);
}
