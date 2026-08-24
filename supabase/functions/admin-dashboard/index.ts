function decodeB64url(value: string) {
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((value.length + 3) % 4);
  const binary = atob(padded);
  return Uint8Array.from(binary, c => c.charCodeAt(0));
}
function b64url(bytes: Uint8Array) {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
async function verify(token: string, secret: string) {
  const parts = token.split(".");
  if (parts.length !== 3) return false;
  const data = `${parts[0]}.${parts[1]}`;
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data)));
  if (b64url(sig) !== parts[2]) return false;
  const payload = JSON.parse(new TextDecoder().decode(decodeB64url(parts[1])));
  return payload.sub === "idepth-admin" && Number(payload.exp || 0) > Math.floor(Date.now() / 1000);
}

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

async function getSubmission(id: string) {
  const res = await serviceFetch(`/rest/v1/user_wallpaper_submissions?select=*&id=eq.${encodeURIComponent(id)}&limit=1`);
  if (!res.ok) throw new Error(await res.text());
  const rows = await res.json();
  return Array.isArray(rows) && rows.length ? rows[0] : null;
}

async function setSubmissionStatus(id: string, status: "approved" | "rejected") {
  const res = await serviceFetch(`/rest/v1/user_wallpaper_submissions?id=eq.${encodeURIComponent(id)}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", Prefer: "return=minimal" },
    body: JSON.stringify({ status, reviewed_at: new Date().toISOString() }),
  });
  if (!res.ok) throw new Error(await res.text());
}

async function setVipOverride(installId: string, enabled: boolean, durationDays: number) {
  const expiresAt = enabled && durationDays > 0
    ? new Date(Date.now() + durationDays * 86400000).toISOString()
    : null;
  const res = await serviceFetch(`/rest/v1/vip_overrides?on_conflict=install_id`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Prefer: "resolution=merge-duplicates,return=representation",
    },
    body: JSON.stringify({
      install_id: installId,
      enabled,
      expires_at: expiresAt,
      note: "admin-panel",
      updated_at: new Date().toISOString(),
    }),
  });
  if (!res.ok) throw new Error(await res.text());
  const rows = await res.json();
  return Array.isArray(rows) && rows.length ? rows[0] : null;
}

async function signedPreview(storagePath: string) {
  if (!storagePath) return "";
  const encoded = storagePath.split("/").map(encodeURIComponent).join("/");
  const res = await serviceFetch(`/storage/v1/object/sign/user-submissions/${encoded}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ expiresIn: 600 }),
  });
  if (!res.ok) return "";
  const json = await res.json();
  const signed = String(json.signedURL || json.signedUrl || "");
  if (!signed) return "";
  if (signed.startsWith("http")) return signed;
  return `${env("SUPABASE_URL")}/storage/v1${signed}`;
}

async function readSubmissionBytes(storagePath: string) {
  const encoded = storagePath.split("/").map(encodeURIComponent).join("/");
  const res = await serviceFetch(`/storage/v1/object/user-submissions/${encoded}`);
  if (!res.ok) throw new Error(await res.text());
  return new Uint8Array(await res.arrayBuffer());
}

function toBase64(bytes: Uint8Array) {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, Math.min(i + chunk, bytes.length)));
  }
  return btoa(binary);
}

async function logEmail(submissionId: string, subject: string, status: string, detail = "") {
  const res = await serviceFetch(`/rest/v1/email_deliveries`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Prefer: "return=minimal" },
    body: JSON.stringify({ submission_id: submissionId, subject, status, detail }),
  });
  if (!res.ok) console.error("email log", await res.text());
}

async function emailSubmission(row: any) {
  const resend = env("RESEND_API_KEY");
  const to = env("CURATION_EMAIL");
  const from = env("MAIL_FROM");
  const bytes = await readSubmissionBytes(row.storage_path);
  const filename = row.original_name || "wallpaper.jpg";
  const subject = `Wallpaper do usuário — ${row.id}`;

  const mail = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${resend}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from,
      to: [to],
      subject,
      html: `<p>Wallpaper aguardando curadoria do iDepth 26.</p><p>ID: ${row.id}</p><p>Status: ${row.status}</p>`,
      attachments: [{ filename, content: toBase64(bytes) }],
    }),
  });

  const responseText = await mail.text();
  if (!mail.ok) {
    await logEmail(row.id, subject, "failed", responseText.slice(0, 500));
    throw new Error(`Resend HTTP ${mail.status}`);
  }
  await logEmail(row.id, subject, "sent", responseText.slice(0, 500));
}

async function metrics() {
  const res = await serviceFetch(`/rest/v1/rpc/admin_dashboard_metrics`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: "{}",
  });
  if (!res.ok) throw new Error(await res.text());
  const data = await res.json();

  if (Array.isArray(data.submissions)) {
    data.submissions = await Promise.all(data.submissions.map(async (item: any) => ({
      ...item,
      preview_url: await signedPreview(String(item.storage_path || "")),
    })));
  }
  return data;
}

Deno.serve(async (req) => {
  try {
    const auth = req.headers.get("authorization") || "";
    const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
    const secret = Deno.env.get("ADMIN_SESSION_SECRET") || "";
    if (!token || !secret || !(await verify(token, secret))) {
      return Response.json({ ok: false, error: "Sessão inválida." }, { status: 401 });
    }

    if (req.method === "GET") {
      return Response.json({ ok: true, ...(await metrics()) });
    }

    if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });
    const body = await req.json();
    const action = String(body.action || "");

    if (action === "vip_set") {
      const installId = String(body.install_id || "").trim().slice(0, 80);
      const enabled = Boolean(body.enabled);
      const durationDays = Math.max(0, Math.min(3650, Number(body.duration_days || 0)));
      if (!installId) return Response.json({ ok: false, error: "install_id ausente." }, { status: 400 });
      const row = await setVipOverride(installId, enabled, durationDays);
      return Response.json({
        ok: true,
        message: enabled ? "VIP ativado." : "VIP desativado.",
        vip: row,
      });
    }

    const id = String(body.submission_id || "");
    if (!id) return Response.json({ ok: false, error: "submission_id ausente." }, { status: 400 });
    const row = await getSubmission(id);
    if (!row) return Response.json({ ok: false, error: "Envio não encontrado." }, { status: 404 });

    if (action === "approve") {
      await setSubmissionStatus(id, "approved");
      return Response.json({ ok: true, message: "Wallpaper aprovado." });
    }
    if (action === "reject") {
      await setSubmissionStatus(id, "rejected");
      return Response.json({ ok: true, message: "Wallpaper rejeitado." });
    }
    if (action === "email") {
      await emailSubmission(row);
      return Response.json({ ok: true, message: "Wallpaper enviado por e-mail." });
    }

    return Response.json({ ok: false, error: "Ação inválida." }, { status: 400 });
  } catch (e) {
    console.error(e);
    return Response.json({ ok: false, error: "Falha no painel administrativo." }, { status: 500 });
  }
});
