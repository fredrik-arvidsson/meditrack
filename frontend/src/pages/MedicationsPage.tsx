import { useState } from "react";
import { useFetch } from "../hooks/useFetch";
import { apiFetch } from "../api";
import type { Medication } from "../types/medication";
import MedicationForm from "../components/MedicationForm";

function MedicationsPage() {
    const [refreshKey, setRefreshKey] = useState(0);
    const [showCreateForm, setShowCreateForm] = useState(false);
    const [editingMedication, setEditingMedication] = useState<Medication | null>(null);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const { data, loading, error } = useFetch<Medication[]>(
        `/api/medications?_=${refreshKey}`
    );

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

    if (loading) {
        return <p className="text-slate-600">Laddar läkemedel...</p>;
    }

    if (error) {
        return (
            <div className="rounded-md bg-red-50 border border-red-200 p-4">
                <p className="text-red-900 font-medium">Kunde inte hämta läkemedel</p>
                <p className="text-red-700 text-sm mt-1">{error}</p>
            </div>
        );
    }

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
                    {data?.map((med) => (
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
