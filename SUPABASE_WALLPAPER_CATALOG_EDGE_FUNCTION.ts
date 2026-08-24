// Criar uma NOVA Edge Function chamada: wallpaper-catalog
// Settings: Verify JWT with legacy secret = OFF
// Esta função é somente leitura. Ela NÃO expõe a service role key.

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
      },
    });
  }

  if (req.method !== "GET" && req.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

  if (!supabaseUrl || !serviceKey) {
    return Response.json({ error: "Supabase environment unavailable" }, { status: 500 });
  }

  const query =
    `${supabaseUrl}/rest/v1/wallpapers?` +
    `select=slug,name,category,thumbnail_url,background_url,homescreen_url,foreground_url,is_new,featured,depth_mode,clock_depth_ready,vip_only,created_at` +
    `&published=eq.true&order=created_at.desc`;

  const response = await fetch(query, {
    headers: {
      apikey: serviceKey,
      Authorization: `Bearer ${serviceKey}`,
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    return Response.json(
      { error: "Database query failed", detail: await response.text() },
      { status: 500 }
    );
  }

  const rows = await response.json();
  const wallpapers = rows.map((w: any) => ({
    id: w.slug,
    name: w.name,
    category: w.category,
    thumbnail: w.thumbnail_url,
    lockscreen: w.background_url,
    homescreen: w.homescreen_url,
    foreground: w.foreground_url,
    new: w.is_new,
    featured: w.featured,
    depth_mode: w.depth_mode,
    clock_depth_ready: w.clock_depth_ready,
    vip_only: w.vip_only ?? false,
    created_at: w.created_at,
  }));

  return Response.json(
    {
      version: 1,
      updated_at: new Date().toISOString(),
      wallpapers,
    },
    {
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Cache-Control": "public, max-age=60",
      },
    }
  );
});
