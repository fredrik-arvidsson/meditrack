package se.meditrack.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.meditrack.dto.CreateMedicationRequest;
import se.meditrack.dto.MedicationResponse;
import se.meditrack.dto.UpdateMedicationRequest;
import se.meditrack.service.MedicationService;

import java.util.List;

/**
 * REST-endpoints för läkemedelsregistret. Tunn layer — all logik bor i
 * MedicationService. Controllerns ansvar: HTTP in, validering, DTO ut.
 *
 * Alla endpoints är implicit tenant-scoped: servicen hämtar careUnitId
 * från CurrentUserProvider, klienten behöver inte (och får inte) skicka det.
 *
 * Auth saknas än så länge — Spring Securitys default-form-login är på,
 * men ingen rollkontroll. Läggs till i security-lagret senare.
 */
@RestController
@RequestMapping("/api/medications")
public class MedicationController {

    private final MedicationService medicationService;

    public MedicationController(MedicationService medicationService) {
        this.medicationService = medicationService;
    }

    @GetMapping
    public List<MedicationResponse> findAll() {
        return medicationService.findAll();
    }

    @GetMapping("/{id}")
    public MedicationResponse findById(@PathVariable Long id) {
        return medicationService.findById(id);
    }

    @PostMapping
    public ResponseEntity<MedicationResponse> create(@Valid @RequestBody CreateMedicationRequest request) {
        MedicationResponse created = medicationService.create(request);
        // 201 Created för ny resurs — RESTful konvention.
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public MedicationResponse update(@PathVariable Long id,
                                     @Valid @RequestBody UpdateMedicationRequest request) {
        return medicationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        // Returnerar 204 No Content. Soft-delete sker i servicen
        // (sätter active=false) — klienten ser samma resultat som vid
        // hård radering, men historiken bevaras.
        medicationService.delete(id);
    }
}