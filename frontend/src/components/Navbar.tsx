import { NavLink } from "react-router-dom";
import { useAuth } from "../AuthContext";

const roleLabel: Record<string, string> = {
    NURSE: "Sjuksköterska",
    PHARMACIST: "Apotekare",
    ADMIN: "Administratör",
};

function Navbar() {
    const { user } = useAuth();
    const baseClasses = "px-4 py-2 text-sm font-medium transition-colors";
    const activeClasses = "text-slate-900 border-b-2 border-slate-900";
    const inactiveClasses = "text-slate-600 hover:text-slate-900";

    return (
        <nav className="bg-white border-b border-slate-200">
            <div className="max-w-7xl mx-auto px-6">
                <div className="flex items-center justify-between h-16">
                    <div>
                        <h1 className="text-xl font-bold text-slate-900">MediTrack</h1>
                    </div>
                    <div className="flex space-x-1">
                        <NavLink
                            to="/medications"
                            className={({ isActive }) =>
                                `${baseClasses} ${isActive ? activeClasses : inactiveClasses}`
                            }
                        >
                            Läkemedel
                        </NavLink>
                        <NavLink
                            to="/stock"
                            className={({ isActive }) =>
                                `${baseClasses} ${isActive ? activeClasses : inactiveClasses}`
                            }
                        >
                            Lager
                        </NavLink>
                        <NavLink
                            to="/orders"
                            className={({ isActive }) =>
                                `${baseClasses} ${isActive ? activeClasses : inactiveClasses}`
                            }
                        >
                            Beställningar
                        </NavLink>
                        {user && (
                            <span className="ml-4 pl-4 border-l border-slate-200 text-sm text-slate-500">
                                Inloggad som {user.name} · {roleLabel[user.role]}
                            </span>
                        )}
                    </div>
                </div>
            </div>
        </nav>
    );
}

export default Navbar;