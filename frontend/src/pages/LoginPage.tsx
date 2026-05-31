import { useState, type FormEvent } from "react";
import { useAuth } from "../AuthContext";

/**
 * Inloggningsvy. Visas när ingen är inloggad (se App). Vid lyckad inloggning
 * uppdateras AuthContext och appen renderas om till huvudvyn.
 *
 * Demo-inloggningar visas i UI:t — det är ett internt demoverktyg, inte
 * produktion, så det är medvetet bekvämt för granskaren att testa.
 */
function LoginPage() {
    const { login } = useAuth();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    async function handleSubmit(e: FormEvent) {
        e.preventDefault();
        setError(null);
        setSubmitting(true);
        try {
            await login(email, password);
            // Vid framgång byter App automatiskt till huvudvyn.
        } catch (err) {
            setError(err instanceof Error ? err.message : "Inloggningen misslyckades");
        } finally {
            setSubmitting(false);
        }
    }

    return (
        <div className="min-h-screen bg-slate-50 flex items-center justify-center px-6">
            <div className="w-full max-w-sm">
                <div className="bg-white rounded-lg border border-slate-200 shadow-sm p-8">
                    <h1 className="text-2xl font-bold text-slate-900 mb-1">MediTrack</h1>
                    <p className="text-sm text-slate-500 mb-6">Logga in för att fortsätta</p>

                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">
                                E-post
                            </label>
                            <input
                                type="email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                autoComplete="username"
                                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm
                                           focus:outline-none focus:ring-2 focus:ring-slate-400"
                                required
                            />
                        </div>

                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">
                                Lösenord
                            </label>
                            <input
                                type="password"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                autoComplete="current-password"
                                className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm
                                           focus:outline-none focus:ring-2 focus:ring-slate-400"
                                required
                            />
                        </div>

                        {error && (
                            <div className="rounded-md bg-red-50 border border-red-200 px-3 py-2">
                                <p className="text-sm text-red-700">{error}</p>
                            </div>
                        )}

                        <button
                            type="submit"
                            disabled={submitting}
                            className="w-full rounded-md bg-slate-900 text-white py-2 text-sm font-medium
                                       hover:bg-slate-800 disabled:opacity-50"
                        >
                            {submitting ? "Loggar in..." : "Logga in"}
                        </button>
                    </form>

                    <div className="mt-6 pt-4 border-t border-slate-200">
                        <p className="text-xs text-slate-500 mb-2">Demo-inloggningar (lösenord: demo1234):</p>
                        <ul className="text-xs text-slate-600 space-y-1">
                            <li>anna.lindberg@meditrack.demo — admin</li>
                            <li>erik.svensson@meditrack.demo — apotekare</li>
                            <li>sara.johansson@meditrack.demo — sjuksköterska</li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default LoginPage;