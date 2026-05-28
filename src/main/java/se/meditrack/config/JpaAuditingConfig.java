package se.meditrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Aktiverar Spring Data JPA Auditing.
 *
 * Fyller automatiskt created_by / updated_by via AuditorAware.
 * Tills riktig autentisering finns på plats returnerar auditorn
 * system-användaren (id = 1L). Byts ut mot SecurityContext-baserad
 * lookup när Spring Security konfigureras.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    /** System-användarens id — platshållare tills auth finns. */
    private static final Long SYSTEM_USER_ID = 1L;

    @Bean
    public AuditorAware<Long> auditorProvider() {
        return () -> Optional.of(SYSTEM_USER_ID);
    }
}