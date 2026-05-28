package se.meditrack.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Ändra varningsnivån (threshold) för en lagerpost. Egen liten record —
 * threshold ändras oberoende av allt annat, och saldot ändras ALDRIG
 * direkt här (det går via lagerrörelser för spårbarhet).
 */
public record UpdateThresholdRequest(

        @NotNull
        @PositiveOrZero(message = "Threshold kan inte vara negativt")
        Integer threshold) {

}