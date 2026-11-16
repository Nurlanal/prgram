// Run: node --experimental-strip-types --test supabase/functions/_tests/
import { test } from "node:test";
import assert from "node:assert/strict";
import { handleCreateCourier, validate, type Deps } from "../admin-create-courier/handler.ts";

const goodBody = { first_name: "Orxan", last_name: "Məmmədov", phone: "+994 70 765 43 21", password: "Kuryer2026", vehicle_info: "Moped" };

function request(body: unknown, auth = "Bearer caller.jwt", method = "POST"): Request {
  return new Request("https://x.supabase.co/functions/v1/admin-create-courier", {
    method,
    headers: { "Content-Type": "application/json", ...(auth ? { Authorization: auth } : {}) },
    body: method === "POST" ? JSON.stringify(body) : undefined,
  });
}

function fakeDeps(opts: { isAdmin?: boolean; createError?: { message: string; status?: number }; promoteError?: boolean } = {}) {
  const calls: string[] = [];
  const deps: Deps = {
    callerRpc: async (fn, args) => {
      calls.push(`rpc:${fn}:${JSON.stringify(args ?? {})}`);
      if (fn === "is_admin") return { data: opts.isAdmin ?? true, error: null };
      if (fn === "admin_make_courier") return { data: null, error: opts.promoteError ? { message: "denied" } : null };
      return { data: null, error: { message: "unknown" } };
    },
    createAuthUser: async (input) => {
      calls.push(`create:${input.phone}:${input.firstName}`);
      return opts.createError ? { userId: null, error: opts.createError } : { userId: "new-user-id", error: null };
    },
    deleteAuthUser: async (id) => { calls.push(`delete:${id}`); },
  };
  return { deps, calls };
}

test("admin with MFA creates a courier with a normalised phone", async () => {
  const { deps, calls } = fakeDeps();
  const res = await handleCreateCourier(request(goodBody), deps);
  assert.equal(res.status, 201);
  assert.deepEqual(await res.json(), { user_id: "new-user-id" });
  assert.deepEqual(calls, [
    "rpc:is_admin:{}",
    "create:+994707654321:Orxan",
    'rpc:admin_make_courier:{"p_user_id":"new-user-id","p_vehicle_info":"Moped"}',
  ]);
});

test("non-admin (or admin without MFA) is rejected before any user is created", async () => {
  const { deps, calls } = fakeDeps({ isAdmin: false });
  const res = await handleCreateCourier(request(goodBody), deps);
  assert.equal(res.status, 403);
  assert.deepEqual(calls, ["rpc:is_admin:{}"]);
});

test("missing Authorization header is rejected", async () => {
  const { deps, calls } = fakeDeps();
  const res = await handleCreateCourier(request(goodBody, ""), deps);
  assert.equal(res.status, 401);
  assert.equal(calls.length, 0);
});

test("only POST is allowed", async () => {
  const { deps } = fakeDeps();
  assert.equal((await handleCreateCourier(request(null, "Bearer x", "GET"), deps)).status, 405);
});

test("phone already registered → 409", async () => {
  const { deps } = fakeDeps({ createError: { message: "Phone number already registered by another user", status: 422 } });
  const res = await handleCreateCourier(request(goodBody), deps);
  assert.equal(res.status, 409);
  assert.deepEqual(await res.json(), { error: "phone_already_registered" });
});

test("if the role cannot be granted, the new auth user is deleted again", async () => {
  const { deps, calls } = fakeDeps({ promoteError: true });
  const res = await handleCreateCourier(request(goodBody), deps);
  assert.equal(res.status, 400);
  assert.equal(calls.at(-1), "delete:new-user-id");
});

test("the password never appears in the response", async () => {
  const { deps } = fakeDeps();
  const res = await handleCreateCourier(request(goodBody), deps);
  assert.ok(!(await res.text()).includes(goodBody.password));
});

test("validation rules", () => {
  assert.equal(validate(goodBody).ok, true);
  const bad = (patch: Record<string, unknown>) => validate({ ...goodBody, ...patch });
  assert.deepEqual(bad({ phone: "+7 900 123 45 67" }), { ok: false, error: "invalid_phone" });
  assert.deepEqual(bad({ phone: "+994123456789" }), { ok: false, error: "invalid_phone" });
  assert.deepEqual(bad({ password: "short1" }), { ok: false, error: "weak_password" });
  assert.deepEqual(bad({ password: "onlyletters" }), { ok: false, error: "weak_password" });
  assert.deepEqual(bad({ password: "12345678" }), { ok: false, error: "weak_password" });
  assert.deepEqual(bad({ first_name: "  " }), { ok: false, error: "invalid_first_name" });
  assert.deepEqual(validate(null), { ok: false, error: "invalid_body" });
});
