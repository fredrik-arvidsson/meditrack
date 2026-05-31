import { useState } from "react";
import { Link } from "react-router-dom";
import { useFetch } from "../hooks/useFetch";
import { apiFetch } from "../api";
import type { StockItem } from "../types/medication";

// Svaret från reorder-agenten. Matchar ReorderResult på backend.
type ReorderResult = {
    draftOrder: { id: number; orderNumber: string } | null;
    source: string;
    message: string;
};

function StockPage() {
    const [refreshKey, setRefreshKey] = useState(0);
    const { data, loading, error } = useFetch<StockItem[]>(
        `/api/stock?_=${refreshKey}`
    );

    const [reorderLoading, setReorderLoading] = useState(false);
    const [reorderError, setReorderError] = useState<string | null>(null);
    const [reorderResult, setReorderResult] = useState<ReorderResult | null>(null);

    async function suggestReorder() {
        setReorderLoading(true);
        setReorderError(null);
        setReorderResult(null);
        try {
            const result = await apiFetch<ReorderResult>(
                `/api/orders/suggest-reorder`,
                { method: "POST" }
            );
            setReorderResult(result);
            // Lagret kan ha ändrats av andra skäl - hämta om för säkerhets skull.
            setRefreshKey((k) => k + 1);
        } catch (err) {
            setReorderError(err instanceof Error ? err.message : "Okänt fel");
        } finally {
            setReorderLoading(false);
        }
    }

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

            {/* AI-agent: föreslå påfyllning. Skapar ett DRAFT-utkast som
                en människa granskar och skickar — agenten beslutar aldrig. */}
            <div className="mb-4 flex items-center gap-3">
                <button
                    onClick={suggestReorder}
                    disabled={reorderLoading || lowStockCount === 0}
                    className="px-4 py-2 text-sm font-medium rounded-md bg-slate-900 text-white hover:bg-slate-800 disabled:opacity-50"
                    title={
                        lowStockCount === 0
                            ? "Inga läkemedel under tröskel — inget att föreslå"
                            : "Skapar ett påfyllningsutkast för granskning"
                    }
                >
                    {reorderLoading ? "Genererar förslag..." : "Föreslå påfyllning (AI)"}
                </button>
                {lowStockCount === 0 && (
                    <span className="text-sm text-slate-500">
                        Inget under tröskel att fylla på.
                    </span>
                )}
            </div>

            {reorderError && (
                <div className="mb-4 rounded-md bg-red-50 border border-red-200 p-4">
                    <p className="text-red-900 font-medium">Kunde inte generera förslag</p>
                    <p className="text-red-700 text-sm mt-1">{reorderError}</p>
                </div>
            )}

            {reorderResult && (
                <div className="mb-4 rounded-md bg-emerald-50 border border-emerald-200 p-4">
                    <p className="text-emerald-900 font-medium">{reorderResult.message}</p>
                    <p className="text-emerald-700 text-sm mt-1">
                        Källa: {reorderResult.source}
                    </p>
                    {reorderResult.draftOrder && (
                        <Link
                            to={`/orders/${reorderResult.draftOrder.id}`}
                            className="text-sm text-emerald-800 underline hover:text-emerald-900 mt-2 inline-block"
                        >
                            Öppna utkast {reorderResult.draftOrder.orderNumber} →
                        </Link>
                    )}
                </div>
            )}

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