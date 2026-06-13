import { useState } from "react";
import { Link } from "react-router-dom";
import { useFetch } from "../hooks/useFetch";
import StatusBadge from "../components/StatusBadge";
import OrderForm from "../components/OrderForm";
import type { Order } from "../types/medication";

function OrdersPage() {
    const [refreshKey, setRefreshKey] = useState(0);
    const [showCreateForm, setShowCreateForm] = useState(false);

    const { data, loading, error } = useFetch<Order[]>(
        `/api/orders?_=${refreshKey}`
    );

    function handleSaved() {
        setShowCreateForm(false);
        setRefreshKey((k) => k + 1);
    }

    function handleCancel() {
        setShowCreateForm(false);
    }

    return (
        <div>
            <div className="flex items-baseline justify-between mb-4">
                <h2 className="text-2xl font-bold text-slate-900">
                    {data && data.length > 0
                        ? `Beställningar för ${data[0].careUnitName}`
                        : "Beställningar"}
                </h2>
                {!showCreateForm && (
                    <button
                        onClick={() => setShowCreateForm(true)}
                        className="px-4 py-2 text-sm font-medium rounded-md bg-slate-900 text-white hover:bg-slate-800"
                    >
                        Skapa beställning
                    </button>
                )}
            </div>

            {showCreateForm && (
                <OrderForm onSaved={handleSaved} onCancel={handleCancel} />
            )}

            {error && (
                <div className="rounded-md bg-red-50 border border-red-200 p-4 mb-4">
                    <p className="text-red-900 font-medium">Kunde inte hämta beställningar</p>
                    <p className="text-red-700 text-sm mt-1">{error}</p>
                </div>
            )}

            <div className="bg-white rounded-md border border-slate-200 overflow-hidden">
                <table className="w-full text-sm">
                    <thead className="bg-slate-50 text-left">
                    <tr>
                        <th className="px-4 py-3 font-medium text-slate-700">Ordernummer</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Status</th>
                        <th className="px-4 py-3 font-medium text-slate-700 text-right">Rader</th>
                        <th className="px-4 py-3 font-medium text-slate-700">Skapad</th>
                        <th className="px-4 py-3"></th>
                    </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200">
                    {loading && (
                        <tr>
                            <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                                Laddar beställningar...
                            </td>
                        </tr>
                    )}
                    {!loading && data?.length === 0 && (
                        <tr>
                            <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                                Inga beställningar än.
                            </td>
                        </tr>
                    )}
                    {!loading && data?.map((order) => (
                        <tr key={order.id} className="hover:bg-slate-50">
                            <td className="px-4 py-3 font-medium text-slate-900">
                                {order.orderNumber}
                            </td>
                            <td className="px-4 py-3">
                                <StatusBadge status={order.status} />
                            </td>
                            <td className="px-4 py-3 text-slate-600 text-right tabular-nums">
                                {order.lines.length}
                            </td>
                            <td className="px-4 py-3 text-slate-600">
                                {new Date(order.createdAt).toLocaleDateString("sv-SE")}
                            </td>
                            <td className="px-4 py-3 text-right">
                                <Link
                                    to={`/orders/${order.id}`}
                                    className="text-sm text-slate-700 hover:text-slate-900 underline"
                                >
                                    Visa detaljer
                                </Link>
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}

export default OrdersPage;