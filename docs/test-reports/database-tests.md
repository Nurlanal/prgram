# Verilənlər bazası testləri

Mühit: PostgreSQL 16 + Supabase shim. Əmr: `supabase/tests/run_local_tests.sh`.

Nəticə: **190 / 190 keçdi**, 0 uğursuz (Mərhələ 4 daxil).

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
| 108 | security: only the reviewed API functions are callable (signed-in-only ones closed to anon) |
| 109 | security: catalogue / home / address functions run with the caller's rights (RLS applies) |
| 110 | security: even the service_role key cannot call the admin bootstrap |
| 111 | admin without MFA: role is known (app asks for the code) but no admin rights |
| 112 | admin without MFA: cannot list customers |
| 113 | admin without MFA: cannot read the audit log |
| 114 | admin without MFA: cannot read purchase prices |
| 115 | admin without MFA: cannot change the catalogue |
| 116 | admin without MFA: cannot upload files |
| 117 | admin without MFA: settings update has no effect |
| 118 | customer with forged admin claims + MFA is still a customer |
| 119 | forged claims: still sees only own profile |
| 120 | unknown user id gets no role |
| 121 | unknown user id sees no personal data |
| 122 | request without user id sees no roles |
| 123 | changing auth user metadata cannot change the role |
| 124 | courier with MFA is not an admin |
| 125 | courier: cannot read the audit log |
| 126 | courier: cannot read purchase prices |
| 127 | courier: sees only own role |
| 128 | courier: cannot create products |
| 129 | phone comes from verified auth phone, not from metadata |
| 130 | email sign-up cannot set an unverified phone number |
| 131 | the real owner of the phone can still register |
| 132 | blocked customer: can still see own order history |
| 133 | blocked customer: cannot add to cart |
| 134 | blocked customer: cannot add addresses |
| 135 | blocked customer: cannot register for push |
| 136 | blocked customer: profile edit has no effect |
| 137 | registration: names are trimmed |
| 138 | registration: address saved atomically as default delivery address |
| 139 | registration: too-short address is ignored, account still created |
| 140 | get_my_account: returns own role and status |
| 141 | get_my_account: courier role |
| 142 | get_my_account: admin role visible before MFA (for routing) |
| 143 | get_my_account: nothing without a signed-in user |
| 144 | get_my_account: not callable by anon |
| 145 | courier creation: customer cannot create couriers |
| 146 | courier creation: courier cannot create couriers |
| 147 | courier creation: admin without MFA cannot create couriers |
| 148 | courier creation: admin with MFA creates courier, creator recorded |
| 149 | courier creation: courier has no customer cart / bonus card |
| 150 | courier creation: audited with the admin as actor |
| 151 | courier creation: cannot re-promote an existing courier |
| 152 | courier creation: a customer with orders cannot become a courier |
| 153 | courier creation: an admin cannot be turned into a courier |
| 154 | blocking: customers cannot block accounts |
| 155 | blocking: admin cannot block themselves |
| 156 | blocking: blocked courier sees blocked status and loses courier rights |
| 157 | blocking: audited |
| 158 | pricing: category percent promotion applies (2.00 → 1.60) |
| 159 | pricing: the lowest of category promo (4.00) and product fixed price (3.49) wins |
| 160 | pricing: expired and future promotions are ignored |
| 161 | pricing: product sale price before its window opens is ignored |
| 162 | pricing: open-ended product sale price applies |
| 163 | pricing: deactivating a promotion restores the regular price immediately |
| 164 | catalogue: all active products, inactive hidden |
| 165 | catalogue: inactive product never listed |
| 166 | catalogue: discount percent computed on the server |
| 167 | search: "seker" finds "Şəkər" (Azerbaijani letters folded) |
| 168 | search: case-insensitive, "CAY" finds "Çay" |
| 169 | search: exact barcode match |
| 170 | search: matches the description too |
| 171 | catalogue: filter by category |
| 172 | catalogue: discounted filter returns only discounted products |
| 173 | catalogue: price sort uses the final (discounted) price |
| 174 | catalogue: out-of-stock flag |
| 175 | catalogue: popular sort uses units sold (aggregate only) |
| 176 | catalogue: limit is capped server-side |
| 177 | popularity helper returns only product_id and units (no customer or order data) |
| 178 | home: only currently active banners |
| 179 | home: active top-level categories |
| 180 | home: biggest discount first |
| 181 | home: best sellers only include products that sold |
| 182 | home: anonymous "for you" falls back to featured products |
| 183 | home: no bonus data for anonymous visitors |
| 184 | home: signed-in customer sees own bonus card |
| 185 | home: "for you" is based on the customer's own purchases (milk → dairy) |
| 186 | home: each customer sees only their own bonus card |
| 187 | address: exactly one default address |
| 188 | address: customer default updated |
| 189 | address: cannot select another customer's address |
| 190 | address: anon cannot call set_default_address |
