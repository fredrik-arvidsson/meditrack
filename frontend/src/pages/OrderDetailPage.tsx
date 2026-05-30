import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { useFetch } from "../hooks/useFetch";
import StatusBadge from "../components/StatusBadge";
import type { Order, OrderStatus } from "../types/medication";

const API_BASE = "http://localhost:8080";

type Action = {
    label: string;
    endpoint: string;
    style: "primary" | "danger";
};

function actionsFor(status: OrderStatus): Action[] {
    switch (status) {
        case "DRAFT":
            return [
                { label: "Skicka", endpoint: "send", style: "primary" },
                { label: "Avbryt", endpoint: "cancel", style: "danger" },
            ];
        case "SENT":
            return [
                { label: "Bekräfta", endpoint: "confirm", style: "primary" },
                { label: "Avbryt", endpoint: "cancel", style: "danger" },
            ];
        case "CONFIRMED":
            return [
                { label: "Leverera", endpoint: "deliver", style: "primary" },
                { label: "Avbryt", endpoint: "cancel", style: "danger" },
            ];
        case "DELIVERED":
        case "CANCELLED":
            return [];
    }
}

function OrderDetailPage() {
    const { id } = useParams<{ id: string }>();
    const [refreshKey, setRefreshKey] = useState(0);
    const [actionLoading, setActionLoading] = useState(false);
    const [actionError, setActionError] = useState<string | null>(null);

    const { data, loading, error } = useFetch<Order>(
        `/api/orders/${id}?_=${refreshKey}`
    );

    async function performAction(endpoint: string) {
        setActionLoading(true);
        setActionError(null);
        try {
            const response = await fetch(
                `${API_BASE}/api/orders/${id}/${endpoint}`,
                { method: "POST" }
            );
            if (!response.ok) {
                let message = `HTTP ${response.status}`;
                try {
                    const body = await response.json();
                    if (body?.message) {
                        message = body.message;
                    }
                } catch {
                    // svaret var inte JSON - behåll status-meddelandet
                }
                throw new Error(message);
            }
            setRefreshKey((k) => k + 1);
        } catch (err) {
            setActionError(err instanceof Error ? err.message : "Okänt fel");
        } finally {
            setActionLoading(false);
        }
    }

    if (loading) {
        return <p className="text-slate-600">Laddar order...</p>;
    }

    if (error || !data) {
        return (
            <div className="rounded-md bg-red-50 border border-red-200 p-4">
                <p className="text-red-900 font-medium">Kunde inte hämta order</p>
                <p className="text-red-700 text-sm mt-1">{error ?? "Ingen data"}</p>
            </div>
        );
    }

    const actions = actionsFor(data.status);

    return (
        <div>
            <Link
                to="/orders"
                className="text-sm text-slate-600 hover:text-slate-900 mb-4 inline-block"
            >
                ← Tillbaka till beställningar
            </Link>

            <div className="flex items-baseline justify-between mb-6">
                <div>
                    <h2 className="text-2xl font-bold text-slate-900">
                        {data.orderNumber}
                    </h2>
                    {data.notes && (
                        <p className="text-sm text-slate-600 mt-1">{data.notes}</p>
                    )}
                </div>
                <StatusBadge status={data.status} />
            </div>

            <div className="grid grid-cols-2 gap-6 mb-6">
                <div className="bg-white rounded-md border border-slate-200 p-4">
                    <h3 className="text-sm font-medium text-slate-700 mb-3">Tidslinje</h3>
                    <dl className="space-y-2 text-sm">
                        <div className="flex justify-between">
                            <dt className="text-slate-600">Skapad</dt>
                            <dd className="text-slate-900 tabular-nums">
                                {new Date(data.createdAt).toLocaleString("sv-SE")}
                            </dd>
                        </div>
                        <div className="flex justify-between">
                            <dt className="text-slate-600">Skickad</dt>
                            <dd className="text-slate-900 tabular-nums">
                                {data.sentAt ? new Date(data.sentAt).toLocaleString("sv-SE") : "—"}
                            </dd>
                        </div>
                        <div className="flex justify-between">
                            <dt className="text-slate-600">Bekräftad</dt>
                            <dd className="text-slate-900 tabular-nums">
                                {data.confirmedAt ? new Date(data.confirmedAt).toLocaleString("sv-SE") : "—"}
                            </dd>
                        </div>
                        <div className="flex justify-between">
                            <dt className="text-slate-600">Levererad</dt>
                            <dd className="text-slate-900 tabular-nums">
                                {data.deliveredAt ? new Date(data.deliveredAt).toLocaleString("sv-SE") : "—"}
                            </dd>
                        </div>
                    </dl>
                </div>

                <div className="bg-white rounded-md border border-slate-200 p-4">
                    <h3 className="text-sm font-medium text-slate-700 mb-3">Åtgärder</h3>
                    {actions.length === 0 ? (
                        <p className="text-sm text-slate-500">
                            Inga åtgärder tillgängliga (terminalt tillstånd).
                        </p>
                    ) : (
                        <div className="flex gap-2">
                            {actions.map((action) => (
                                <button
                                    key={action.endpoint}
                                    onClick={() => performAction(action.endpoint)}
                                    disabled={actionLoading}
                                    className={
                                        action.style === "primary"
                                            ? "px-4 py-2 text-sm font-medium rounded-md bg-slate-900 text-white hover:bg-slate-800 disabled:opacity-50"
                                            : "px-4 py-2 text-sm font-medium rounded-md bg-white text-slate-700 border border-slate-300 hover:bg-slate-50 disabled:opacity-50"
                                    }
                                >
                                    {action.label}
                                </button>
                            ))}
                        </div>
                    )}
                    {actionError && (
                        <p className="text-sm text-red-700 mt-3">{actionError}</p>
                    )}
                </div>
            </div>

            <h3 className="text-sm font-medium text-slate-700 mb-2">Rader</h3>
            <div className="bg-white rounded-md border border-slate-200 overflow-hidden">
                <table className="w-full text-sm">
                    <thead className="bg-slate-50 text-left">
                    <tr>
                        <th className="px-4 py-3 font-medium text-slate-700">Läkemedel</th>
                        <th className="px-4 py-3 font-medium text-slate-700 text-right">Antal</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Anteckning</th>
                    </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200">
                    {data.lines.map((line) => (
                        <tr key={line.id} className="hover:bg-slate-50">
                            <td className="px-4 py-3 font-medium text-slate-900">
                                {line.medicationName}
                            </td>
                            <td className="px-4 py-3 text-slate-900 text-right tabular-nums">
                                {line.quantity}
                            </td>
                            <td className="px-4 py-3 text-slate-600">
                                {line.notes ?? "—"}
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

export default OrderDetailPage;