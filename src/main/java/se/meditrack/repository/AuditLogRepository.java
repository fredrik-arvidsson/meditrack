package se.meditrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.meditrack.entity.AuditLog;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByCareUnitIdOrderByCreatedAtDesc(Long careUnitId);
}
