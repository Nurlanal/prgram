<?php
declare(strict_types=1);

function page_admin(): void
{
    require_admin();
    $tab = $_GET['tab'] ?? 'overview';
    $term = trim((string) ($_GET['q'] ?? ''));
    $like = '%' . $term . '%';
    view('admin', [
        'title' => 'Admin',
        'tab' => $tab,
        'term' => $term,
        'stats' => [
            'users' => (int) scalar('SELECT COUNT(*) FROM users'),
            'creators' => (int) scalar("SELECT COUNT(*) FROM users WHERE role = 'creator' AND creator_status = 'approved'"),
            'posts' => (int) scalar('SELECT COUNT(*) FROM posts WHERE removed = 0'),
            'active_subs' => (int) scalar('SELECT COUNT(*) FROM subscriptions WHERE expires_at > ?', [now()]),
            'gross' => (int) scalar("SELECT COALESCE(SUM(gross_cents),0) FROM transactions WHERE type IN ('subscription','tip','ppv')"),
            'fees' => (int) scalar('SELECT COALESCE(SUM(fee_cents),0) FROM transactions'),
        ],
        'applications' => rows("SELECT * FROM users WHERE creator_status = 'pending' ORDER BY id"),
        'reports' => rows(
            "SELECT r.*, ru.username AS reporter, p.body AS post_body, p.creator_id, pu.username AS post_author, tu.username AS target_user
             FROM reports r
             JOIN users ru ON ru.id = r.reporter_id
             LEFT JOIN posts p ON p.id = r.post_id
             LEFT JOIN users pu ON pu.id = p.creator_id
             LEFT JOIN users tu ON tu.id = r.user_id
             WHERE r.status = 'open' ORDER BY r.id"
        ),
        'payouts' => rows(
            "SELECT t.*, u.username, u.legal_name FROM transactions t JOIN users u ON u.id = t.from_user
             WHERE t.type = 'payout' AND t.status = 'pending' ORDER BY t.id"
        ),
        'users' => $tab === 'users' ? rows(
            'SELECT * FROM users WHERE username LIKE ? OR email LIKE ? OR display_name LIKE ? ORDER BY id DESC LIMIT 100',
            [$like, $like, $like]
        ) : [],
    ]);
}

function do_admin_creator(string $id, string $action): void
{
    require_admin();
    $u = row("SELECT * FROM users WHERE id = ? AND creator_status = 'pending'", [(int) $id]);
    if (!$u) {
        throw new HttpError(404, 'Müraciət tapılmadı.');
    }
    if ($action === 'approve') {
        q("UPDATE users SET role = 'creator', creator_status = 'approved' WHERE id = ?", [$u['id']]);
        flash('success', '@' . $u['username'] . ' kreator kimi təsdiqləndi.');
    } else {
        q("UPDATE users SET creator_status = 'rejected' WHERE id = ?", [$u['id']]);
        flash('success', 'Müraciət rədd edildi.');
    }
    redirect('/admin');
}

function do_admin_report(string $id, string $action): void
{
    require_admin();
    $r = row("SELECT r.*, p.creator_id FROM reports r LEFT JOIN posts p ON p.id = r.post_id WHERE r.id = ? AND r.status = 'open'", [(int) $id]);
    if (!$r) {
        throw new HttpError(404, 'Şikayət tapılmadı.');
    }
    if ($action === 'remove' && $r['post_id']) {
        q('UPDATE posts SET removed = 1 WHERE id = ?', [$r['post_id']]);
    }
    if ($action === 'ban') {
        $target = $r['user_id'] ?: $r['creator_id'];
        if ($target) {
            q("UPDATE users SET banned = 1 WHERE id = ? AND role <> 'admin'", [$target]);
        }
        if ($r['post_id']) {
            q('UPDATE posts SET removed = 1 WHERE id = ?', [$r['post_id']]);
        }
    }
    q('UPDATE reports SET status = ? WHERE id = ?', [$action === 'dismiss' ? 'dismissed' : 'resolved', $r['id']]);
    flash('success', 'Şikayət emal edildi.');
    redirect('/admin');
}

function do_admin_user(string $id, string $action): void
{
    $me = require_admin();
    if ((int) $id === (int) $me['id']) {
        throw new HttpError(400, 'Özünüzü bloklaya bilməzsiniz.');
    }
    q("UPDATE users SET banned = ? WHERE id = ? AND role <> 'admin'", [$action === 'ban' ? 1 : 0, (int) $id]);
    flash('success', $action === 'ban' ? 'İstifadəçi bloklandı.' : 'Blok götürüldü.');
    back('/admin?tab=users');
}

function do_admin_payout(string $id, string $action): void
{
    require_admin();
    transaction(function () use ($id, $action) {
        $t = row("SELECT * FROM transactions WHERE id = ? AND type = 'payout' AND status = 'pending'", [(int) $id]);
        if (!$t) {
            throw new HttpError(404, 'Sorğu tapılmadı.');
        }
        if ($action === 'paid') {
            q("UPDATE transactions SET status = 'paid' WHERE id = ?", [$t['id']]);
        } else {
            q("UPDATE transactions SET status = 'rejected' WHERE id = ?", [$t['id']]);
            q('UPDATE users SET balance_cents = balance_cents + ? WHERE id = ?', [$t['gross_cents'], $t['from_user']]);
        }
    });
    flash('success', $action === 'paid' ? 'Ödənildi kimi qeyd olundu.' : 'Sorğu rədd edildi, məbləğ balansa qaytarıldı.');
    redirect('/admin');
}
