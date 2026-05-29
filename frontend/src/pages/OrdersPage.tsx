import { Link } from "react-router-dom";
import { useFetch } from "../hooks/useFetch";
import StatusBadge from "../components/StatusBadge";
import type { Order } from "../types/medication";

function OrdersPage() {
    const { data, loading, error } = useFetch<Order[]>("/api/orders");

    if (loading) {
        return <p className="text-slate-600">Laddar beställningar...</p>;
    }

    if (error) {
        return (
            <div className="rounded-md bg-red-50 border border-red-200 p-4">
                <p className="text-red-900 font-medium">Kunde inte hämta beställningar</p>
                <p className="text-red-700 text-sm mt-1">{error}</p>
            </div>
        );
    }

    return (
        <div>
            <h2 className="text-2xl font-bold text-slate-900 mb-4">Beställningar</h2>

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
                    {data?.map((order) => (
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