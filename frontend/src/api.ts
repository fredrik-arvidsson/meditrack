const API_BASE = "http://localhost:8080";

export type FieldError = { field: string; message: string };

export class ApiValidationError extends Error {
    fieldErrors: FieldError[];
    constructor(message: string, fieldErrors: FieldError[]) {
        super(message);
        this.fieldErrors = fieldErrors;
    }
}

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
        ...(options.headers as Record<string, string> | undefined),
    };
    // Sätt Content-Type bara när det finns en body att skicka.
    if (options.body) {
        headers["Content-Type"] = headers["Content-Type"] ?? "application/json";
    }
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
            if (body && Array.isArray(body.fieldErrors)) {
                throw new ApiValidationError(message, body.fieldErrors);
            }
        } catch (parseErr) {
            if (parseErr instanceof ApiValidationError) throw parseErr;
            // ingen JSON-kropp — behåll generiskt meddelande
        }
        throw new Error(message);
    }

    // 204 No Content → inget att parsa, returnera null direkt.
    if (response.status === 204) {
        return null as T;
    }

    const text = await response.text();
    return (text ? JSON.parse(text) : null) as T;
}