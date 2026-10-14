import "server-only";

// Серверные компоненты ходят в бэкенд по внутренней сети Docker.
const base = process.env.INTERNAL_API_URL ?? process.env.BACKEND_URL ?? "http://localhost:8080";

export async function serverGet<T>(path: string): Promise<{ ok: true; data: T } | { ok: false; status: number }> {
  try {
    const res = await fetch(`${base}${path}`, { cache: "no-store" });
    if (!res.ok) return { ok: false, status: res.status };
    return { ok: true, data: (await res.json()) as T };
  } catch {
    return { ok: false, status: 503 };
  }
}
