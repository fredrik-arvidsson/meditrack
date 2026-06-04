import { useState } from "react";
import { useFetch } from "../hooks/useFetch";
import { apiFetch, ApiValidationError } from "../api";
import type { Medication, Order } from "../types/medication";

type Props = {
    onSaved: () => void;
    onCancel: () => void;
};

// En rad under uppbyggnad i formuläret. medicationId som sträng eftersom
// select-värden alltid är strängar; konverteras till number vid submit.
type DraftLine = {
    medicationId: string;
    quantity: string;
};

function OrderForm({ onSaved, onCancel }: Props) {
    // Hämta läkemedel att välja bland. Återanvänder samma endpoint som
    // läkemedelslistan; bara aktiva kommer tillbaka.
    const { data: medications, loading: medsLoading } =
        useFetch<Medication[]>("/api/medications");

    const [lines, setLines] = useState<DraftLine[]>([
        { medicationId: "", quantity: "" },
    ]);
    const [notes, setNotes] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

    function updateLine(index: number, field: keyof DraftLine, value: string) {
        setLines((prev) =>
            prev.map((line, i) => (i === index ? { ...line, [field]: value } : line))
        );
    }

    function addLine() {
        setLines((prev) => [...prev, { medicationId: "", quantity: "" }]);
    }

    function removeLine(index: number) {
        setLines((prev) => prev.filter((_, i) => i !== index));
    }

    // Lokal validering innan vi skickar — speglar backend (minst en rad,
    // varje rad måste ha läkemedel + kvantitet > 0).
    function validate(): string | null {
        if (lines.length === 0) {
            return "En beställning måste ha minst en rad.";
        }
        for (const line of lines) {
            if (!line.medicationId) {
                return "Varje rad måste ha ett valt läkemedel.";
            }
            const qty = Number(line.quantity);
            if (!Number.isInteger(qty) || qty <= 0) {
                return "Varje rad måste ha en kvantitet större än noll.";
            }
        }
        // Samma läkemedel två gånger? Backend har unik constraint per
        // (order, medication) — fånga det här istället för ett 500-svar.
        const ids = lines.map((l) => l.medicationId);
        if (new Set(ids).size !== ids.length) {
            return "Samma läkemedel förekommer på flera rader — slå ihop dem till en.";
        }
        return null;
    }

    async function handleSubmit() {
        const validationError = validate();
        if (validationError) {
            setError(validationError);
            return;
        }
        setError(null);
        setFieldErrors({});
        setSubmitting(true);

        const payload = {
            lines: lines.map((l) => ({
                medicationId: Number(l.medicationId),
                quantity: Number(l.quantity),
            })),
            notes: notes.trim() || null,
        };

        try {
            await apiFetch<Order>("/api/orders", {
                method: "POST",
                body: JSON.stringify(payload),
            });
            onSaved();
        } catch (err) {
            if (err instanceof ApiValidationError) {
                setFieldErrors(err.fieldErrors);
                setError("Beställningen kunde inte sparas — kontrollera fälten.");
            } else {
                setError(err instanceof Error ? err.message : "Okänt fel");
            }
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="bg-white rounded-md border border-slate-200 p-4 mb-4">
            <h3 className="text-lg font-semibold text-slate-900 mb-4">Ny beställning</h3>

            {error && (
                <div className="rounded-md bg-red-50 border border-red-200 p-3 mb-4">
                    <p className="text-red-800 text-sm">{error}</p>
                </div>
            )}

            <div className="space-y-3 mb-4">
                {lines.map((line, index) => (
                    <div key={index} className="flex gap-2 items-start">
                        <div className="flex-1">
                            <select
                                value={line.medicationId}
                                onChange={(e) => updateLine(index, "medicationId", e.target.value)}
                                disabled={medsLoading}
                                className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 bg-white focus:outline-none focus:ring-2 focus:ring-slate-400"
                            >
                                <option value="">
                                    {medsLoading ? "Laddar läkemedel..." : "Välj läkemedel..."}
                                </option>
                                {medications?.map((med) => (
                                    <option key={med.id} value={String(med.id)}>
                                        {med.name} {med.strength ? `(${med.strength})` : ""}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="w-28">
                            <input
                                type="number"
                                min="1"
                                value={line.quantity}
                                onChange={(e) => updateLine(index, "quantity", e.target.value)}
                                placeholder="Antal"
                                className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 focus:outline-none focus:ring-2 focus:ring-slate-400"
                            />
                        </div>
                        <button
                            type="button"
                            onClick={() => removeLine(index)}
                            disabled={lines.length === 1}
                            className="px-3 py-2 text-sm font-medium rounded-md bg-white text-red-700 border border-red-300 hover:bg-red-50 disabled:opacity-40"
                            aria-label="Ta bort rad"
                        >
                            Ta bort
                        </button>
                    </div>
                ))}
            </div>

            <button
                type="button"
                onClick={addLine}
                className="mb-4 px-3 py-1.5 text-sm font-medium rounded-md bg-white text-slate-700 border border-slate-300 hover:bg-slate-50"
            >
                + Lägg till rad
            </button>

            <div className="mb-4">
                <label className="block text-sm font-medium text-slate-700 mb-1">
                    Notering (valfritt)
                </label>
                <textarea
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                    rows={2}
                    className="w-full px-3 py-2 text-sm rounded-md border border-slate-300 focus:outline-none focus:ring-2 focus:ring-slate-400"
                    placeholder="T.ex. brådskande, eller hänvisning till avdelning"
                />
            </div>

            <div className="flex gap-2">
                <button
                    type="button"
                    onClick={handleSubmit}
                    disabled={submitting}
                    className="px-4 py-2 text-sm font-medium rounded-md bg-slate-900 text-white hover:bg-slate-800 disabled:opacity-50"
                >
                    {submitting ? "Sparar..." : "Skapa beställning"}
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
        </div>
    );
}

export default OrderForm;