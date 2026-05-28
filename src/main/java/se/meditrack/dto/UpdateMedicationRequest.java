package se.meditrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import se.meditrack.enums.MedicationForm;

/**
 * Inkommande data för att uppdatera ett läkemedel. Samma fält som create
 * här, men separat record — create och update kan divergera senare
 * (t.ex. får man inte byta form på ett läkemedel med historik), och då
 * vill vi inte ha tvingats dela en gemensam typ.
 */
public record UpdateMedicationRequest(

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

        boolean controlledSubstance,

        boolean active) {

}
