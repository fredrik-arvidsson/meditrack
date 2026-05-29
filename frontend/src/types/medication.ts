export type MedicationForm = "TABLET" | "INJECTION" | "SOLUTION" | "INHALATION";

export type Medication = {
    id: number;
    name: string;
    atcCode: string | null;
    form: MedicationForm;
    strength: string;
    unit: string;
    controlledSubstance: boolean;
    active: boolean;
};