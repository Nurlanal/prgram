CREATE TABLE IF NOT EXISTS users (
  id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  email           VARCHAR(190) NOT NULL UNIQUE,
  username        VARCHAR(40) NOT NULL UNIQUE,
  password_hash   VARCHAR(255) NOT NULL,
  display_name    VARCHAR(100) NOT NULL,
  bio             TEXT NOT NULL,
  avatar          VARCHAR(100) NULL,
  cover           VARCHAR(100) NULL,
  dob             DATE NOT NULL,
  role            VARCHAR(20) NOT NULL DEFAULT 'fan',
  creator_status  VARCHAR(20) NOT NULL DEFAULT 'none',
  legal_name      VARCHAR(150) NULL,
  sub_price_cents INT NOT NULL DEFAULT 0,
  balance_cents   INT NOT NULL DEFAULT 0,
  banned          TINYINT NOT NULL DEFAULT 0,
  created_at      DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS posts (
  id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  creator_id      INT UNSIGNED NOT NULL,
  body            TEXT NOT NULL,
  visibility      VARCHAR(20) NOT NULL DEFAULT 'subscribers',
  ppv_price_cents INT NOT NULL DEFAULT 0,
  removed         TINYINT NOT NULL DEFAULT 0,
  created_at      DATETIME NOT NULL,
  INDEX idx_posts_creator (creator_id, id),
  FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS media (
  id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  post_id    INT UNSIGNED NOT NULL,
  filename   VARCHAR(100) NOT NULL,
  mime       VARCHAR(50) NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_media_post (post_id),
  FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS subscriptions (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  fan_id      INT UNSIGNED NOT NULL,
  creator_id  INT UNSIGNED NOT NULL,
  price_cents INT NOT NULL,
  auto_renew  TINYINT NOT NULL DEFAULT 1,
  started_at  DATETIME NOT NULL,
  expires_at  DATETIME NOT NULL,
  UNIQUE KEY uq_sub (fan_id, creator_id),
  FOREIGN KEY (fan_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS unlocks (
  fan_id      INT UNSIGNED NOT NULL,
  post_id     INT UNSIGNED NOT NULL,
  price_cents INT NOT NULL,
  created_at  DATETIME NOT NULL,
  PRIMARY KEY (fan_id, post_id),
  FOREIGN KEY (fan_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transactions (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  from_user   INT UNSIGNED NULL,
  to_user     INT UNSIGNED NULL,
  type        VARCHAR(20) NOT NULL,
  gross_cents INT NOT NULL,
  fee_cents   INT NOT NULL DEFAULT 0,
  net_cents   INT NOT NULL,
  ref_id      INT UNSIGNED NULL,
  note        VARCHAR(255) NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'completed',
  created_at  DATETIME NOT NULL,
  INDEX idx_tx_to (to_user, created_at),
  INDEX idx_tx_from (from_user, created_at),
  FOREIGN KEY (from_user) REFERENCES users(id) ON DELETE SET NULL,
  FOREIGN KEY (to_user) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS messages (
  id           INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  sender_id    INT UNSIGNED NOT NULL,
  recipient_id INT UNSIGNED NOT NULL,
  body         TEXT NOT NULL,
  read_at      DATETIME NULL,
  created_at   DATETIME NOT NULL,
  INDEX idx_msg_pair (sender_id, recipient_id, id),
  INDEX idx_msg_recipient (recipient_id, read_at),
  FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS likes (
  user_id INT UNSIGNED NOT NULL,
  post_id INT UNSIGNED NOT NULL,
  PRIMARY KEY (user_id, post_id),
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS comments (
  id         INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  post_id    INT UNSIGNED NOT NULL,
  user_id    INT UNSIGNED NOT NULL,
  body       TEXT NOT NULL,
  created_at DATETIME NOT NULL,
  FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reports (
  id          INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  reporter_id INT UNSIGNED NOT NULL,
  post_id     INT UNSIGNED NULL,
  user_id     INT UNSIGNED NULL,
  reason      TEXT NOT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'open',
  created_at  DATETIME NOT NULL,
  FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rate_limits (
  id       VARCHAR(190) PRIMARY KEY,
  hits     INT NOT NULL,
  reset_at BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
