package se.meditrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lagersaldo för ett läkemedel på en vårdenhet. Separat från Medication
 * eftersom saldo är operativ data (ändras ofta) medan läkemedlet är
 * katalogdata. Threshold (varningsnivå) ligger här — "lågt lager" beror
 * på enhetens förbrukning.
 *
 * @Version ger optimistisk låsning: om två transaktioner läser samma
 * StockItem och båda försöker spara, får den andra en
 * OptimisticLockException istället för att tyst skriva över. Vid leverans
 * används dessutom pessimistic lock i StockService (SELECT ... FOR UPDATE).
 */
@Entity
@Table(name = "stock_items")
@Getter
@Setter
@NoArgsConstructor
public class StockItem extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "care_unit_id", nullable = false)
    private CareUnit careUnit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(name = "quantity", nullable = false)
    private int quantity = 0;

    @Column(name = "threshold", nullable = false)
    private int threshold = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}