import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import Navbar from "./components/Navbar";
import MedicationsPage from "./pages/MedicationsPage";
import StockPage from "./pages/StockPage";
import OrdersPage from "./pages/OrdersPage";
import OrderDetailPage from "./pages/OrderDetailPage";

function App() {
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

export default App;