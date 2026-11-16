// Supabase Edge Function: POST /functions/v1/admin-create-courier
// Deploy: supabase functions deploy admin-create-courier
// SUPABASE_URL, SUPABASE_ANON_KEY and SUPABASE_SERVICE_ROLE_KEY are provided by the platform
// as server-side secrets; the service role key never leaves the server.
import { createClient } from "npm:@supabase/supabase-js@2.45.4";
import { handleCreateCourier } from "./handler.ts";

const url = Deno.env.get("SUPABASE_URL")!;
const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

Deno.serve((req) => {
  const authorization = req.headers.get("Authorization") ?? "";
  const asCaller = createClient(url, anonKey, {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const asService = createClient(url, serviceKey, { auth: { persistSession: false, autoRefreshToken: false } });

  return handleCreateCourier(req, {
    callerRpc: async (fn, args) => {
      const { data, error } = await asCaller.rpc(fn, args ?? {});
      return { data, error: error ? { message: error.message } : null };
    },
    createAuthUser: async ({ phone, password, firstName, lastName }) => {
      const { data, error } = await asService.auth.admin.createUser({
        phone,
        password,
        phone_confirm: true,
        user_metadata: { first_name: firstName, last_name: lastName },
      });
      return { userId: data?.user?.id ?? null, error: error ? { message: error.message, status: error.status } : null };
    },
    deleteAuthUser: async (userId) => {
      await asService.auth.admin.deleteUser(userId);
    },
  });
});
