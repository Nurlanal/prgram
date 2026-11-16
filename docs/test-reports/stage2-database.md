# Mərhələ 2 — verilənlər bazası testləri

Mühit: PostgreSQL 16 + Supabase shim (`supabase/tests/00_supabase_shim.sql`). Əmr: `supabase/tests/run_local_tests.sh`.

Nəticə: **94 / 94 keçdi**, 0 uğursuz.

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
