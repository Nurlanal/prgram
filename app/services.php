<?php
declare(strict_types=1);

/*
 * Business rules: access control for content and every money movement.
 * All money is stored as integer cents.
 */

const SUB_DAYS = 30;

function fee_percent(): int
{
    return (int) config('fee_percent', 20);
}

function plus_days(string $fromUtc, int $days): string
{
    return gmdate('Y-m-d H:i:s', strtotime($fromUtc . ' UTC') + $days * 86400);
}

function active_subscription(?int $fanId, int $creatorId): ?array
{
    if (!$fanId) {
        return null;
    }
    return row('SELECT * FROM subscriptions WHERE fan_id = ? AND creator_id = ? AND expires_at > ?', [$fanId, $creatorId, now()]);
}

function has_unlocked(?int $fanId, int $postId): bool
{
    return $fanId && (bool) scalar('SELECT 1 FROM unlocks WHERE fan_id = ? AND post_id = ?', [$fanId, $postId]);
}

/**
 * Who can see a post's media / full text:
 *  - its creator and admins, always
 *  - PPV posts (price > 0): only fans who paid for it
 *  - free public posts: everyone
 *  - subscriber posts: fans with an active subscription
 */
function can_view_post(?array $post, ?array $user): bool
{
    if (!$post || $post['removed']) {
        return false;
    }
    if ($user && ((int) $user['id'] === (int) $post['creator_id'] || is_admin($user))) {
        return true;
    }
    if ((int) $post['ppv_price_cents'] > 0) {
        return has_unlocked($user ? (int) $user['id'] : null, (int) $post['id']);
    }
    if ($post['visibility'] === 'public') {
        return true;
    }
    return (bool) active_subscription($user ? (int) $user['id'] : null, (int) $post['creator_id']);
}

/** Users may DM each other when one is subscribed to the other, or one is an admin. */
function can_message(array $a, array $b): bool
{
    if ((int) $a['id'] === (int) $b['id']) {
        return false;
    }
    if (is_admin($a) || is_admin($b)) {
        return true;
    }
    return active_subscription((int) $a['id'], (int) $b['id']) || active_subscription((int) $b['id'], (int) $a['id']);
}

function approved_creator(int $id): array
{
    $c = row("SELECT * FROM users WHERE id = ? AND role = 'creator' AND creator_status = 'approved' AND banned = 0", [$id]);
    if (!$c) {
        throw new HttpError(404, 'Kreator tapılmadı.');
    }
    return $c;
}

/**
 * Moves $amount from the fan's wallet to the creator minus the platform fee.
 * Must be called inside transaction(). The balance check is part of the UPDATE,
 * so two concurrent purchases can never overdraw a wallet.
 */
function charge(int $fanId, int $creatorId, int $amount, string $type, ?int $refId = null, ?string $note = null): array
{
    if ($amount <= 0) {
        throw new HttpError(400, 'Yanlış məbləğ.');
    }
    $debited = q('UPDATE users SET balance_cents = balance_cents - ? WHERE id = ? AND balance_cents >= ?', [$amount, $fanId, $amount])->rowCount();
    if (!$debited) {
        throw new HttpError(402, 'Balans kifayət etmir. Cüzdanınızı artırın.');
    }
    $fee = (int) round($amount * fee_percent() / 100);
    $net = $amount - $fee;
    q('UPDATE users SET balance_cents = balance_cents + ? WHERE id = ?', [$net, $creatorId]);
    q(
        'INSERT INTO transactions (from_user, to_user, type, gross_cents, fee_cents, net_cents, ref_id, note, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
        [$fanId, $creatorId, $type, $amount, $fee, $net, $refId, $note, now()]
    );
    return ['fee' => $fee, 'net' => $net];
}

function subscribe(int $fanId, int $creatorId): array
{
    if ($fanId === $creatorId) {
        throw new HttpError(400, 'Özünüzə abunə ola bilməzsiniz.');
    }
    return transaction(function () use ($fanId, $creatorId) {
        $creator = approved_creator($creatorId);
        $price = (int) $creator['sub_price_cents'];
        $existing = row('SELECT * FROM subscriptions WHERE fan_id = ? AND creator_id = ?', [$fanId, $creatorId]);
        $from = ($existing && $existing['expires_at'] > now()) ? $existing['expires_at'] : now();
        $expires = plus_days($from, SUB_DAYS);
        if ($price > 0) {
            charge($fanId, $creatorId, $price, 'subscription', null, SUB_DAYS . ' gün');
        }
        if ($existing) {
            q('UPDATE subscriptions SET expires_at = ?, price_cents = ?, auto_renew = 1 WHERE id = ?', [$expires, $price, $existing['id']]);
        } else {
            q(
                'INSERT INTO subscriptions (fan_id, creator_id, price_cents, auto_renew, started_at, expires_at) VALUES (?, ?, ?, 1, ?, ?)',
                [$fanId, $creatorId, $price, now(), $expires]
            );
        }
        return ['expires_at' => $expires, 'price_cents' => $price];
    });
}

/**
 * Renews expired subscriptions with auto-renew on. Run from cron (bin/cron.php).
 * If the fan can't pay, auto-renew is switched off and the subscription lapses.
 */
function process_renewals(): array
{
    $due = rows('SELECT * FROM subscriptions WHERE auto_renew = 1 AND expires_at <= ?', [now()]);
    $ok = 0;
    $failed = 0;
    foreach ($due as $s) {
        try {
            transaction(function () use ($s) {
                $creator = approved_creator((int) $s['creator_id']);
                $price = (int) $creator['sub_price_cents'];
                if ($price > 0) {
                    charge((int) $s['fan_id'], (int) $s['creator_id'], $price, 'subscription', null, 'avto-yeniləmə');
                }
                q('UPDATE subscriptions SET expires_at = ?, price_cents = ? WHERE id = ?', [plus_days(now(), SUB_DAYS), $price, $s['id']]);
            });
            $ok++;
        } catch (Throwable $e) {
            q('UPDATE subscriptions SET auto_renew = 0 WHERE id = ?', [$s['id']]);
            $failed++;
        }
    }
    return ['renewed' => $ok, 'lapsed' => $failed];
}

function unlock_post(int $fanId, int $postId): bool
{
    return transaction(function () use ($fanId, $postId) {
        $post = row('SELECT * FROM posts WHERE id = ? AND removed = 0', [$postId]);
        if (!$post) {
            throw new HttpError(404, 'Post tapılmadı.');
        }
        if ((int) $post['ppv_price_cents'] <= 0) {
            throw new HttpError(400, 'Bu post pullu deyil.');
        }
        if ((int) $post['creator_id'] === $fanId) {
            throw new HttpError(400, 'Bu sizin postunuzdur.');
        }
        if (has_unlocked($fanId, $postId)) {
            return false;
        }
        approved_creator((int) $post['creator_id']);
        charge($fanId, (int) $post['creator_id'], (int) $post['ppv_price_cents'], 'ppv', $postId);
        q('INSERT INTO unlocks (fan_id, post_id, price_cents, created_at) VALUES (?, ?, ?, ?)', [$fanId, $postId, $post['ppv_price_cents'], now()]);
        return true;
    });
}

function send_tip(int $fanId, int $creatorId, int $amount, string $note = ''): void
{
    if ($fanId === $creatorId) {
        throw new HttpError(400, 'Özünüzə bəxşiş göndərə bilməzsiniz.');
    }
    if ($amount < 100 || $amount > 100000) {
        throw new HttpError(400, 'Bəxşiş $1.00 – $1000.00 arası olmalıdır.');
    }
    transaction(function () use ($fanId, $creatorId, $amount, $note) {
        approved_creator($creatorId);
        $note = mb_substr(trim($note), 0, 200);
        charge($fanId, $creatorId, $amount, 'tip', null, $note ?: null);
        $text = '💸 ' . money($amount) . ' bəxşiş göndərdi' . ($note !== '' ? ': ' . $note : '');
        q('INSERT INTO messages (sender_id, recipient_id, body, created_at) VALUES (?, ?, ?, ?)', [$fanId, $creatorId, $text, now()]);
    });
}

/** Full data needed to render a post card for a viewer. */
function present_post(array $post, ?array $viewer): array
{
    $vid = $viewer ? (int) $viewer['id'] : 0;
    $canView = can_view_post($post, $viewer);
    $media = rows('SELECT id, mime FROM media WHERE post_id = ? ORDER BY id', [$post['id']]);
    return [
        'post' => $post,
        'creator' => row('SELECT * FROM users WHERE id = ?', [$post['creator_id']]),
        'can_view' => $canView,
        'media' => array_map(fn ($m) => [
            'id' => (int) $m['id'],
            'type' => str_starts_with($m['mime'], 'video/') ? 'video' : 'image',
        ], $media),
        'likes' => (int) scalar('SELECT COUNT(*) FROM likes WHERE post_id = ?', [$post['id']]),
        'comments' => (int) scalar('SELECT COUNT(*) FROM comments WHERE post_id = ?', [$post['id']]),
        'liked' => $vid && scalar('SELECT 1 FROM likes WHERE user_id = ? AND post_id = ?', [$vid, $post['id']]),
        'is_owner' => $vid === (int) $post['creator_id'],
        'subscribed' => $vid && active_subscription($vid, (int) $post['creator_id']),
    ];
}

function unread_messages(int $userId): int
{
    return (int) scalar('SELECT COUNT(*) FROM messages WHERE recipient_id = ? AND read_at IS NULL', [$userId]);
}
