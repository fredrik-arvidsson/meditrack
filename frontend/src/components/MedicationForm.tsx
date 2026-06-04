import { useState, type FormEvent } from "react";
import { apiFetch, ApiValidationError } from "../api";
import type {
    Medication,
    MedicationForm as MedicationFormType,
    CreateMedicationRequest,
    UpdateMedicationRequest,
} from "../types/medication";

const FORM_OPTIONS: { value: MedicationFormType; label: string }[] = [
    { value: "TABLET", label: "Tablett" },
    { value: "INJECTION", label: "Injektion" },
    { value: "SOLUTION", label: "Lösning" },
    { value: "INHALATION", label: "Inhalation" },
];

type Props = {
    medication?: Medication;
    onSaved: () => void;
    onCancel: () => void;
};

function MedicationForm({ medication, onSaved, onCancel }: Props) {
    const isEdit = !!medication;

    const [name, setName] = useState(medication?.name ?? "");
    const [atcCode, setAtcCode] = useState(medication?.atcCode ?? "");
    const [form, setForm] = useState<MedicationFormType>(medication?.form ?? "TABLET");
    const [strength, setStrength] = useState(medication?.strength ?? "");
    const [unit, setUnit] = useState(medication?.unit ?? "");
    const [controlledSubstance, setControlledSubstance] = useState(
        medication?.controlledSubstance ?? false
    );

    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState<string | null>(null);
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

    async function handleSubmit(e: FormEvent) {
        e.preventDefault();
        setFormError(null);
        setFieldErrors({});

        // Klientvalidering som speglar backend Bean Validation
        const errors: Record<string, string> = {};
        if (!name.trim()) errors.name = "Namn är obligatoriskt";
        if (!unit.trim()) errors.unit = "Enhet är obligatorisk";
        if (Object.keys(errors).length > 0) {
            setFieldErrors(errors);
            return;
        }

        const base: CreateMedicationRequest = {
            name: name.trim(),
            atcCode: atcCode.trim(),
            form,
            strength: strength.trim(),
            unit: unit.trim(),
            controlledSubstance,
        };

        setSubmitting(true);
        try {
            if (isEdit) {
                const body: UpdateMedicationRequest = { ...base, active: medication.active };
                await apiFetch<Medication>(`/api/medications/${medication.id}`, {
                    method: "PUT",
                    body: JSON.stringify(body),
                });
            } else {
                await apiFetch<Medication>("/api/medications", {
                    method: "POST",
                    body: JSON.stringify(base),
                });
            }
            onSaved();
        } catch (err) {
            if (err instanceof ApiValidationError) {
                const map: Record<string, string> = {};
                err.fieldErrors.forEach((fe) => {
                    map[fe.field] = fe.message;
                });
                setFieldErrors(map);
                setFormError(err.message);
            } else {
                setFormError(err instanceof Error ? err.message : "Okänt fel");
            }
        } finally {
            setSubmitting(false);
        }
    }

    const inputClass = (field: string) =>
        `w-full rounded-md border px-3 py-2 text-sm focus:outline-none focus:ring-2 ${
            fieldErrors[field]
                ? "border-red-300 focus:ring-red-400"
                : "border-slate-300 focus:ring-slate-400"
        }`;

    return (
        <div className="bg-white rounded-md border border-slate-200 p-4 mb-4">
            <h3 className="text-lg font-semibold text-slate-900 mb-4">
                {isEdit ? "Redigera läkemedel" : "Nytt läkemedel"}
            </h3>

            {formError && (
                <div className="rounded-md bg-red-50 border border-red-200 px-3 py-2 mb-4">
                    <p className="text-sm text-red-700">{formError}</p>
                </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">
                            Namn *
                        </label>
                        <input
                            type="text"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            className={inputClass("name")}
                            maxLength={200}
                        />
                        {fieldErrors.name && (
                            <p className="text-sm text-red-700 mt-1">{fieldErrors.name}</p>
                        )}
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">
                            ATC-kod
                        </label>
                        <input
                            type="text"
                            value={atcCode}
                            onChange={(e) => setAtcCode(e.target.value)}
                            className={inputClass("atcCode")}
                            maxLength={20}
                        />
                        {fieldErrors.atcCode && (
                            <p className="text-sm text-red-700 mt-1">{fieldErrors.atcCode}</p>
                        )}
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">
                            Form *
                        </label>
                        <select
                            value={form}
                            onChange={(e) => setForm(e.target.value as MedicationFormType)}
                            className={inputClass("form")}
                        >
                            {FORM_OPTIONS.map((opt) => (
                                <option key={opt.value} value={opt.value}>
                                    {opt.label}
                                </option>
                            ))}
                        </select>
                        {fieldErrors.form && (
                            <p className="text-sm text-red-700 mt-1">{fieldErrors.form}</p>
                        )}
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">
                            Styrka
                        </label>
                        <input
                            type="text"
                            value={strength}
                            onChange={(e) => setStrength(e.target.value)}
                            className={inputClass("strength")}
                            maxLength={50}
                            placeholder="t.ex. 500 mg"
                        />
                        {fieldErrors.strength && (
                            <p className="text-sm text-red-700 mt-1">{fieldErrors.strength}</p>
                        )}
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">
                            Enhet *
                        </label>
                        <input
                            type="text"
                            value={unit}
                            onChange={(e) => setUnit(e.target.value)}
                            className={inputClass("unit")}
                            maxLength={20}
                            placeholder="t.ex. st, ml, mg"
                        />
                        {fieldErrors.unit && (
                            <p className="text-sm text-red-700 mt-1">{fieldErrors.unit}</p>
                        )}
                    </div>

                    <div className="flex items-center pt-6">
                        <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                            <input
                                type="checkbox"
                                checked={controlledSubstance}
                                onChange={(e) => setControlledSubstance(e.target.checked)}
                                className="rounded border-slate-300"
                            />
                            Narkotikaklassat
                        </label>
                    </div>
                </div>

                <div className="flex gap-3 pt-2">
                    <button
                        type="submit"
                        disabled={submitting}
                        className="px-4 py-2 text-sm font-medium rounded-md bg-slate-900 text-white hover:bg-slate-800 disabled:opacity-50"
                    >
                        {submitting ? "Sparar..." : "Spara"}
                    </button>
                    <button
                        type="button"
                        onClick={onCancel}
                        disabled={submitting}
                        className="px-4 py-2 text-sm font-medium rounded-md bg-white text-slate-700 border border-slate-300 hover:bg-slate-50 disabled:opacity-50"
                    >
                        Avbryt
                    </button>
                </div>
            </form>
        </div>
    );
}

export default MedicationForm;
