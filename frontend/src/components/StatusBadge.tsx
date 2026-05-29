import type { OrderStatus } from "../types/medication";

const STATUS_CONFIG: Record<OrderStatus, { label: string; classes: string }> = {
    DRAFT:     { label: "Utkast",     classes: "bg-slate-100 text-slate-700" },
    SENT:      { label: "Skickad",    classes: "bg-blue-100 text-blue-800" },
    CONFIRMED: { label: "Bekräftad",  classes: "bg-indigo-100 text-indigo-800" },
    DELIVERED: { label: "Levererad",  classes: "bg-emerald-100 text-emerald-800" },
    CANCELLED: { label: "Avbruten",   classes: "bg-red-100 text-red-800" },
};

type Props = {
    status: OrderStatus;
};

function StatusBadge({ status }: Props) {
    const config = STATUS_CONFIG[status];
    return (
        <span
            className={`inline-block px-2 py-0.5 text-xs rounded font-medium ${config.classes}`}
        >
            {config.label}
        </span>
    );
}

export default StatusBadge;