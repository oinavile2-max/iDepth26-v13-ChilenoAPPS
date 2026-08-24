const cors = {
  "content-type": "application/json",
};

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });

  try {
    const body = await req.json();
    const installId = String(body.install_id || "").slice(0, 80);
    const event = String(body.event || "").slice(0, 80);

    if (!installId || !event) {
      return Response.json({ ok: false, error: "invalid event" }, { status: 400 });
    }

    const row = {
      install_id: installId,
      event,
      details: String(body.details || "").slice(0, 500),
      app_version: String(body.app_version || "").slice(0, 30),
      android_version: String(body.android_version || "").slice(0, 50),
      device: String(body.device || "").slice(0, 120),
    };

    const url = Deno.env.get("SUPABASE_URL")!;
    const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const res = await fetch(`${url}/rest/v1/app_events`, {
      method: "POST",
      headers: {
        apikey: key,
        Authorization: `Bearer ${key}`,
        "Content-Type": "application/json",
        Prefer: "return=minimal",
      },
      body: JSON.stringify(row),
    });

    if (!res.ok) throw new Error(await res.text());
    return Response.json({ ok: true }, { headers: cors });
  } catch (e) {
    console.error(e);
    return Response.json({ ok: false }, { status: 500, headers: cors });
  }
});
