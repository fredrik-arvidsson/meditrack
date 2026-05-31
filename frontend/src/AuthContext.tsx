import { createContext, useContext, useState, useEffect, type ReactNode } from "react";
import { apiFetch, setCredentials, clearCredentials, setAuthErrorHandler } from "./api";

// Inloggad användares profil — matchar MeResponse från backend.
export interface CurrentUser {
    id: number;
    name: string;
    email: string;
    role: "NURSE" | "PHARMACIST" | "ADMIN";
}

interface AuthContextValue {
    user: CurrentUser | null;
    login: (email: string, password: string) => Promise<void>;
    logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
    const [user, setUser] = useState<CurrentUser | null>(null);

    // Låt api-lagret signalera utloggning vid 401 (t.ex. om sessionen
    // tappas). Då nollställs user och login-vyn visas igen.
    useEffect(() => {
        setAuthErrorHandler(() => {
            clearCredentials();
            setUser(null);
        });
    }, []);

    async function login(email: string, password: string) {
        // Sätt credentials FÖRST, så att /api/me-anropet bär dem.
        setCredentials(email, password);
        try {
            // /api/me verifierar credentials (401 om fel) och ger oss rollen.
            const me = await apiFetch<CurrentUser>("/api/me");
            setUser(me);
        } catch (err) {
            // Inloggning misslyckades — rensa credentials igen.
            clearCredentials();
            setUser(null);
            throw err;
        }
    }

    function logout() {
        clearCredentials();
        setUser(null);
    }

    return (
        <AuthContext.Provider value={{ user, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
}

// Bekvämlighets-hook så komponenter slipper importera context-objektet.
export function useAuth(): AuthContextValue {
    const ctx = useContext(AuthContext);
    if (!ctx) {
        throw new Error("useAuth måste användas inom AuthProvider");
    }
    return ctx;
}