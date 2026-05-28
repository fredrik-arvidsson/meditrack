package se.meditrack.enums;

/**
 * Läkemedelsform. Matchar CHECK-constraint chk_medication_form i V1.
 *
 * VARCHAR i databasen (flexibilitet), enum i koden (typsäkerhet).
 */
public enum MedicationForm {
    TABLET,
    INJECTION,
    SOLUTION,
    CREAM,
    INHALATION,
    OINTMENT,
    DROPS,
    SUPPOSITORY,
    PATCH
}