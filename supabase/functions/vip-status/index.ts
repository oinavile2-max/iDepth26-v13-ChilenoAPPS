function env(name: string) {
  const value = Deno.env.get(name) || "";
  if (!value) throw new Error(`Secret ${name} ausente`);
  return value;
}

async function serviceFetch(path: string, init: RequestInit = {}) {
  const url = env("SUPABASE_URL");
  const key = env("SUPABASE_SERVICE_ROLE_KEY");
  const headers = new Headers(init.headers || {});
  headers.set("apikey", key);
  headers.set("Authorization", `Bearer ${key}`);
  return fetch(`${url}${path}`, { ...init, headers });
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
  try {
    const body = await req.json();
    const installId = String(body.install_id || "").trim().slice(0, 80);
    if (!installId) {
      return Response.json({ ok: false, error: "install_id ausente" }, { status: 400 });
    }

    const res = await serviceFetch(
      `/rest/v1/vip_overrides?select=enabled,expires_at&install_id=eq.${encodeURIComponent(installId)}&limit=1`
    );
    if (!res.ok) throw new Error(await res.text());
    const rows = await res.json();
    const row = Array.isArray(rows) && rows.length ? rows[0] : null;
    const now = Date.now();
    const expiresAt = row?.expires_at ? Date.parse(String(row.expires_at)) : 0;
    const active = Boolean(row?.enabled) && (!expiresAt || expiresAt > now);

    return Response.json({
      ok: true,
      admin_vip: active,
      expires_at: row?.expires_at || null,
    });
  } catch (e) {
    console.error(e);
    return Response.json({ ok: false, error: "Falha ao verificar VIP." }, { status: 500 });
  }
});
