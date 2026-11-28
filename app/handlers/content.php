<?php
declare(strict_types=1);

const PAGE_SIZE = 20;

function before_id(): int
{
    $b = (int) ($_GET['before'] ?? 0);
    return $b > 0 ? $b : PHP_INT_MAX;
}

function page_home(): void
{
    $me = current_user();
    if (!$me) {
        $featured = rows("SELECT * FROM users WHERE role = 'creator' AND creator_status = 'approved' AND banned = 0 ORDER BY id DESC LIMIT 8");
        view('landing', ['title' => 'Xoş gəldiniz', 'featured' => $featured]);
    }
    $posts = rows(
        'SELECT p.* FROM posts p
         WHERE p.removed = 0 AND p.id < ? AND (
           p.creator_id = ? OR p.creator_id IN (SELECT creator_id FROM subscriptions WHERE fan_id = ? AND expires_at > ?))
         ORDER BY p.id DESC LIMIT ' . PAGE_SIZE,
        [before_id(), $me['id'], $me['id'], now()]
    );
    $suggested = rows(
        "SELECT * FROM users WHERE role = 'creator' AND creator_status = 'approved' AND banned = 0 AND id <> ?
         AND id NOT IN (SELECT creator_id FROM subscriptions WHERE fan_id = ? AND expires_at > ?)
         ORDER BY id DESC LIMIT 5",
        [$me['id'], $me['id'], now()]
    );
    view('feed', [
        'title' => 'Lent',
        'cards' => array_map(fn ($p) => present_post($p, $me), $posts),
        'suggested' => $suggested,
        'next' => count($posts) === PAGE_SIZE ? end($posts)['id'] : null,
    ]);
}

function page_explore(): void
{
    $term = trim((string) ($_GET['q'] ?? ''));
    $like = '%' . str_replace(['%', '_'], ['\%', '\_'], $term) . '%';
    $creators = rows(
        "SELECT u.*,
            (SELECT COUNT(*) FROM posts p WHERE p.creator_id = u.id AND p.removed = 0) AS post_count,
            (SELECT COUNT(*) FROM subscriptions s WHERE s.creator_id = u.id AND s.expires_at > ?) AS sub_count
         FROM users u
         WHERE u.role = 'creator' AND u.creator_status = 'approved' AND u.banned = 0
           AND (u.username LIKE ? ESCAPE '\\' OR u.display_name LIKE ? ESCAPE '\\')
         ORDER BY sub_count DESC, u.id DESC LIMIT 60",
        [now(), $like, $like]
    );
    view('explore', ['title' => 'Kəşf et', 'creators' => $creators, 'term' => $term]);
}

function page_profile(string $username): void
{
    $me = current_user();
    $u = row('SELECT * FROM users WHERE LOWER(username) = LOWER(?) AND banned = 0', [$username]);
    if (!$u) {
        throw new HttpError(404, 'İstifadəçi tapılmadı.');
    }
    $tab = ($_GET['tab'] ?? '') === 'media' ? 'media' : 'posts';
    $sql = 'SELECT * FROM posts WHERE creator_id = ? AND removed = 0 AND id < ?'
        . ($tab === 'media' ? ' AND id IN (SELECT post_id FROM media)' : '')
        . ' ORDER BY id DESC LIMIT ' . PAGE_SIZE;
    $posts = rows($sql, [$u['id'], before_id()]);
    $sub = $me ? row('SELECT * FROM subscriptions WHERE fan_id = ? AND creator_id = ?', [$me['id'], $u['id']]) : null;
    view('profile', [
        'title' => $u['display_name'],
        'user' => $u,
        'tab' => $tab,
        'sub' => $sub,
        'active' => $sub && $sub['expires_at'] > now(),
        'counts' => [
            'posts' => (int) scalar('SELECT COUNT(*) FROM posts WHERE creator_id = ? AND removed = 0', [$u['id']]),
            'photos' => (int) scalar("SELECT COUNT(*) FROM media m JOIN posts p ON p.id = m.post_id WHERE p.creator_id = ? AND p.removed = 0 AND m.mime LIKE 'image/%'", [$u['id']]),
            'videos' => (int) scalar("SELECT COUNT(*) FROM media m JOIN posts p ON p.id = m.post_id WHERE p.creator_id = ? AND p.removed = 0 AND m.mime LIKE 'video/%'", [$u['id']]),
            'likes' => (int) scalar('SELECT COUNT(*) FROM likes l JOIN posts p ON p.id = l.post_id WHERE p.creator_id = ?', [$u['id']]),
        ],
        'cards' => array_map(fn ($p) => present_post($p, $me), $posts),
        'next' => count($posts) === PAGE_SIZE ? end($posts)['id'] : null,
        'can_dm' => $me && can_message($me, $u),
    ]);
}

function find_post(int $id): array
{
    $post = row('SELECT * FROM posts WHERE id = ? AND removed = 0', [$id]);
    if (!$post) {
        throw new HttpError(404, 'Post tapılmadı.');
    }
    return $post;
}

function page_post(string $id): void
{
    $me = current_user();
    $post = find_post((int) $id);
    $card = present_post($post, $me);
    $comments = $card['can_view'] ? rows(
        'SELECT c.*, u.username, u.display_name, u.avatar FROM comments c JOIN users u ON u.id = c.user_id
         WHERE c.post_id = ? ORDER BY c.id',
        [$post['id']]
    ) : [];
    view('post', ['title' => 'Post', 'card' => $card, 'comments' => $comments]);
}

function do_create_post(): void
{
    $me = require_creator();
    $body = mb_substr(input('body'), 0, 5000);
    $visibility = input('visibility') === 'public' ? 'public' : 'subscribers';
    $ppvRaw = input('ppv_price');
    $ppv = $ppvRaw === '' ? 0 : to_cents($ppvRaw);
    $files = uploaded_files('media');

    if ($ppv === null || ($ppv > 0 && ($ppv < 100 || $ppv > 50000))) {
        throw new HttpError(400, 'PPV qiyməti $1.00 – $500.00 arası olmalıdır (və ya boş).');
    }
    if (count($files) > 10) {
        throw new HttpError(400, 'Ən çox 10 fayl yükləmək olar.');
    }
    if ($body === '' && !$files) {
        throw new HttpError(400, 'Post boşdur.');
    }
    if ($ppv > 0 && !$files) {
        throw new HttpError(400, 'Pullu (PPV) postda ən azı bir media olmalıdır.');
    }

    $stored = [];
    try {
        foreach ($files as $f) {
            $stored[] = store_upload($f, true);
        }
        transaction(function () use ($me, $body, $visibility, $ppv, $stored) {
            q(
                'INSERT INTO posts (creator_id, body, visibility, ppv_price_cents, removed, created_at) VALUES (?, ?, ?, ?, 0, ?)',
                [$me['id'], $body, $visibility, $ppv, now()]
            );
            $postId = (int) db()->lastInsertId();
            foreach ($stored as $s) {
                q('INSERT INTO media (post_id, filename, mime, created_at) VALUES (?, ?, ?, ?)', [$postId, $s['filename'], $s['mime'], now()]);
            }
        });
    } catch (Throwable $e) {
        foreach ($stored as $s) {
            delete_upload($s['filename']);
        }
        throw $e;
    }
    flash('success', 'Post dərc edildi.');
    back('/');
}

function do_delete_post(string $id): void
{
    $me = require_login();
    $post = row('SELECT * FROM posts WHERE id = ?', [(int) $id]);
    if (!$post || ((int) $post['creator_id'] !== (int) $me['id'] && !is_admin($me))) {
        throw new HttpError(404, 'Post tapılmadı.');
    }
    // Paid posts stay in the DB (soft delete) so purchase history is intact.
    if (scalar('SELECT 1 FROM unlocks WHERE post_id = ?', [$post['id']])) {
        q('UPDATE posts SET removed = 1 WHERE id = ?', [$post['id']]);
    } else {
        $files = rows('SELECT filename FROM media WHERE post_id = ?', [$post['id']]);
        q('DELETE FROM posts WHERE id = ?', [$post['id']]);
        foreach ($files as $f) {
            delete_upload($f['filename']);
        }
    }
    flash('success', 'Post silindi.');
    redirect('/u/' . rawurlencode(row('SELECT username FROM users WHERE id = ?', [$post['creator_id']])['username']));
}

function do_like(string $id): void
{
    $me = require_login();
    $post = find_post((int) $id);
    if (!can_view_post($post, $me)) {
        throw new HttpError(403, 'Bu posta girişiniz yoxdur.');
    }
    $removed = q('DELETE FROM likes WHERE user_id = ? AND post_id = ?', [$me['id'], $post['id']])->rowCount();
    if (!$removed) {
        q('INSERT INTO likes (user_id, post_id) VALUES (?, ?)', [$me['id'], $post['id']]);
    }
    if (($_SERVER['HTTP_ACCEPT'] ?? '') === 'application/json') {
        header('Content-Type: application/json');
        echo json_encode([
            'liked' => !$removed,
            'likes' => (int) scalar('SELECT COUNT(*) FROM likes WHERE post_id = ?', [$post['id']]),
        ]);
        exit;
    }
    back('/post/' . $post['id']);
}

function do_comment(string $id): void
{
    $me = require_login();
    $post = find_post((int) $id);
    if (!can_view_post($post, $me)) {
        throw new HttpError(403, 'Bu posta girişiniz yoxdur.');
    }
    rate_limit('comment', 30, 60);
    $body = mb_substr(input('body'), 0, 1000);
    if ($body === '') {
        throw new HttpError(400, 'Şərh boşdur.');
    }
    q('INSERT INTO comments (post_id, user_id, body, created_at) VALUES (?, ?, ?, ?)', [$post['id'], $me['id'], $body, now()]);
    redirect('/post/' . $post['id'] . '#comments');
}

function do_delete_comment(string $id): void
{
    $me = require_login();
    $c = row('SELECT c.*, p.creator_id FROM comments c JOIN posts p ON p.id = c.post_id WHERE c.id = ?', [(int) $id]);
    $allowed = $c && (in_array((int) $me['id'], [(int) $c['user_id'], (int) $c['creator_id']], true) || is_admin($me));
    if (!$allowed) {
        throw new HttpError(404, 'Şərh tapılmadı.');
    }
    q('DELETE FROM comments WHERE id = ?', [$c['id']]);
    redirect('/post/' . $c['post_id'] . '#comments');
}

function do_unlock(string $id): void
{
    $me = require_login();
    if (unlock_post((int) $me['id'], (int) $id)) {
        flash('success', 'Post açıldı. Həzz alın!');
    }
    back('/post/' . (int) $id);
}

function do_report(): void
{
    $me = require_login();
    rate_limit('report', 10, 3600);
    $postId = (int) input('post_id') ?: null;
    $userId = (int) input('user_id') ?: null;
    $reason = mb_substr(input('reason'), 0, 500);
    if ((!$postId && !$userId) || $reason === '') {
        throw new HttpError(400, 'Şikayət səbəbini yazın.');
    }
    q('INSERT INTO reports (reporter_id, post_id, user_id, reason, status, created_at) VALUES (?, ?, ?, ?, ?, ?)', [$me['id'], $postId, $userId, $reason, 'open', now()]);
    flash('success', 'Şikayətiniz moderatorlara göndərildi.');
    back();
}

// ------------------------------------------------------------ protected files

function serve_media(string $id): void
{
    $m = row('SELECT * FROM media WHERE id = ?', [(int) $id]);
    $post = $m ? row('SELECT * FROM posts WHERE id = ?', [$m['post_id']]) : null;
    if (!$m || !can_view_post($post, current_user())) {
        http_response_code($m ? 403 : 404);
        exit;
    }
    session_write_close(); // don't block other requests during long video streams
    stream_file($m['filename'], $m['mime'], 'private, max-age=3600');
}

function serve_profile_file(string $file): void
{
    // Only files that are actually somebody's avatar/cover — never post media.
    if (!scalar('SELECT 1 FROM users WHERE avatar = ? OR cover = ?', [$file, $file]) || !is_file(upload_dir() . '/' . $file)) {
        http_response_code(404);
        exit;
    }
    session_write_close();
    $mime = (new finfo(FILEINFO_MIME_TYPE))->file(upload_dir() . '/' . $file) ?: 'application/octet-stream';
    stream_file($file, $mime, 'public, max-age=86400');
}
