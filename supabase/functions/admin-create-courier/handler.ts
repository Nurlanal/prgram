// Pure request handler for "admin creates a courier account".
// No imports: dependencies are injected so the logic can be unit-tested outside Deno.
//
// Security model:
//   * The caller's JWT is used to ask the DATABASE whether the caller is an admin with MFA
//     (public.is_admin() requires role = admin AND aal2). The token's claims are never trusted directly.
//   * Only after that check is the service-role client (server secret, never in the APK) used
//     to create the auth user with a confirmed phone and the admin-chosen password.
//   * The role change itself runs as the caller (public.admin_make_courier re-checks is_admin()),
//     so it is audited with the admin as actor. On failure the new auth user is deleted again.
//   * Passwords are never logged or returned.

export interface CourierInput {
  first_name: string;
  last_name: string;
  phone: string;
  password: string;
  vehicle_info?: string | null;
}

export interface Deps {
  /** Calls a Postgres function as the CALLER (their JWT). */
  callerRpc(fn: string, args?: Record<string, unknown>): Promise<{ data: unknown; error: { message: string } | null }>;
  /** Service-role admin API. */
  createAuthUser(input: { phone: string; password: string; firstName: string; lastName: string }):
    Promise<{ userId: string | null; error: { message: string; status?: number } | null }>;
  deleteAuthUser(userId: string): Promise<void>;
}

const AZ_MOBILE = /^\+994(10|50|51|55|60|70|77|99)\d{7}$/;

export function validate(body: unknown): { ok: true; value: CourierInput } | { ok: false; error: string } {
  if (typeof body !== "object" || body === null) return { ok: false, error: "invalid_body" };
  const b = body as Record<string, unknown>;
  const first = typeof b.first_name === "string" ? b.first_name.trim() : "";
  const last = typeof b.last_name === "string" ? b.last_name.trim() : "";
  const phone = typeof b.phone === "string" ? b.phone.replace(/[\s()-]/g, "") : "";
  const password = typeof b.password === "string" ? b.password : "";
  const vehicle = typeof b.vehicle_info === "string" ? b.vehicle_info.trim().slice(0, 120) : null;

  if (first.length < 1 || first.length > 80) return { ok: false, error: "invalid_first_name" };
  if (last.length < 1 || last.length > 80) return { ok: false, error: "invalid_last_name" };
  if (!AZ_MOBILE.test(phone)) return { ok: false, error: "invalid_phone" };
  if (password.length < 8 || password.length > 72 || !/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
    return { ok: false, error: "weak_password" };
  }
  return { ok: true, value: { first_name: first, last_name: last, phone, password, vehicle_info: vehicle } };
}

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

export async function handleCreateCourier(req: Request, deps: Deps): Promise<Response> {
  if (req.method !== "POST") return json(405, { error: "method_not_allowed" });
  if (!(req.headers.get("Authorization") ?? "").startsWith("Bearer ")) {
    return json(401, { error: "not_authenticated" });
  }

  // 1. Is the caller an admin with MFA? Asked to the database, as the caller.
  const admin = await deps.callerRpc("is_admin");
  if (admin.error || admin.data !== true) return json(403, { error: "admin_mfa_required" });

  // 2. Validate input.
  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return json(400, { error: "invalid_body" });
  }
  const parsed = validate(body);
  if (!parsed.ok) return json(400, { error: parsed.error });
  const input = parsed.value;

  // 3. Create the auth user (service role) with a confirmed phone.
  const created = await deps.createAuthUser({
    phone: input.phone,
    password: input.password,
    firstName: input.first_name,
    lastName: input.last_name,
  });
  if (created.error || !created.userId) {
    const msg = created.error?.message ?? "";
    if (created.error?.status === 422 || /already/i.test(msg)) return json(409, { error: "phone_already_registered" });
    return json(502, { error: "auth_user_creation_failed" });
  }

  // 4. Grant the courier role as the caller (re-checked + audited in the database).
  const promoted = await deps.callerRpc("admin_make_courier", {
    p_user_id: created.userId,
    p_vehicle_info: input.vehicle_info ?? null,
  });
  if (promoted.error) {
    await deps.deleteAuthUser(created.userId); // never leave a half-created account behind
    return json(400, { error: "courier_role_failed" });
  }
  return json(201, { user_id: created.userId });
}
