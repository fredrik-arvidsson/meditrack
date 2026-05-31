import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./AuthContext";
import Navbar from "./components/Navbar";
import LoginPage from "./pages/LoginPage";
import MedicationsPage from "./pages/MedicationsPage";
import StockPage from "./pages/StockPage";
import OrdersPage from "./pages/OrdersPage";
import OrderDetailPage from "./pages/OrderDetailPage";

/**
 * Innehållet som kräver inloggning. Ligger separat så det kan läsa
 * useAuth() — det måste ske INNANFÖR AuthProvider (annars finns ingen
 * context). Är ingen inloggad visas LoginPage; annars hela appen.
 */
function AppContent() {
    const { user } = useAuth();

    if (!user) {
        return <LoginPage />;
    }

    return (
        <BrowserRouter>
            <div className="min-h-screen bg-slate-50">
                <Navbar />
                <main className="max-w-7xl mx-auto px-6 py-8">
                    <Routes>
                        <Route path="/" element={<Navigate to="/medications" replace />} />
                        <Route path="/medications" element={<MedicationsPage />} />
                        <Route path="/stock" element={<StockPage />} />
                        <Route path="/orders" element={<OrdersPage />} />
                        <Route path="/orders/:id" element={<OrderDetailPage />} />
                    </Routes>
                </main>
            </div>
        </BrowserRouter>
    );
}

function App() {
    // AuthProvider ligger ÖVERST så att hela appen (inkl. LoginPage) har
    // tillgång till auth-kontexten.
    return (
        <AuthProvider>
            <AppContent />
        </AuthProvider>
    );
}

export default App;