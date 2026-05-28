package se.meditrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se.meditrack.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndCareUnitId(Long id, Long careUnitId);

    List<User> findAllByCareUnitIdAndDeletedAtIsNull(Long careUnitId);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);
}