import { useState } from "react";
import { useFetch } from "../hooks/useFetch";
import { apiFetch } from "../api";
import type { Medication } from "../types/medication";
import MedicationForm from "../components/MedicationForm";

// Läkemedelsformer som kan filtreras på. Matchar backend-enumet MedicationForm.
// "" = inget filter (alla former).
const FORM_OPTIONS: { value: string; label: string }[] = [
    { value: "", label: "Alla former" },
    { value: "TABLET", label: "Tablett" },
    { value: "INJECTION", label: "Injektion" },
    { value: "SOLUTION", label: "Lösning" },
    { value: "CREAM", label: "Kräm" },
    { value: "INHALATION", label: "Inhalation" },
    { value: "OINTMENT", label: "Salva" },
    { value: "DROPS", label: "Droppar" },
    { value: "SUPPOSITORY", label: "Suppositorium" },
    { value: "PATCH", label: "Plåster" },
];

function MedicationsPage() {
    const [refreshKey, setRefreshKey] = useState(0);
    const [showCreateForm, setShowCreateForm] = useState(false);
    const [editingMedication, setEditingMedication] = useState<Medication | null>(null);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    // Sök- och filtertillstånd. Ändringar här bygger en ny URL, vilket får
    // useFetch att hämta om automatiskt (path ligger i dess dependency-array).
    const [query, setQuery] = useState("");
    const [formFilter, setFormFilter] = useState("");

    // Bygg query-strängen. Bara icke-tomma parametrar tas med. refreshKey
    // tvingar om-fetch efter create/edit/delete (cache-bust).
    const params = new URLSearchParams();
    if (query.trim()) params.set("q", query.trim());
    if (formFilter) params.set("form", formFilter);
    params.set("_", String(refreshKey));
    const path = `/api/medications?${params.toString()}`;

    const { data, loading, error } = useFetch<Medication[]>(path);

    function handleSaved() {
        setShowCreateForm(false);
        setEditingMedication(null);
        setRefreshKey((k) => k + 1);
    }

    function handleCancel() {
        setShowCreateForm(false);
        setEditingMedication(null);
    }

    async function handleDelete(med: Medication) {
        if (!window.confirm(`Vill du ta bort "${med.name}"? Åtgärden kan inte ångras.`)) {
            return;
        }
        setDeleteError(null);
        try {
            await apiFetch<null>(`/api/medications/${med.id}`, { method: "DELETE" });
            setRefreshKey((k) => k + 1);
        } catch (err) {
            setDeleteError(err instanceof Error ? err.message : "Okänt fel");
        }
    }

    const isFormOpen = showCreateForm || editingMedication !== null;
    const hasActiveFilter = query.trim() !== "" || formFilter !== "";

    return (
        <div>
            <div className="flex items-baseline justify-between mb-4">
                <h2 className="text-2xl font-bold text-slate-900">Läkemedel</h2>
                {!isFormOpen && (
                    <button
                        onClick={() => setShowCreateForm(true)}
                        className="px-4 py-2 text-sm font-medium rounded-md bg-slate-900 text-white hover:bg-slate-800"
                    >
                        Lägg till läkemedel
                    </button>
                )}
            </div>

            {/* Sök och filtrera. Söker i namn och ATC-kod via backend (?q=),
          filtrerar på form (?form=). Filtreringen sker serverside. */}
            <div className="flex flex-col sm:flex-row gap-3 mb-4">
                <input
                    type="text"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder="Sök på namn eller ATC-kod..."
                    className="flex-1 px-3 py-2 text-sm rounded-md border border-slate-300 focus:outline-none focus:ring-2 focus:ring-slate-400"
                    aria-label="Sök läkemedel"
                />
                <select
                    value={formFilter}
                    onChange={(e) => setFormFilter(e.target.value)}
                    className="px-3 py-2 text-sm rounded-md border border-slate-300 bg-white focus:outline-none focus:ring-2 focus:ring-slate-400"
                    aria-label="Filtrera på form"
                >
                    {FORM_OPTIONS.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                            {opt.label}
                        </option>
                    ))}
                </select>
                {hasActiveFilter && (
                    <button
                        onClick={() => {
                            setQuery("");
                            setFormFilter("");
                        }}
                        className="px-3 py-2 text-sm font-medium rounded-md bg-white text-slate-700 border border-slate-300 hover:bg-slate-50"
                    >
                        Rensa
                    </button>
                )}
            </div>

            {showCreateForm && (
                <MedicationForm onSaved={handleSaved} onCancel={handleCancel} />
            )}

            {editingMedication && (
                <MedicationForm
                    medication={editingMedication}
                    onSaved={handleSaved}
                    onCancel={handleCancel}
                />
            )}

            {deleteError && (
                <div className="rounded-md bg-red-50 border border-red-200 p-4 mb-4">
                    <p className="text-red-900 font-medium">Kunde inte ta bort läkemedel</p>
                    <p className="text-red-700 text-sm mt-1">{deleteError}</p>
                </div>
            )}

            {error && (
                <div className="rounded-md bg-red-50 border border-red-200 p-4 mb-4">
                    <p className="text-red-900 font-medium">Kunde inte hämta läkemedel</p>
                    <p className="text-red-700 text-sm mt-1">{error}</p>
                </div>
            )}

            <div className="bg-white rounded-md border border-slate-200 overflow-hidden">
                <table className="w-full text-sm">
                    <thead className="bg-slate-50 text-left">
                    <tr>
                        <th className="px-4 py-3 font-medium text-slate-700">Namn</th>
                        <th className="px-4 py-3 font-medium text-slate-700">ATC-kod</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Form</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Styrka</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Enhet</th>
                        <th className="px-4 py-3 font-medium text-slate-700"></th>
                    </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200">
                    {loading && (
                        <tr>
                            <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                                Laddar läkemedel...
                            </td>
                        </tr>
                    )}
                    {!loading && data?.length === 0 && (
                        <tr>
                            <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                                {hasActiveFilter
                                    ? "Inga läkemedel matchar din sökning."
                                    : "Inga läkemedel registrerade än."}
                            </td>
                        </tr>
                    )}
                    {!loading && data?.map((med) => (
                        <tr key={med.id} className="hover:bg-slate-50">
                            <td className="px-4 py-3 font-medium text-slate-900">
                                {med.name}
                                {med.controlledSubstance && (
                                    <span className="ml-2 inline-block px-2 py-0.5 text-xs bg-amber-100 text-amber-800 rounded">
                      Narkotika
                    </span>
                                )}
                            </td>
                            <td className="px-4 py-3 text-slate-600">{med.atcCode ?? "—"}</td>
                            <td className="px-4 py-3 text-slate-600">{med.form}</td>
                            <td className="px-4 py-3 text-slate-600">{med.strength}</td>
                            <td className="px-4 py-3 text-slate-600">{med.unit}</td>
                            <td className="px-4 py-3 text-right">
                                <div className="flex justify-end gap-2">
                                    <button
                                        onClick={() => {
                                            setShowCreateForm(false);
                                            setEditingMedication(med);
                                        }}
                                        disabled={isFormOpen}
                                        className="px-3 py-1 text-xs font-medium rounded-md bg-white text-slate-700 border border-slate-300 hover:bg-slate-50 disabled:opacity-50"
                                    >
                                        Redigera
                                    </button>
                                    <button
                                        onClick={() => handleDelete(med)}
                                        disabled={isFormOpen}
                                        className="px-3 py-1 text-xs font-medium rounded-md bg-white text-red-700 border border-red-300 hover:bg-red-50 disabled:opacity-50"
                                    >
                                        Ta bort
                                    </button>
                                </div>
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

export default MedicationsPage;