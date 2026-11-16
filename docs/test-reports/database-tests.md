# Verilənlər bazası testləri

Mühit: PostgreSQL 16 + Supabase shim (`supabase/tests/00_supabase_shim.sql`). Əmr: `supabase/tests/run_local_tests.sh`.

Nəticə: **156 / 156 keçdi**, 0 uğursuz (Mərhələ 3 daxil).

| # | Yoxlama |
|---|---|
| 1 | signup creates a profile for every auth user |
| 2 | every new user starts as customer (metadata role ignored) |
| 3 | signup creates a cart |
| 4 | signup creates a bonus card |
| 5 | phone normalised to E.164 with + |
| 6 | bonus card numbers are 16 digits with a valid Luhn check digit |
| 7 | bonus QR tokens are unique |
| 8 | seed: 12 initial categories |
| 9 | seed: single app_settings row |
| 10 | app_settings is a singleton |
| 11 | bootstrap_first_admin grants admin |
| 12 | bootstrap refuses when an admin already exists |
| 13 | promoted staff accounts lose customer-only data (cart, bonus card) |
| 14 | staff accounts are no longer customers |
| 15 | the last admin can never be removed |
| 16 | unique order number format MK-YYMMDD-NNNNNN |
| 17 | order status history recorded on insert |
| 18 | every product automatically gets a stock row |
| 19 | stock can never be negative |
| 20 | bonus balance can never be negative |
| 21 | order total must equal subtotal - discount - bonus + fee |
| 22 | sale price must be below regular price |
| 23 | retrying an order with the same idempotency key cannot create a duplicate |
| 24 | pickup orders cannot have a courier |
| 25 | bonus cannot be earned twice for the same order |
| 26 | a payment cannot be marked paid without confirmation |
| 27 | audit log records catalogue changes |
| 28 | orders are published to Realtime |
| 29 | anon: can browse categories |
| 30 | anon: sees only active products |
| 31 | anon: can read store settings |
| 32 | anon: can read public product images |
| 33 | anon: cannot read profiles |
| 34 | anon: cannot read orders |
| 35 | anon: cannot read purchase prices |
| 36 | anon: cannot create categories |
| 37 | anon: cannot call admin bootstrap |
| 38 | anon: cannot call internal functions |
| 39 | customer: sees only own profile |
| 40 | customer: sees only own role |
| 41 | customer: sees own order |
| 42 | customer: sees own order items |
| 43 | customer: sees only own bonus card |
| 44 | customer: sees own bonus history |
| 45 | customer: sees own + broadcast notifications |
| 46 | customer: cannot read the audit log |
| 47 | customer: inactive products hidden |
| 48 | customer: can edit own name |
| 49 | customer: cannot change own phone directly |
| 50 | customer: cannot change account status |
| 51 | customer: cannot change own role |
| 52 | customer: cannot insert a role |
| 53 | customer: cannot change bonus balance |
| 54 | customer: cannot create bonus transactions |
| 55 | customer: cannot modify order prices |
| 56 | customer: cannot insert orders directly (server function only) |
| 57 | customer: cannot mark an order paid |
| 58 | customer: cannot change stock |
| 59 | customer: price update attempt has no effect |
| 60 | customer: cannot read purchase prices |
| 61 | customer: cannot upload product images |
| 62 | customer: can add to own cart |
| 63 | customer: cannot create an address for someone else |
| 64 | customer: cannot write another customer's favorites |
| 65 | customer: can mark own notification read |
| 66 | other customer: cannot see A's order |
| 67 | other customer: cannot see A's order items |
| 68 | other customer: cannot see A's cart |
| 69 | other customer: cannot see A's addresses |
| 70 | other customer: cannot see A's favorites |
| 71 | other customer: cannot see A's bonus history |
| 72 | other customer: cannot see A's profile |
| 73 | other customer: sees broadcast only, not A's personal notification |
| 74 | other customer: cannot add items to A's cart |
| 75 | other customer: update of A's profile had no effect |
| 76 | courier: sees the order assigned to them |
| 77 | courier: sees items of assigned order |
| 78 | courier: cannot browse customer profiles |
| 79 | courier: cannot see bonus cards |
| 80 | courier: does not receive customer broadcasts |
| 81 | courier: status changes only via server function |
| 82 | courier: can toggle own availability |
| 83 | courier: unassigned orders are invisible |
| 84 | admin: sees all profiles |
| 85 | admin: sees all orders |
| 86 | admin: sees inactive products too |
| 87 | admin: can read purchase prices |
| 88 | admin: can read the audit log |
| 89 | admin: can change bonus rate |
| 90 | admin: stock quantity changes only via audited stock function |
| 91 | admin: role changes only via server function (no direct table writes) |
| 92 | admin: invalid brand color rejected |
| 93 | audit log records who changed a price |
| 94 | deactivated admin: loses admin access |
| 95 | security: no password / secret / hash column in any public table |
| 96 | security: profiles contains only non-secret identity fields |
| 97 | security: Supabase Auth keeps only a bcrypt hash (auth.users) |
| 98 | security: password hashes never copied into the audit log |
| 99 | security: API users cannot read auth.users / password hashes |
| 100 | security: anon cannot read auth.users |
| 101 | security: RLS enabled on every public table |
| 102 | security: every public table has explicit policies |
| 103 | security: no views / materialized views that could bypass RLS |
| 104 | security: anon has read-only access to catalogue tables only |
| 105 | security: no client write privilege on orders / bonus / roles / payments / audit |
| 106 | security: stock quantity and profile identity/status columns not client-writable |
| 107 | security: every SECURITY DEFINER function pins search_path |
| 108 | security: only role helpers (+ signed-in account / admin RPCs) are callable through the API |
| 109 | security: even the service_role key cannot call the admin bootstrap |
| 110 | admin without MFA: role is known (app asks for the code) but no admin rights |
| 111 | admin without MFA: cannot list customers |
| 112 | admin without MFA: cannot read the audit log |
| 113 | admin without MFA: cannot read purchase prices |
| 114 | admin without MFA: cannot change the catalogue |
| 115 | admin without MFA: cannot upload files |
| 116 | admin without MFA: settings update has no effect |
| 117 | customer with forged admin claims + MFA is still a customer |
| 118 | forged claims: still sees only own profile |
| 119 | unknown user id gets no role |
| 120 | unknown user id sees no personal data |
| 121 | request without user id sees no roles |
| 122 | changing auth user metadata cannot change the role |
| 123 | courier with MFA is not an admin |
| 124 | courier: cannot read the audit log |
| 125 | courier: cannot read purchase prices |
| 126 | courier: sees only own role |
| 127 | courier: cannot create products |
| 128 | phone comes from verified auth phone, not from metadata |
| 129 | email sign-up cannot set an unverified phone number |
| 130 | the real owner of the phone can still register |
| 131 | blocked customer: can still see own order history |
| 132 | blocked customer: cannot add to cart |
| 133 | blocked customer: cannot add addresses |
| 134 | blocked customer: cannot register for push |
| 135 | blocked customer: profile edit has no effect |
| 136 | registration: names are trimmed |
| 137 | registration: address saved atomically as default delivery address |
| 138 | registration: too-short address is ignored, account still created |
| 139 | get_my_account: returns own role and status |
| 140 | get_my_account: courier role |
| 141 | get_my_account: admin role visible before MFA (for routing) |
| 142 | get_my_account: nothing without a signed-in user |
| 143 | get_my_account: not callable by anon |
| 144 | courier creation: customer cannot create couriers |
| 145 | courier creation: courier cannot create couriers |
| 146 | courier creation: admin without MFA cannot create couriers |
| 147 | courier creation: admin with MFA creates courier, creator recorded |
| 148 | courier creation: courier has no customer cart / bonus card |
| 149 | courier creation: audited with the admin as actor |
| 150 | courier creation: cannot re-promote an existing courier |
| 151 | courier creation: a customer with orders cannot become a courier |
| 152 | courier creation: an admin cannot be turned into a courier |
| 153 | blocking: customers cannot block accounts |
| 154 | blocking: admin cannot block themselves |
| 155 | blocking: blocked courier sees blocked status and loses courier rights |
| 156 | blocking: audited |
