const API_BASE = "http://localhost:8080";

// Credentials hålls i minnet (modulnivå), satt vid inloggning. Inte i
// localStorage: för en intern demo räcker minneslagring, och vi slipper
// exponera lösenord i webbläsarlagring. Vid sidladdning är man utloggad.
let authHeader: string | null = null;

export function setCredentials(email: string, password: string) {
    // Basic auth: base64(email:lösenord) i Authorization-headern.
    authHeader = "Basic " + btoa(`${email}:${password}`);
}

export function clearCredentials() {
    authHeader = null;
}

// En 401/403 från servern ska kunna trigga utloggning i UI:t. Vi låter
// AuthContext registrera en callback här, så api-lagret slipper känna
// till React men ändå kan signalera "du är utloggad".
let onAuthError: (() => void) | null = null;

export function setAuthErrorHandler(handler: () => void) {
    onAuthError = handler;
}

/**
 * Central fetch för hela appen. Lägger på auth-headern automatiskt,
 * sätter JSON-content-type, och översätter fel till begripliga meddelanden.
 * 401 → credentials saknas/fel → logga ut. 403 → inloggad men saknar
 * behörighet → visa serverns meddelande (t.ex. separation of duties).
 */
export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
    const headers: Record<string, string> = {
        "Content-Type": "application/json",
        ...(options.headers as Record<string, string> | undefined),
    };
    if (authHeader) {
        headers["Authorization"] = authHeader;
    }

    const response = await fetch(`${API_BASE}${path}`, { ...options, headers });

    if (response.status === 401) {
        // Fel eller saknade credentials — logga ut så login visas igen.
        if (onAuthError) onAuthError();
        throw new Error("Inloggningen misslyckades eller har gått ut.");
    }

    if (!response.ok) {
        // Försök läsa serverns felmeddelande (vår ApiError har "message").
        let message = `HTTP ${response.status}`;
        try {
            const body = await response.json();
            if (body && body.message) message = body.message;
        } catch {
            // ingen JSON-kropp — behåll generiskt meddelande
        }
        throw new Error(message);
    }

    // 204 No Content eller tom kropp → returnera null som T.
    const text = await response.text();
    return (text ? JSON.parse(text) : null) as T;
}