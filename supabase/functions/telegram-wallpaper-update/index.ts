import JSZip from "npm:jszip@3.10.1";

const BUCKET = "idepth26";
const TELEGRAM_API = "https://api.telegram.org";

type WallpaperRecord = {
  slug: string;
  name: string;
  category: string;
  thumbnail_url: string | null;
  background_url: string;
  homescreen_url: string;
  foreground_url: string | null;
  published: boolean;
  is_new: boolean;
  featured: boolean;
  depth_mode: string;
  clock_depth_ready: boolean;
};

Deno.serve(async (req) => {
  try {
    const url = new URL(req.url);

    if (req.method === "OPTIONS") {
      return new Response("ok");
    }

    // Endpoint simples de saúde.
    if (req.method === "GET" && url.searchParams.get("health") === "1") {
      return Response.json({ ok: true, service: "iDepth26 Telegram Auto Publisher" });
    }

    if (req.method !== "POST") {
      return new Response("Method not allowed", { status: 405 });
    }

    const payload = await req.json();

    // 1) CHAMADA DO TRIGGER DO BANCO -> anuncia no canal.
    if (payload?.record && payload?.table === "wallpapers") {
      requireDbWebhookSecret(req);
      await announceWallpaper(payload.record);
      return Response.json({ ok: true, mode: "database-announcement" });
    }

    // 2) UPDATE RECEBIDO DO TELEGRAM -> publica no Supabase.
    if (payload?.update_id) {
      requireTelegramSecret(req);
      await handleTelegramUpdate(payload);
      return Response.json({ ok: true, mode: "telegram-ingest" });
    }

    return Response.json({ error: "Unsupported payload" }, { status: 400 });
  } catch (error) {
    console.error(error);
    return Response.json(
      { error: "telegram-wallpaper-update failed", detail: String(error) },
      { status: 500 },
    );
  }
});

function env(name: string, required = true): string {
  const value = Deno.env.get(name) || "";
  if (required && !value) throw new Error(`Secret ${name} ausente`);
  return value;
}

function requireDbWebhookSecret(req: Request) {
  const expected = env("WEBHOOK_SECRET");
  const received = req.headers.get("x-webhook-secret") || "";
  if (received !== expected) throw new Error("DB webhook unauthorized");
}

function requireTelegramSecret(req: Request) {
  const expected = env("TELEGRAM_WEBHOOK_SECRET");
  const received = req.headers.get("x-telegram-bot-api-secret-token") || "";
  if (!expected || received !== expected) throw new Error("Telegram webhook unauthorized");
}

async function handleTelegramUpdate(update: any) {
  const msg = update.message || update.edited_message;
  if (!msg) return;

  const fromId = String(msg.from?.id || "");
  const chatId = String(msg.chat?.id || "");
  const text = String(msg.caption || msg.text || "").trim();

  // Permite descobrir o ID antes de configurar o ADMIN.
  if (/^\/id\b/i.test(text)) {
    await sendMessage(chatId, `Seu Telegram User ID é: ${fromId}`);
    return;
  }

  const adminId = env("TELEGRAM_ADMIN_USER_ID", false);
  if (!adminId) {
    await sendMessage(
      chatId,
      "Admin ainda não configurado. Envie /id e salve esse número no secret TELEGRAM_ADMIN_USER_ID.",
    );
    return;
  }

  if (fromId !== adminId) {
    await sendMessage(chatId, "Acesso negado.");
    return;
  }

  if (/^\/help\b/i.test(text) || (!msg.document && !msg.photo)) {
    await sendHelp(chatId);
    return;
  }

  const meta = parseCaption(text);

  if (msg.document) {
    const fileName = String(msg.document.file_name || "wallpaper");
    const fileId = String(msg.document.file_id || "");

    if (/\.zip$/i.test(fileName) || msg.document.mime_type === "application/zip") {
      const bytes = await downloadTelegramFile(fileId);
      await publishZip(chatId, bytes, fileName, meta);
      return;
    }

    if (isImageName(fileName) || String(msg.document.mime_type || "").startsWith("image/")) {
      const bytes = await downloadTelegramFile(fileId);
      await publishSingleImage(chatId, bytes, fileName, msg.document.mime_type || "", meta);
      return;
    }

    await sendMessage(chatId, "Arquivo não suportado. Envie JPG/PNG/WEBP ou um ZIP.");
    return;
  }

  if (Array.isArray(msg.photo) && msg.photo.length) {
    const largest = msg.photo[msg.photo.length - 1];
    const bytes = await downloadTelegramFile(String(largest.file_id));
    const inferred = {
      ...meta,
      name: meta.name || `Wallpaper ${new Date().toISOString().slice(0, 10)}`,
    };
    await publishSingleImage(chatId, bytes, "wallpaper.jpg", "image/jpeg", inferred);
  }
}

function parseCaption(text: string) {
  // Exemplos:
  // /add Ferrari Red | Carros
  // /add Coelho Preto | Animais
  // Sem /add: nome é inferido pelo arquivo e categoria = Novidades.
  const m = text.match(/^\/(?:add|novo)\s*(.*?)\s*(?:\|\s*(.*))?$/i);
  if (!m) return { name: "", category: "Novidades" };

  return {
    name: (m[1] || "").trim(),
    category: (m[2] || "Novidades").trim() || "Novidades",
  };
}

async function publishSingleImage(
  chatId: string,
  bytes: Uint8Array,
  originalName: string,
  mime: string,
  meta: { name: string; category: string },
) {
  const name = meta.name || prettyName(stripExtension(originalName));
  const category = meta.category || "Novidades";
  const slug = slugify(`${category}-${name}`);
  const ext = imageExtension(originalName, mime);
  const objectPath = `wallpapers/telegram/${slug}/lockscreen.${ext}`;

  await uploadStorage(objectPath, bytes, mimeFromExtension(ext));
  const background = publicUrl(objectPath);

  const existed = await wallpaperExists(slug);

  const row: WallpaperRecord = {
    slug,
    name,
    category,
    thumbnail_url: background,
    background_url: background,
    homescreen_url: background,
    foreground_url: null,
    published: true,
    is_new: true,
    featured: false,
    depth_mode: "single_image",
    clock_depth_ready: false,
  };

  await upsertWallpaper(row);

  await sendMessage(
    chatId,
    `✅ ${existed ? "Atualizado" : "Publicado"}: ${name}\n` +
    `Categoria: ${category}\n` +
    `Tipo: imagem simples\n` +
    `Miniatura: automática\n` +
    `Já disponível para o catálogo do app.`,
  );

  if (existed) await announceWallpaper(row, true);
}

async function publishZip(
  chatId: string,
  bytes: Uint8Array,
  zipFileName: string,
  captionMeta: { name: string; category: string },
) {
  const zip = await JSZip.loadAsync(bytes);

  let metaJson: any = {};
  const metaEntry = Object.values(zip.files).find((f: any) =>
    !f.dir && /(^|\/)meta\.json$/i.test(f.name)
  ) as any;

  if (metaEntry) {
    try {
      metaJson = JSON.parse(await metaEntry.async("string"));
    } catch {
      metaJson = {};
    }
  }

  const name =
    captionMeta.name ||
    String(metaJson.name || "").trim() ||
    prettyName(stripExtension(zipFileName));

  const category =
    (captionMeta.category !== "Novidades" ? captionMeta.category : "") ||
    String(metaJson.category || "").trim() ||
    "Novidades";

  const slug = slugify(`${category}-${name}`);

  const entries = Object.values(zip.files).filter((f: any) => !f.dir) as any[];

  const bg = pickZipEntry(entries, [
    "lockscreen", "background", "wallpaper", "fundo", "base",
  ]);

  const home = pickZipEntry(entries, [
    "homescreen", "home", "inicio", "inicial",
  ]);

  const fg = pickZipEntry(entries, [
    "foreground", "subject", "front", "assunto", "recorte",
  ], true);

  const thumb = pickZipEntry(entries, [
    "thumbnail", "thumb", "preview", "miniatura",
  ]);

  let backgroundEntry = bg;
  if (!backgroundEntry) {
    backgroundEntry = entries.find((f: any) =>
      isImageName(baseName(f.name)) &&
      !matchesAny(baseName(f.name), ["foreground", "subject", "front", "assunto", "recorte", "thumbnail", "thumb", "preview", "miniatura", "homescreen", "home"])
    );
  }

  if (!backgroundEntry) {
    await sendMessage(chatId, "❌ ZIP sem imagem principal. Inclua lockscreen.jpg ou background.jpg.");
    return;
  }

  const uploaded: Record<string, string> = {};

  uploaded.background = await uploadZipImage(backgroundEntry, slug, "lockscreen");

  if (home) uploaded.home = await uploadZipImage(home, slug, "homescreen");
  if (fg) uploaded.foreground = await uploadZipImage(fg, slug, "foreground");
  if (thumb) uploaded.thumb = await uploadZipImage(thumb, slug, "thumb");

  const existed = await wallpaperExists(slug);

  const row: WallpaperRecord = {
    slug,
    name,
    category,
    thumbnail_url: uploaded.thumb || uploaded.background,
    background_url: uploaded.background,
    homescreen_url: uploaded.home || uploaded.background,
    foreground_url: uploaded.foreground || null,
    published: true,
    is_new: true,
    featured: Boolean(metaJson.featured ?? false),
    depth_mode: uploaded.foreground ? "layered" : "single_image",
    clock_depth_ready: Boolean(uploaded.foreground),
  };

  await upsertWallpaper(row);

  await sendMessage(
    chatId,
    `✅ ${existed ? "Atualizado" : "Publicado"}: ${name}\n` +
    `Categoria: ${category}\n` +
    `Tipo: ${uploaded.foreground ? "PROFUNDIDADE REAL / layered" : "imagem simples"}\n` +
    `Foreground: ${uploaded.foreground ? "SIM ✅" : "não"}\n` +
    `Homescreen: ${uploaded.home ? "SIM" : "automático"}\n` +
    `Miniatura: ${uploaded.thumb ? "arquivo próprio" : "automática"}\n` +
    `Já disponível para o catálogo do app.`,
  );

  if (existed) await announceWallpaper(row, true);
}

async function uploadZipImage(entry: any, slug: string, canonicalName: string) {
  const original = baseName(entry.name);
  const ext = imageExtension(original, "");
  const bytes = await entry.async("uint8array");
  const path = `wallpapers/telegram/${slug}/${canonicalName}.${ext}`;
  await uploadStorage(path, bytes, mimeFromExtension(ext));
  return publicUrl(path);
}

function pickZipEntry(entries: any[], keywords: string[], pngPreferred = false) {
  const candidates = entries.filter((f: any) => {
    const name = baseName(f.name).toLowerCase();
    return isImageName(name) && matchesAny(name, keywords);
  });

  if (!candidates.length) return null;
  if (pngPreferred) {
    const png = candidates.find((f: any) => /\.png$/i.test(baseName(f.name)));
    if (png) return png;
  }
  return candidates[0];
}

function matchesAny(value: string, keywords: string[]) {
  const v = value.toLowerCase();
  return keywords.some((k) => v.includes(k.toLowerCase()));
}

async function downloadTelegramFile(fileId: string): Promise<Uint8Array> {
  const token = env("TELEGRAM_BOT_TOKEN");

  const getFileRes = await fetch(
    `${TELEGRAM_API}/bot${token}/getFile?file_id=${encodeURIComponent(fileId)}`,
  );

  const getFileJson = await getFileRes.json();
  if (!getFileRes.ok || !getFileJson?.ok || !getFileJson?.result?.file_path) {
    throw new Error(`Telegram getFile falhou: ${JSON.stringify(getFileJson)}`);
  }

  const filePath = getFileJson.result.file_path;
  const downloadRes = await fetch(`${TELEGRAM_API}/file/bot${token}/${filePath}`);

  if (!downloadRes.ok) {
    throw new Error(`Telegram download falhou: HTTP ${downloadRes.status}`);
  }

  return new Uint8Array(await downloadRes.arrayBuffer());
}

async function uploadStorage(
  objectPath: string,
  bytes: Uint8Array,
  contentType: string,
) {
  const supabaseUrl = env("SUPABASE_URL");
  const serviceKey = env("SUPABASE_SERVICE_ROLE_KEY");

  const encodedPath = objectPath
    .split("/")
    .map((p) => encodeURIComponent(p))
    .join("/");

  const res = await fetch(
    `${supabaseUrl}/storage/v1/object/${BUCKET}/${encodedPath}`,
    {
      method: "POST",
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
        "Content-Type": contentType || "application/octet-stream",
        "x-upsert": "true",
      },
      body: bytes,
    },
  );

  if (!res.ok) {
    throw new Error(`Storage upload falhou: HTTP ${res.status} ${await res.text()}`);
  }
}

function publicUrl(objectPath: string) {
  const supabaseUrl = env("SUPABASE_URL");
  const encoded = objectPath
    .split("/")
    .map((p) => encodeURIComponent(p))
    .join("/");

  return `${supabaseUrl}/storage/v1/object/public/${BUCKET}/${encoded}`;
}

async function wallpaperExists(slug: string) {
  const supabaseUrl = env("SUPABASE_URL");
  const serviceKey = env("SUPABASE_SERVICE_ROLE_KEY");

  const res = await fetch(
    `${supabaseUrl}/rest/v1/wallpapers?select=slug&slug=eq.${encodeURIComponent(slug)}&limit=1`,
    {
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
      },
    },
  );

  if (!res.ok) return false;
  const rows = await res.json();
  return Array.isArray(rows) && rows.length > 0;
}

async function upsertWallpaper(row: WallpaperRecord) {
  const supabaseUrl = env("SUPABASE_URL");
  const serviceKey = env("SUPABASE_SERVICE_ROLE_KEY");

  const res = await fetch(
    `${supabaseUrl}/rest/v1/wallpapers?on_conflict=slug`,
    {
      method: "POST",
      headers: {
        apikey: serviceKey,
        Authorization: `Bearer ${serviceKey}`,
        "Content-Type": "application/json",
        Prefer: "resolution=merge-duplicates,return=representation",
      },
      body: JSON.stringify(row),
    },
  );

  if (!res.ok) {
    throw new Error(`DB upsert falhou: HTTP ${res.status} ${await res.text()}`);
  }

  return await res.json();
}

async function announceWallpaper(record: any, updated = false) {
  const channel = env("TELEGRAM_CHANNEL");
  const name = record.name || "Novo wallpaper";
  const category = record.category || "Novidades";
  const thumb = record.thumbnail_url || record.background_url || "";
  const layered = Boolean(record.foreground_url) || record.depth_mode === "layered";

  const caption =
    `${updated ? "🔄 WALLPAPER ATUALIZADO" : "🆕 NOVO WALLPAPER"}\n\n` +
    `🖼 ${name}\n` +
    `📁 ${category}\n` +
    `${layered ? "✨ Profundidade real / relógio atrás do assunto" : "📱 Wallpaper"}\n\n` +
    `Abra o iDepth 26 e atualize o catálogo.`;

  if (thumb) {
    await telegram("sendPhoto", {
      chat_id: channel,
      photo: thumb,
      caption,
    });
  } else {
    await telegram("sendMessage", {
      chat_id: channel,
      text: caption,
    });
  }
}

async function sendHelp(chatId: string) {
  await sendMessage(
    chatId,
    "iDepth 26 — Publicação automática\n\n" +
    "MODO MAIS FÁCIL — wallpaper simples:\n" +
    "Envie uma imagem como ARQUIVO com legenda:\n" +
    "/add Nome | Categoria\n\n" +
    "MODO DEPTH — tudo automático:\n" +
    "Envie um ZIP com:\n" +
    "lockscreen.jpg\n" +
    "foreground.png\n" +
    "homescreen.jpg (opcional)\n" +
    "thumb.jpg (opcional)\n\n" +
    "Legenda do ZIP:\n" +
    "/add Nome | Categoria\n\n" +
    "O bot sobe no Storage, cria/atualiza a tabela, configura depth e anuncia no canal.",
  );
}

async function sendMessage(chatId: string, text: string) {
  await telegram("sendMessage", { chat_id: chatId, text });
}

async function telegram(method: string, body: any) {
  const token = env("TELEGRAM_BOT_TOKEN");
  const res = await fetch(`${TELEGRAM_API}/bot${token}/${method}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!res.ok) {
    throw new Error(`Telegram ${method} falhou: HTTP ${res.status} ${await res.text()}`);
  }

  return await res.json();
}

function slugify(value: string) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 100) || `wallpaper-${Date.now()}`;
}

function prettyName(value: string) {
  return value
    .replace(/[-_]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\b\w/g, (c) => c.toUpperCase()) || "Wallpaper";
}

function stripExtension(value: string) {
  return value.replace(/\.[^.]+$/, "");
}

function baseName(value: string) {
  return value.split("/").pop() || value;
}

function isImageName(value: string) {
  return /\.(jpe?g|png|webp)$/i.test(value);
}

function imageExtension(fileName: string, mime: string) {
  const m = fileName.match(/\.(jpe?g|png|webp)$/i);
  if (m) return m[1].toLowerCase().replace("jpeg", "jpg");
  if (mime.includes("png")) return "png";
  if (mime.includes("webp")) return "webp";
  return "jpg";
}

function mimeFromExtension(ext: string) {
  if (ext === "png") return "image/png";
  if (ext === "webp") return "image/webp";
  return "image/jpeg";
}
