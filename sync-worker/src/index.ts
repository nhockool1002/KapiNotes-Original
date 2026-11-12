/**
 * KapiNotes sync Worker.
 *
 * Replaces per-user Google Drive sync: the app now sends its single `.kapibackup` blob here
 * instead of to the Drive API. This Worker is the only thing holding R2 credentials (via the
 * native binding below) - the app itself never sees a bucket key, only a Google ID token it
 * already has from signing in.
 *
 * Each user's backup lives at "users/<google-sub>/kapinotes_sync.kapibackup". The Google
 * "sub" claim (a stable per-account numeric id) is used as the storage key rather than email,
 * since email is allowed to change on a Google account but sub never does.
 */

export interface Env {
  SYNC_BUCKET: R2Bucket;
  ALLOWED_AUDIENCES: string;
}

const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const GOOGLE_ISSUERS = ["https://accounts.google.com", "accounts.google.com"];

interface GoogleJwk {
  kid: string;
  n: string;
  e: string;
  kty: string;
  alg: string;
}

let cachedJwks: { keys: GoogleJwk[]; fetchedAt: number } | null = null;

async function getGoogleJwks(): Promise<GoogleJwk[]> {
  const ONE_HOUR_MS = 60 * 60 * 1000;
  if (cachedJwks && Date.now() - cachedJwks.fetchedAt < ONE_HOUR_MS) {
    return cachedJwks.keys;
  }
  const res = await fetch(GOOGLE_JWKS_URL);
  if (!res.ok) throw new Error(`Failed to fetch Google JWKS: ${res.status}`);
  const body = await res.json<{ keys: GoogleJwk[] }>();
  cachedJwks = { keys: body.keys, fetchedAt: Date.now() };
  return body.keys;
}

function base64UrlToUint8Array(b64url: string): Uint8Array {
  const b64 = b64url.replace(/-/g, "+").replace(/_/g, "/");
  const padded = b64 + "=".repeat((4 - (b64.length % 4)) % 4);
  const raw = atob(padded);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes;
}

function base64UrlDecodeJson<T>(b64url: string): T {
  const bytes = base64UrlToUint8Array(b64url);
  return JSON.parse(new TextDecoder().decode(bytes));
}

interface GoogleIdTokenPayload {
  iss: string;
  aud: string;
  sub: string;
  email?: string;
  email_verified?: boolean;
  exp: number;
  iat: number;
}

/** Verifies a Google-issued ID token's signature, issuer, audience and expiry. Returns its payload. */
async function verifyGoogleIdToken(idToken: string, allowedAudiences: string[]): Promise<GoogleIdTokenPayload> {
  const parts = idToken.split(".");
  if (parts.length !== 3) throw new Error("Malformed ID token");
  const [headerB64, payloadB64, sigB64] = parts;

  const header = base64UrlDecodeJson<{ alg: string; kid: string }>(headerB64);
  const payload = base64UrlDecodeJson<GoogleIdTokenPayload>(payloadB64);

  if (header.alg !== "RS256") throw new Error("Unexpected token algorithm");
  if (!GOOGLE_ISSUERS.includes(payload.iss)) throw new Error("Unexpected token issuer");
  if (!allowedAudiences.includes(payload.aud)) throw new Error("Token audience not allowed");
  if (payload.exp * 1000 < Date.now()) throw new Error("Token expired");

  const jwks = await getGoogleJwks();
  const jwk = jwks.find((k) => k.kid === header.kid);
  if (!jwk) throw new Error("Signing key not found");

  const key = await crypto.subtle.importKey(
    "jwk",
    { kty: jwk.kty, n: jwk.n, e: jwk.e, alg: "RS256", ext: true },
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );

  const signedData = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const signature = base64UrlToUint8Array(sigB64);
  const valid = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, signature, signedData);
  if (!valid) throw new Error("Invalid token signature");

  return payload;
}

async function authenticate(request: Request, env: Env): Promise<GoogleIdTokenPayload> {
  const authHeader = request.headers.get("Authorization") ?? "";
  const match = authHeader.match(/^Bearer (.+)$/);
  if (!match) throw new Error("Missing Authorization: Bearer <google-id-token> header");
  const allowed = env.ALLOWED_AUDIENCES.split(",").map((s) => s.trim()).filter(Boolean);
  return verifyGoogleIdToken(match[1], allowed);
}

function objectKeyFor(sub: string): string {
  return `users/${sub}/kapinotes_sync.kapibackup`;
}

/** Each "Chia sẻ link xem-only" export gets its own permanent object (never overwritten), so
 * sharing a second note doesn't invalidate a link already sent out. Prefixed separately from the
 * per-account sync blobs above so the two can be told apart/managed independently in the bucket. */
function sharedObjectKeyFor(id: string): string {
  return `shared/${id}.pdf`;
}

const MAX_SHARE_BYTES = 50 * 1024 * 1024; // 50 MB - generous for a handwritten-notes PDF export

/** GET/PUT/DELETE one account's single backup blob - unchanged from before. */
async function handleSync(request: Request, env: Env): Promise<Response> {
  let user: GoogleIdTokenPayload;
  try {
    user = await authenticate(request, env);
  } catch (err) {
    return new Response(`Unauthorized: ${(err as Error).message}`, { status: 401 });
  }

  const key = objectKeyFor(user.sub);

  if (request.method === "GET") {
    const object = await env.SYNC_BUCKET.get(key);
    if (!object) return new Response(null, { status: 404 });
    return new Response(object.body, {
      headers: { "Content-Type": "application/octet-stream" },
    });
  }

  if (request.method === "PUT") {
    // Simple per-account cap so one compromised/buggy client can't fill the whole bucket.
    const MAX_BACKUP_BYTES = 200 * 1024 * 1024; // 200 MB
    const contentLength = Number(request.headers.get("Content-Length") ?? "0");
    if (contentLength > MAX_BACKUP_BYTES) {
      return new Response("Backup too large", { status: 413 });
    }
    await env.SYNC_BUCKET.put(key, request.body);
    return new Response(null, { status: 204 });
  }

  if (request.method === "DELETE") {
    await env.SYNC_BUCKET.delete(key);
    return new Response(null, { status: 204 });
  }

  return new Response("Method not allowed", { status: 405 });
}

/** POST a PDF export (authenticated - any signed-in account) and get back a permanent, random,
 * publicly-readable object id. Backs the Editor's "Chia sẻ dạng link xem-only" feature. */
async function handleShareUpload(request: Request, env: Env): Promise<Response> {
  if (request.method !== "POST") {
    return new Response("Method not allowed", { status: 405 });
  }
  try {
    await authenticate(request, env);
  } catch (err) {
    return new Response(`Unauthorized: ${(err as Error).message}`, { status: 401 });
  }

  const contentLength = Number(request.headers.get("Content-Length") ?? "0");
  if (contentLength > MAX_SHARE_BYTES) {
    return new Response("File too large", { status: 413 });
  }

  const id = crypto.randomUUID();
  await env.SYNC_BUCKET.put(sharedObjectKeyFor(id), request.body, {
    httpMetadata: { contentType: "application/pdf" },
  });
  return new Response(JSON.stringify({ id }), {
    status: 201,
    headers: { "Content-Type": "application/json" },
  });
}

/** GET a previously-shared PDF by id - deliberately UNAUTHENTICATED, same as a real "anyone with
 * the link can view" share: the random, unguessable UUID in the URL path is what gates access,
 * not a signed-in session (the recipient of a shared link is never expected to have signed in). */
async function handleSharedGet(id: string, env: Env): Promise<Response> {
  const object = await env.SYNC_BUCKET.get(sharedObjectKeyFor(id));
  if (!object) return new Response("Not found", { status: 404 });
  return new Response(object.body, {
    headers: {
      "Content-Type": "application/pdf",
      "Content-Disposition": "inline",
      "Cache-Control": "public, max-age=31536000, immutable",
    },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/sync") {
      return handleSync(request, env);
    }
    if (url.pathname === "/share") {
      return handleShareUpload(request, env);
    }
    const sharedMatch = url.pathname.match(/^\/shared\/([a-zA-Z0-9-]+)$/);
    if (sharedMatch && request.method === "GET") {
      return handleSharedGet(sharedMatch[1], env);
    }

    return new Response("Not found", { status: 404 });
  },
} satisfies ExportedHandler<Env>;
