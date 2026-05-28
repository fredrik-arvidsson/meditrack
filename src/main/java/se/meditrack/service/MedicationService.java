package se.meditrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.meditrack.dto.CreateMedicationRequest;
import se.meditrack.dto.MedicationResponse;
import se.meditrack.dto.UpdateMedicationRequest;
import se.meditrack.entity.CareUnit;
import se.meditrack.entity.Medication;
import se.meditrack.exception.NotFoundException;
import se.meditrack.repository.CareUnitRepository;
import se.meditrack.repository.MedicationRepository;
import se.meditrack.security.CurrentUserProvider;

import java.util.List;

/**
 * Affärslogik för läkemedelsregistret. All åtkomst är tenant-scopad:
 * careUnitId hämtas från inloggad användare, aldrig från klienten.
 *
 * @Transactional på klassnivå (skrivmetoder) + readOnly på läsmetoder.
 * DTO-mappning sker INUTI metoderna så lazy-relationer kan laddas medan
 * Hibernate-sessionen är öppen (open-in-view: false). Entiteter lämnar
 * aldrig servicen.
 */
@Service
@Transactional
public class MedicationService {

    private final MedicationRepository medicationRepository;
    private final CareUnitRepository careUnitRepository;
    private final CurrentUserProvider currentUser;

    public MedicationService(MedicationRepository medicationRepository,
                             CareUnitRepository careUnitRepository,
                             CurrentUserProvider currentUser) {
        this.medicationRepository = medicationRepository;
        this.careUnitRepository = careUnitRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<MedicationResponse> findAll() {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        return medicationRepository.findAllByCareUnitId(careUnitId).stream()
                .map(MedicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MedicationResponse findById(Long id) {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        Medication medication = medicationRepository.findByIdAndCareUnitId(id, careUnitId)
                .orElseThrow(() -> new NotFoundException("Läkemedel hittades inte: " + id));
        return MedicationResponse.from(medication);
    }

    public MedicationResponse create(CreateMedicationRequest request) {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        CareUnit careUnit = careUnitRepository.findById(careUnitId)
                .orElseThrow(() -> new NotFoundException("Vårdenhet hittades inte: " + careUnitId));

        Medication medication = new Medication();
        medication.setCareUnit(careUnit);
        medication.setName(request.name());
        medication.setAtcCode(request.atcCode());
        medication.setForm(request.form());
        medication.setStrength(request.strength());
        medication.setUnit(request.unit());
        medication.setControlledSubstance(request.controlledSubstance());
        medication.setActive(true);

        Medication saved = medicationRepository.save(medication);
        return MedicationResponse.from(saved);
    }

    public MedicationResponse update(Long id, UpdateMedicationRequest request) {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        Medication medication = medicationRepository.findByIdAndCareUnitId(id, careUnitId)
                .orElseThrow(() -> new NotFoundException("Läkemedel hittades inte: " + id));

        medication.setName(request.name());
        medication.setAtcCode(request.atcCode());
        medication.setForm(request.form());
        medication.setStrength(request.strength());
        medication.setUnit(request.unit());
        medication.setControlledSubstance(request.controlledSubstance());
        medication.setActive(request.active());

        // Ingen explicit save() behövs — entiteten är managed inom transaktionen,
        // Hibernate flushar ändringarna vid commit (dirty checking).
        return MedicationResponse.from(medication);
    }

    public void delete(Long id) {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        Medication medication = medicationRepository.findByIdAndCareUnitId(id, careUnitId)
                .orElseThrow(() -> new NotFoundException("Läkemedel hittades inte: " + id));

        // Soft-delete via active-flaggan istället för hård radering —
        // ett läkemedel kan ha historik (orderrader, lagerrörelser) som
        // måste bevaras. Hård delete skulle bryta referensintegritet och
        // spårbarhet.
        medication.setActive(false);
    }
}