export type MedicationForm = "TABLET" | "INJECTION" | "SOLUTION" | "INHALATION";

export type CreateMedicationRequest = {
    name: string;
    atcCode: string;
    form: MedicationForm;
    strength: string;
    unit: string;
    controlledSubstance: boolean;
};

export type UpdateMedicationRequest = CreateMedicationRequest & {
    active: boolean;
};

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

export type StockItem = {
    id: number;
    medicationId: number;
    medicationName: string;
    quantity: number;
    threshold: number;
    belowThreshold: boolean;
};

export type OrderStatus = "DRAFT" | "SENT" | "CONFIRMED" | "DELIVERED" | "CANCELLED";

export type OrderLine = {
    id: number;
    medicationId: number;
    medicationName: string;
    quantity: number;
    notes: string | null;
};

export type Order = {
    id: number;
    orderNumber: string;
    status: OrderStatus;
    lines: OrderLine[];
    sentAt: string | null;
    confirmedAt: string | null;
    deliveredAt: string | null;
    createdAt: string;
    notes: string | null;
};