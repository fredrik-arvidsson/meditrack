import { useFetch } from "../hooks/useFetch";
import type { StockItem } from "../types/medication";

function StockPage() {
    const { data, loading, error } = useFetch<StockItem[]>("/api/stock");

    if (loading) {
        return <p className="text-slate-600">Laddar lager...</p>;
    }

    if (error) {
        return (
            <div className="rounded-md bg-red-50 border border-red-200 p-4">
                <p className="text-red-900 font-medium">Kunde inte hämta lager</p>
                <p className="text-red-700 text-sm mt-1">{error}</p>
            </div>
        );
    }

    const lowStockCount = data?.filter((item) => item.belowThreshold).length ?? 0;

    return (
        <div>
            <div className="flex items-baseline justify-between mb-4">
                <h2 className="text-2xl font-bold text-slate-900">Lager</h2>
                {lowStockCount > 0 && (
                    <p className="text-sm text-amber-700">
                        ⚠ {lowStockCount} läkemedel under tröskelnivå
                    </p>
                )}
            </div>

            <div className="bg-white rounded-md border border-slate-200 overflow-hidden">
                <table className="w-full text-sm">
                    <thead className="bg-slate-50 text-left">
                    <tr>
                        <th className="px-4 py-3 font-medium text-slate-700">Läkemedel</th>
                        <th className="px-4 py-3 font-medium text-slate-700 text-right">Saldo</th>
                        <th className="px-4 py-3 font-medium text-slate-700 text-right">Tröskel</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Status</th>
                    </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200">
                    {data?.map((item) => (
                        <tr
                            key={item.id}
                            className={item.belowThreshold ? "bg-amber-50" : "hover:bg-slate-50"}
                        >
                            <td className="px-4 py-3 font-medium text-slate-900">
                                {item.medicationName}
                            </td>
                            <td className="px-4 py-3 text-slate-900 text-right tabular-nums">
                                {item.quantity}
                            </td>
                            <td className="px-4 py-3 text-slate-600 text-right tabular-nums">
                                {item.threshold}
                            </td>
                            <td className="px-4 py-3">
                                {item.belowThreshold ? (
                                    <span className="inline-block px-2 py-0.5 text-xs bg-amber-100 text-amber-800 rounded font-medium">
                                        Under tröskel
                                    </span>
                                ) : (
                                    <span className="inline-block px-2 py-0.5 text-xs bg-emerald-100 text-emerald-800 rounded font-medium">
                                        OK
                                    </span>
                                )}
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

export default StockPage;