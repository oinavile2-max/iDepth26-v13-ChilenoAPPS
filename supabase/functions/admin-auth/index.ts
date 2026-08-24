function hex(bytes: ArrayBuffer) {
  return [...new Uint8Array(bytes)].map(b => b.toString(16).padStart(2, "0")).join("");
}
async function sha256(value: string) {
  return hex(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value)));
}
function b64url(bytes: Uint8Array) {
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}
function fromB64(value: string) {
  const binary = atob(value);
  return Uint8Array.from(binary, c => c.charCodeAt(0));
}
async function passwordPbkdf2(password: string, saltB64: string) {
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]
  );
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      hash: "SHA-256",
      salt: fromB64(saltB64),
      iterations: 210000,
    },
    key,
    256,
  );
  return b64url(new Uint8Array(bits));
}
async function sign(payload: Record<string, unknown>, secret: string) {
  const header = b64url(new TextEncoder().encode(JSON.stringify({ alg: "HS256", typ: "JWT" })));
  const body = b64url(new TextEncoder().encode(JSON.stringify(payload)));
  const data = `${header}.${body}`;
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(data)));
  return `${data}.${b64url(sig)}`;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return new Response("Method not allowed", { status: 405 });

  try {
    const body = await req.json();
    const login = String(body.login_id || "").replace(/\D/g, "");
    const password = String(body.password || "");

    const expectedLogin = Deno.env.get("ADMIN_LOGIN_SHA256") || "";
    const passwordSalt = Deno.env.get("ADMIN_PASSWORD_SALT") || "";
    const expectedPassword = Deno.env.get("ADMIN_PASSWORD_PBKDF2") || "";
    const sessionSecret = Deno.env.get("ADMIN_SESSION_SECRET") || "";

    if (!expectedLogin || !passwordSalt || !expectedPassword || !sessionSecret) {
      return Response.json({ ok: false, error: "Admin backend não configurado." }, { status: 503 });
    }

    const loginOk = (await sha256(login)) === expectedLogin;
    const passwordOk = (await passwordPbkdf2(password, passwordSalt)) === expectedPassword;

    if (!loginOk || !passwordOk) {
      await new Promise(r => setTimeout(r, 550));
      return Response.json({ ok: false, error: "Credenciais inválidas." }, { status: 401 });
    }

    const now = Math.floor(Date.now() / 1000);
    const token = await sign({ sub: "idepth-admin", iat: now, exp: now + 4 * 60 * 60 }, sessionSecret);

    return Response.json({ ok: true, token, expires_in: 14400 });
  } catch (e) {
    console.error(e);
    return Response.json({ ok: false, error: "Falha no login." }, { status: 500 });
  }
});
