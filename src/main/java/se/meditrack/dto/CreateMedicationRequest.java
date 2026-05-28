package se.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import se.meditrack.enums.MedicationForm;

/**
 * Inkommande data för att skapa ett läkemedel. Validering sitter HÄR,
 * på vägen in — inte på entiteten. careUnitId kommer inte från klienten
 * utan sätts av service-lagret från inloggad användares kontext (tenant).
 */
public record CreateMedicationRequest(

        @NotBlank(message = "Namn är obligatoriskt")
        @Size(max = 200)
        String name,

        @Size(max = 20)
        String atcCode,

        @NotNull(message = "Form är obligatorisk")
        MedicationForm form,

        @Size(max = 50)
        String strength,

        @NotBlank(message = "Enhet är obligatorisk")
        @Size(max = 20)
        String unit,

        boolean controlledSubstance
) {}