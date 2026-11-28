CREATE TABLE IF NOT EXISTS users (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  email           TEXT NOT NULL UNIQUE COLLATE NOCASE,
  username        TEXT NOT NULL UNIQUE COLLATE NOCASE,
  password_hash   TEXT NOT NULL,
  display_name    TEXT NOT NULL,
  bio             TEXT NOT NULL DEFAULT '',
  avatar          TEXT,
  cover           TEXT,
  dob             TEXT NOT NULL,
  role            TEXT NOT NULL DEFAULT 'fan',
  creator_status  TEXT NOT NULL DEFAULT 'none',
  legal_name      TEXT,
  sub_price_cents INTEGER NOT NULL DEFAULT 0,
  balance_cents   INTEGER NOT NULL DEFAULT 0,
  banned          INTEGER NOT NULL DEFAULT 0,
  created_at      TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS posts (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  creator_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body            TEXT NOT NULL DEFAULT '',
  visibility      TEXT NOT NULL DEFAULT 'subscribers',
  ppv_price_cents INTEGER NOT NULL DEFAULT 0,
  removed         INTEGER NOT NULL DEFAULT 0,
  created_at      TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_posts_creator ON posts (creator_id, id);

CREATE TABLE IF NOT EXISTS media (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  post_id    INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  filename   TEXT NOT NULL,
  mime       TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_media_post ON media (post_id);

CREATE TABLE IF NOT EXISTS subscriptions (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  fan_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  creator_id  INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  price_cents INTEGER NOT NULL,
  auto_renew  INTEGER NOT NULL DEFAULT 1,
  started_at  TEXT NOT NULL,
  expires_at  TEXT NOT NULL,
  UNIQUE (fan_id, creator_id)
);

CREATE TABLE IF NOT EXISTS unlocks (
  fan_id      INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  post_id     INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  price_cents INTEGER NOT NULL,
  created_at  TEXT NOT NULL,
  PRIMARY KEY (fan_id, post_id)
);

CREATE TABLE IF NOT EXISTS transactions (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  from_user   INTEGER REFERENCES users(id) ON DELETE SET NULL,
  to_user     INTEGER REFERENCES users(id) ON DELETE SET NULL,
  type        TEXT NOT NULL,
  gross_cents INTEGER NOT NULL,
  fee_cents   INTEGER NOT NULL DEFAULT 0,
  net_cents   INTEGER NOT NULL,
  ref_id      INTEGER,
  note        TEXT,
  status      TEXT NOT NULL DEFAULT 'completed',
  created_at  TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_tx_to ON transactions (to_user, created_at);
CREATE INDEX IF NOT EXISTS idx_tx_from ON transactions (from_user, created_at);

CREATE TABLE IF NOT EXISTS messages (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  sender_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  recipient_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body         TEXT NOT NULL,
  read_at      TEXT,
  created_at   TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_msg_pair ON messages (sender_id, recipient_id, id);
CREATE INDEX IF NOT EXISTS idx_msg_recipient ON messages (recipient_id, read_at);

CREATE TABLE IF NOT EXISTS likes (
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  post_id INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, post_id)
);

CREATE TABLE IF NOT EXISTS comments (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  post_id    INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body       TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS reports (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  reporter_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  post_id     INTEGER REFERENCES posts(id) ON DELETE CASCADE,
  user_id     INTEGER REFERENCES users(id) ON DELETE CASCADE,
  reason      TEXT NOT NULL,
  status      TEXT NOT NULL DEFAULT 'open',
  created_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS rate_limits (
  id       TEXT PRIMARY KEY,
  hits     INTEGER NOT NULL,
  reset_at INTEGER NOT NULL
);
