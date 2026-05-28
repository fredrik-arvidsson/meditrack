package se.meditrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vårdenheten — tenant-roten i MediTrack.
 *
 * Allt annat (användare, läkemedel, beställningar) pekar tillbaka hit
 * via care_unit_id. CareUnit har själv ingen tenant-FK — den ÄR tenant.
 */
@Entity
@Table(name = "care_units")
@Getter
@Setter
@NoArgsConstructor
public class CareUnit extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "external_id", length = 50)
    private String externalId;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}