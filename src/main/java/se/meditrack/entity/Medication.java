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
import se.meditrack.enums.MedicationForm;

/**
 * Läkemedel i en vårdenhets katalog (per tenant — inte global katalog,
 * matchar PDL-isolering). Beskriver VAD som kan lagerföras; faktiskt
 * saldo ligger på StockItem.
 */
@Entity
@Table(name = "medications")
@Getter
@Setter
@NoArgsConstructor
public class Medication extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "care_unit_id", nullable = false)
    private CareUnit careUnit;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "atc_code", length = 20)
    private String atcCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "form", nullable = false, length = 50)
    private MedicationForm form;

    @Column(name = "strength", length = 50)
    private String strength;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "controlled_substance", nullable = false)
    private boolean controlledSubstance = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}