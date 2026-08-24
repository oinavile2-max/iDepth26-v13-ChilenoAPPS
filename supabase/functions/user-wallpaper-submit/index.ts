function toBase64(bytes: Uint8Array) {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, Math.min(i + chunk, bytes.length)));
  }
  return btoa(binary);
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });

  try {
    const form = await req.formData();
    const file = form.get("wallpaper");
    if (!(file instanceof File)) {
      return Response.json({ ok: false, message: "Imagem ausente." }, { status: 400 });
    }
    if (file.size > 12 * 1024 * 1024) {
      return Response.json({ ok: false, message: "Imagem acima de 12 MB." }, { status: 413 });
    }

    const installId = String(form.get("install_id") || "").slice(0, 80);
    const appVersion = String(form.get("app_version") || "").slice(0, 30);
    const bytes = new Uint8Array(await file.arrayBuffer());
    const ext = file.type.includes("png") ? "png" : file.type.includes("webp") ? "webp" : "jpg";
    const id = crypto.randomUUID();
    const path = `${new Date().toISOString().slice(0,10)}/${id}.${ext}`;

    const url = Deno.env.get("SUPABASE_URL")!;
    const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

    const storage = await fetch(`${url}/storage/v1/object/user-submissions/${path}`, {
      method: "POST",
      headers: {
        apikey: key,
        Authorization: `Bearer ${key}`,
        "Content-Type": file.type || "image/jpeg",
        "x-upsert": "false",
      },
      body: bytes,
    });
    if (!storage.ok) throw new Error(await storage.text());

    const row = {
      id,
      install_id: installId,
      storage_path: path,
      original_name: file.name || "wallpaper",
      mime_type: file.type || "image/jpeg",
      size_bytes: file.size,
      status: "pending",
      notes: `app ${appVersion}`,
    };

    const db = await fetch(`${url}/rest/v1/user_wallpaper_submissions`, {
      method: "POST",
      headers: {
        apikey: key,
        Authorization: `Bearer ${key}`,
        "Content-Type": "application/json",
        Prefer: "return=minimal",
      },
      body: JSON.stringify(row),
    });
    if (!db.ok) throw new Error(await db.text());

    // E-mail é opcional: configure RESEND_API_KEY, CURATION_EMAIL e MAIL_FROM.
    const resend = Deno.env.get("RESEND_API_KEY") || "";
    const to = Deno.env.get("CURATION_EMAIL") || "";
    const from = Deno.env.get("MAIL_FROM") || "";
    if (resend && to && from) {
      const mail = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${resend}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          from,
          to: [to],
          subject: "Novo wallpaper enviado pelo iDepth 26",
          html: `<p>Novo wallpaper aguardando curadoria.</p><p>ID: ${id}</p><p>Instalação: ${installId}</p>`,
          attachments: [{
            filename: file.name || `wallpaper.${ext}`,
            content: toBase64(bytes),
          }],
        }),
      });
      const mailText = await mail.text();
      const emailRow = {
        submission_id: id,
        subject: "Novo wallpaper enviado pelo iDepth 26",
        status: mail.ok ? "sent" : "failed",
        detail: mailText.slice(0, 500),
      };
      const emailLog = await fetch(`${url}/rest/v1/email_deliveries`, {
        method: "POST",
        headers: {
          apikey: key,
          Authorization: `Bearer ${key}`,
          "Content-Type": "application/json",
          Prefer: "return=minimal",
        },
        body: JSON.stringify(emailRow),
      });
      if (!emailLog.ok) console.error("email log:", await emailLog.text());
      if (!mail.ok) console.error("Resend:", mailText);
    }

    return Response.json({ ok: true, message: "Wallpaper enviado para curadoria.", submission_id: id });
  } catch (e) {
    console.error(e);
    return Response.json({ ok: false, message: "Falha no envio." }, { status: 500 });
  }
});
