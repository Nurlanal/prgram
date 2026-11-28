<?php
declare(strict_types=1);

define('ROOT', dirname(__DIR__));

$configFile = ROOT . '/config.php';
if (!is_file($configFile)) {
    $configFile = ROOT . '/config.sample.php';
}
$GLOBALS['config'] = require $configFile;

function config(string $key, $default = null)
{
    $value = $GLOBALS['config'];
    foreach (explode('.', $key) as $part) {
        if (!is_array($value) || !array_key_exists($part, $value)) {
            return $default;
        }
        $value = $value[$part];
    }
    return $value;
}

// ---------------------------------------------------------------- Database

function db(): PDO
{
    static $pdo = null;
    if ($pdo) {
        return $pdo;
    }
    $c = config('db');
    if ($c['driver'] === 'mysql') {
        $m = $c['mysql'];
        $dsn = "mysql:host={$m['host']};port={$m['port']};dbname={$m['name']};charset=utf8mb4";
        $pdo = new PDO($dsn, $m['user'], $m['pass']);
    } else {
        $dir = dirname($c['sqlite_path']);
        if (!is_dir($dir)) {
            mkdir($dir, 0775, true);
        }
        $pdo = new PDO('sqlite:' . $c['sqlite_path']);
        $pdo->exec('PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL; PRAGMA busy_timeout = 5000;');
    }
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
    $pdo->setAttribute(PDO::ATTR_EMULATE_PREPARES, false);
    return $pdo;
}

function q(string $sql, array $params = []): PDOStatement
{
    $st = db()->prepare($sql);
    $st->execute($params);
    return $st;
}

function row(string $sql, array $params = []): ?array
{
    $r = q($sql, $params)->fetch();
    return $r === false ? null : $r;
}

function rows(string $sql, array $params = []): array
{
    return q($sql, $params)->fetchAll();
}

function scalar(string $sql, array $params = [])
{
    $v = q($sql, $params)->fetchColumn();
    return $v === false ? null : $v;
}

/** Runs $fn in a DB transaction, rolling back on any exception. */
function transaction(callable $fn)
{
    $pdo = db();
    $pdo->beginTransaction();
    try {
        $result = $fn();
        $pdo->commit();
        return $result;
    } catch (Throwable $e) {
        $pdo->rollBack();
        throw $e;
    }
}

function now(): string
{
    return gmdate('Y-m-d H:i:s');
}

// ---------------------------------------------------------------- Errors

class HttpError extends RuntimeException
{
    public int $status;

    public function __construct(int $status, string $message)
    {
        parent::__construct($message);
        $this->status = $status;
    }
}

// ---------------------------------------------------------------- Session / auth

function start_session(): void
{
    if (PHP_SAPI === 'cli' || session_status() === PHP_SESSION_ACTIVE) {
        return;
    }
    session_name('fanora_sid');
    session_set_cookie_params([
        'lifetime' => 60 * 60 * 24 * 30,
        'path' => '/',
        'httponly' => true,
        'samesite' => 'Lax',
        'secure' => (bool) config('cookie_secure', false),
    ]);
    ini_set('session.use_strict_mode', '1');
    session_start();
}

function current_user(): ?array
{
    static $loaded = false, $user = null;
    if ($loaded) {
        return $user;
    }
    $loaded = true;
    $id = $_SESSION['uid'] ?? null;
    if ($id) {
        $user = row('SELECT * FROM users WHERE id = ? AND banned = 0', [$id]);
        if (!$user) {
            unset($_SESSION['uid']);
        }
    }
    return $user;
}

function refresh_user(): ?array
{
    $id = $_SESSION['uid'] ?? null;
    return $id ? row('SELECT * FROM users WHERE id = ?', [$id]) : null;
}

function login_user(int $id): void
{
    session_regenerate_id(true);
    $_SESSION['uid'] = $id;
}

function is_creator(?array $u): bool
{
    return $u && $u['role'] === 'creator' && $u['creator_status'] === 'approved';
}

function is_admin(?array $u): bool
{
    return $u && $u['role'] === 'admin';
}

function require_login(): array
{
    $u = current_user();
    if (!$u) {
        flash('error', 'Davam etmək üçün daxil olun.');
        redirect('/login');
    }
    return $u;
}

function require_creator(): array
{
    $u = require_login();
    if (!is_creator($u)) {
        throw new HttpError(403, 'Yalnız təsdiqlənmiş kreatorlar üçün.');
    }
    return $u;
}

function require_admin(): array
{
    $u = require_login();
    if (!is_admin($u)) {
        throw new HttpError(403, 'Yalnız admin.');
    }
    return $u;
}

// ---------------------------------------------------------------- CSRF

function csrf_token(): string
{
    if (empty($_SESSION['csrf'])) {
        $_SESSION['csrf'] = bin2hex(random_bytes(32));
    }
    return $_SESSION['csrf'];
}

function csrf_field(): string
{
    return '<input type="hidden" name="_csrf" value="' . e(csrf_token()) . '">';
}

function verify_csrf(): void
{
    $sent = $_POST['_csrf'] ?? ($_SERVER['HTTP_X_CSRF_TOKEN'] ?? '');
    if (!is_string($sent) || !hash_equals(csrf_token(), $sent)) {
        throw new HttpError(419, 'Sessiya vaxtı bitib. Səhifəni yeniləyib yenidən cəhd edin.');
    }
}

// ---------------------------------------------------------------- Helpers

function e($v): string
{
    return htmlspecialchars((string) $v, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function money(int $cents): string
{
    return '$' . number_format($cents / 100, 2, '.', ',');
}

/** Parses a user-typed amount like "9.99" into integer cents (or null). */
function to_cents($v): ?int
{
    if (!is_string($v) && !is_numeric($v)) {
        return null;
    }
    $v = str_replace(',', '.', trim((string) $v));
    if (!preg_match('/^\d{1,7}(\.\d{1,2})?$/', $v)) {
        return null;
    }
    return (int) round(((float) $v) * 100);
}

function redirect(string $to): never
{
    header('Location: ' . $to, true, 303);
    exit;
}

function back(string $fallback = '/'): never
{
    $ref = $_SERVER['HTTP_REFERER'] ?? '';
    $host = $_SERVER['HTTP_HOST'] ?? '';
    if ($ref && parse_url($ref, PHP_URL_HOST) === parse_url('http://' . $host, PHP_URL_HOST)) {
        $path = parse_url($ref, PHP_URL_PATH) ?: '/';
        $query = parse_url($ref, PHP_URL_QUERY);
        redirect($path . ($query ? '?' . $query : ''));
    }
    redirect($fallback);
}

function flash(string $type, string $message): void
{
    $_SESSION['flash'][] = ['type' => $type, 'message' => $message];
}

function take_flashes(): array
{
    $f = $_SESSION['flash'] ?? [];
    unset($_SESSION['flash']);
    return $f;
}

function old(string $key, string $default = ''): string
{
    return e($_SESSION['old'][$key] ?? $default);
}

function input(string $key, string $default = ''): string
{
    $v = $_POST[$key] ?? $default;
    return is_string($v) ? trim($v) : $default;
}

function avatar_url(?array $u): string
{
    if ($u && !empty($u['avatar'])) {
        return '/pfile/' . rawurlencode($u['avatar']);
    }
    return '/assets/avatar.svg';
}

function cover_url(?array $u): ?string
{
    return ($u && !empty($u['cover'])) ? '/pfile/' . rawurlencode($u['cover']) : null;
}

function time_ago(string $utc): string
{
    $diff = time() - strtotime($utc . ' UTC');
    if ($diff < 60) return 'indicə';
    if ($diff < 3600) return floor($diff / 60) . ' dəq əvvəl';
    if ($diff < 86400) return floor($diff / 3600) . ' saat əvvəl';
    if ($diff < 86400 * 7) return floor($diff / 86400) . ' gün əvvəl';
    return gmdate('d.m.Y', strtotime($utc . ' UTC'));
}

function format_date(string $utc): string
{
    return gmdate('d.m.Y', strtotime($utc . ' UTC'));
}

function nl2br_e(string $text): string
{
    return nl2br(e($text));
}

/** Renders views/$name.php inside the layout. */
function view(string $name, array $data = [], int $status = 200): never
{
    http_response_code($status);
    $data['me'] = current_user();
    extract($data, EXTR_SKIP);
    ob_start();
    require ROOT . "/views/{$name}.php";
    $content = ob_get_clean();
    require ROOT . '/views/layout.php';
    unset($_SESSION['old']);
    exit;
}

/** IP-based rate limit stored in the DB (survives cookie resets). */
function rate_limit(string $key, int $max, int $seconds): void
{
    $id = $key . ':' . ($_SERVER['REMOTE_ADDR'] ?? 'cli');
    $now = time();
    $b = row('SELECT hits, reset_at FROM rate_limits WHERE id = ?', [$id]);
    if (!$b || $now > (int) $b['reset_at']) {
        q('DELETE FROM rate_limits WHERE id = ?', [$id]);
        q('INSERT INTO rate_limits (id, hits, reset_at) VALUES (?, 1, ?)', [$id, $now + $seconds]);
        return;
    }
    q('UPDATE rate_limits SET hits = hits + 1 WHERE id = ?', [$id]);
    if ((int) $b['hits'] + 1 > $max) {
        throw new HttpError(429, 'Çox sayda cəhd. Bir az sonra yenidən yoxlayın.');
    }
}

require __DIR__ . '/services.php';
require __DIR__ . '/upload.php';
