package se.meditrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import se.meditrack.enums.Role;

import java.time.LocalDateTime;

/**
 * Vårdpersonal. Hör till en vårdenhet (tenant) och har en roll
 * som styr behörighet (RBAC, separation of duties — HSLF-FS 2017:37).
 *
 * Soft-delete + anonymisering enligt GDPR art. 17 (rätten att bli glömd):
 * användare raderas inte hårt — deleted_at sätts, och PII (namn, e-post)
 * nollställs vid anonymisering (anonymized_at).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "care_unit_id", nullable = false)
    private CareUnit careUnit;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "name", length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;
}