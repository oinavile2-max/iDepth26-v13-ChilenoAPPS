const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store, max-age=0",
};

function env(name: string): string {
  const value = Deno.env.get(name) || "";
  if (!value) throw new Error(`Secret ${name} ausente`);
  return value;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: CORS });
  }

  if (req.method !== "GET") {
    return new Response(JSON.stringify({ ok: false, error: "Method not allowed" }), {
      status: 405,
      headers: CORS,
    });
  }

  try {
    const supabaseUrl = env("SUPABASE_URL");
    const serviceKey = env("SUPABASE_SERVICE_ROLE_KEY");

    const select = [
      "id",
      "slug",
      "name",
      "category",
      "thumbnail_url",
      "background_url",
      "homescreen_url",
      "foreground_url",
      "is_new",
      "featured",
      "depth_mode",
      "clock_depth_ready",
      "created_at",
    ].join(",");

    const url =
      `${supabaseUrl}/rest/v1/wallpapers` +
      `?select=${encodeURIComponent(select)}` +
      `&published=eq.true` +
      `&order=created_at.desc` +
      `&limit=500`;

    const res = await fetch(url, {
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
        Accept: "application/json",
      },
    });

    if (!res.ok) {
      throw new Error(`PostgREST HTTP ${res.status}: ${await res.text()}`);
    }

    const rows = await res.json();
    const wallpapers = (Array.isArray(rows) ? rows : [])
      .filter((row: any) => row && row.name && row.background_url)
      .map((row: any) => ({
        ...row,
        // Os 5 wallpapers grátis já pertencem ao pack fixo do app.
        // Todo lançamento remoto publicado depois entra como conteúdo VIP.
        vip_only: true,
      }));

    return new Response(
      JSON.stringify({
        ok: true,
        count: wallpapers.length,
        wallpapers,
        generated_at: new Date().toISOString(),
      }),
      { status: 200, headers: CORS },
    );
  } catch (error) {
    console.error(error);
    return new Response(
      JSON.stringify({
        ok: false,
        error: "Falha ao carregar catálogo de wallpapers.",
      }),
      { status: 500, headers: CORS },
    );
  }
});
