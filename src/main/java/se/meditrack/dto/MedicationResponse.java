package se.meditrack.dto;

import se.meditrack.entity.Medication;
import se.meditrack.enums.MedicationForm;

/**
 * Utgående representation av ett läkemedel. Exponerar BARA det API:t ska
 * visa — inga interna fält (version, audit-by, tenant-relation läcker inte).
 * Statisk from(entity) är den handskrivna mappern: synlig, ingen magi.
 */
public record MedicationResponse(
        Long id,
        String name,
        String atcCode,
        MedicationForm form,
        String strength,
        String unit,
        boolean controlledSubstance,
        boolean active
) {
    public static MedicationResponse from(Medication medication) {
        return new MedicationResponse(
                medication.getId(),
                medication.getName(),
                medication.getAtcCode(),
                medication.getForm(),
                medication.getStrength(),
                medication.getUnit(),
                medication.isControlledSubstance(),
                medication.isActive());
    }
}