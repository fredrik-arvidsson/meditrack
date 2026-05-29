import { useFetch } from "../hooks/useFetch";
import type { Medication } from "../types/medication";

function MedicationsPage() {
    const { data, loading, error } = useFetch<Medication[]>("/api/medications");

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
            <h2 className="text-2xl font-bold text-slate-900 mb-4">Läkemedel</h2>
            <div className="bg-white rounded-md border border-slate-200 overflow-hidden">
                <table className="w-full text-sm">
                    <thead className="bg-slate-50 text-left">
                    <tr>
                        <th className="px-4 py-3 font-medium text-slate-700">Namn</th>
                        <th className="px-4 py-3 font-medium text-slate-700">ATC-kod</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Form</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Styrka</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Enhet</th>
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
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

export default MedicationsPage;