import { useEffect, useState } from "react";
import { apiFetch } from "../api";

export function useFetch<T>(path: string) {
    const [data, setData] = useState<T | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        setError(null);

        apiFetch<T>(path)
            .then((json) => {
                if (!cancelled) {
                    setData(json);
                    setLoading(false);
                }
            })
            .catch((err) => {
                if (!cancelled) {
                    setError(err instanceof Error ? err.message : "Något gick fel");
                    setLoading(false);
                }
            });

        return () => {
            cancelled = true;
        };
    }, [path]);

    return { data, loading, error };
}