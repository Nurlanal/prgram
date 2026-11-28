<?php
declare(strict_types=1);

function do_subscribe(string $creatorId): void
{
    $me = require_login();
    $r = subscribe((int) $me['id'], (int) $creatorId);
    flash('success', 'Abunəlik aktivdir: ' . format_date($r['expires_at']) . ' tarixinədək.');
    back('/');
}

function do_toggle_renew(string $creatorId): void
{
    $me = require_login();
    $on = input('on') === '1' ? 1 : 0;
    $n = q('UPDATE subscriptions SET auto_renew = ? WHERE fan_id = ? AND creator_id = ?', [$on, $me['id'], (int) $creatorId])->rowCount();
    if (!$n && !row('SELECT 1 FROM subscriptions WHERE fan_id = ? AND creator_id = ?', [$me['id'], (int) $creatorId])) {
        throw new HttpError(404, 'Abunəlik tapılmadı.');
    }
    flash('success', $on ? 'Avto-yeniləmə aktivdir.' : 'Avto-yeniləmə dayandırıldı. Abunəlik müddəti bitənə qədər giriş qalır.');
    back('/subscriptions');
}

function page_subscriptions(): void
{
    $me = require_login();
    $subs = rows(
        'SELECT s.expires_at, s.auto_renew, s.price_cents AS paid_cents, u.*
         FROM subscriptions s JOIN users u ON u.id = s.creator_id
         WHERE s.fan_id = ? ORDER BY s.expires_at DESC',
        [$me['id']]
    );
    $fans = is_creator($me) ? rows(
        'SELECT s.expires_at, s.auto_renew, u.* FROM subscriptions s JOIN users u ON u.id = s.fan_id
         WHERE s.creator_id = ? AND s.expires_at > ? ORDER BY s.expires_at DESC',
        [$me['id'], now()]
    ) : [];
    view('subscriptions', ['title' => 'Abunəliklər', 'subs' => $subs, 'fans' => $fans]);
}

function do_tip(string $creatorId): void
{
    $me = require_login();
    rate_limit('tip', 20, 60);
    $amount = to_cents(input('amount'));
    if ($amount === null) {
        throw new HttpError(400, 'Məbləği düzgün yazın.');
    }
    send_tip((int) $me['id'], (int) $creatorId, $amount, input('note'));
    flash('success', money($amount) . ' bəxşiş göndərildi. Təşəkkürlər!');
    back('/');
}

function page_wallet(): void
{
    $me = require_login();
    $tx = rows(
        'SELECT t.*, f.username AS from_username, r.username AS to_username
         FROM transactions t
         LEFT JOIN users f ON f.id = t.from_user
         LEFT JOIN users r ON r.id = t.to_user
         WHERE t.from_user = ? OR t.to_user = ?
         ORDER BY t.id DESC LIMIT 100',
        [$me['id'], $me['id']]
    );
    view('wallet', ['title' => 'Cüzdan', 'tx' => $tx, 'balance' => (int) refresh_user()['balance_cents']]);
}

/**
 * DEMO top-up. For production, replace this with a checkout at a payment
 * provider that allows creator/adult content, and credit the wallet only
 * from the provider's verified webhook.
 */
function do_topup(): void
{
    $me = require_login();
    if (!config('demo_payments')) {
        throw new HttpError(501, 'Ödəniş provayderi hələ qoşulmayıb.');
    }
    $amount = to_cents(input('amount'));
    if ($amount === null || $amount < 500 || $amount > 50000) {
        throw new HttpError(400, 'Məbləğ $5.00 – $500.00 arası olmalıdır.');
    }
    transaction(function () use ($me, $amount) {
        q('UPDATE users SET balance_cents = balance_cents + ? WHERE id = ?', [$amount, $me['id']]);
        q(
            "INSERT INTO transactions (from_user, to_user, type, gross_cents, fee_cents, net_cents, note, created_at)
             VALUES (NULL, ?, 'topup', ?, 0, ?, 'demo', ?)",
            [$me['id'], $amount, $amount, now()]
        );
    });
    flash('success', money($amount) . ' cüzdana əlavə edildi.');
    redirect('/wallet');
}

/** Creator requests a payout; admin marks it paid after the bank transfer. */
function do_payout(): void
{
    $me = require_creator();
    $amount = to_cents(input('amount'));
    if ($amount === null || $amount < 2000) {
        throw new HttpError(400, 'Minimum çıxarış $20.00-dır.');
    }
    transaction(function () use ($me, $amount) {
        $ok = q('UPDATE users SET balance_cents = balance_cents - ? WHERE id = ? AND balance_cents >= ?', [$amount, $me['id'], $amount])->rowCount();
        if (!$ok) {
            throw new HttpError(400, 'Balans kifayət etmir.');
        }
        q(
            "INSERT INTO transactions (from_user, to_user, type, gross_cents, fee_cents, net_cents, status, created_at)
             VALUES (?, NULL, 'payout', ?, 0, ?, 'pending', ?)",
            [$me['id'], $amount, $amount, now()]
        );
    });
    flash('success', 'Çıxarış sorğusu göndərildi. Admin təsdiqindən sonra hesabınıza köçürüləcək.');
    redirect('/wallet');
}

function page_dashboard(): void
{
    $me = require_creator();
    $id = (int) $me['id'];
    $since = gmdate('Y-m-d H:i:s', time() - 30 * 86400);
    $types = "('subscription','tip','ppv')";

    $byType = [];
    foreach (rows("SELECT type, COALESCE(SUM(net_cents),0) AS net, COUNT(*) AS n FROM transactions WHERE to_user = ? AND type IN $types GROUP BY type", [$id]) as $r) {
        $byType[$r['type']] = $r;
    }
    $daily = [];
    foreach (rows("SELECT created_at, net_cents FROM transactions WHERE to_user = ? AND type IN $types AND created_at > ?", [$id, $since]) as $r) {
        $d = substr($r['created_at'], 0, 10);
        $daily[$d] = ($daily[$d] ?? 0) + (int) $r['net_cents'];
    }
    $series = [];
    for ($i = 29; $i >= 0; $i--) {
        $d = gmdate('Y-m-d', time() - $i * 86400);
        $series[$d] = $daily[$d] ?? 0;
    }

    view('dashboard', [
        'title' => 'Kreator paneli',
        'balance' => (int) refresh_user()['balance_cents'],
        'subs' => (int) scalar('SELECT COUNT(*) FROM subscriptions WHERE creator_id = ? AND expires_at > ?', [$id, now()]),
        'last30' => array_sum($series),
        'total' => (int) scalar("SELECT COALESCE(SUM(net_cents),0) FROM transactions WHERE to_user = ? AND type IN $types", [$id]),
        'byType' => $byType,
        'series' => $series,
        'topFans' => rows(
            "SELECT u.username, u.display_name, u.avatar, COALESCE(SUM(t.net_cents),0) AS spent
             FROM transactions t JOIN users u ON u.id = t.from_user
             WHERE t.to_user = ? AND t.type IN $types GROUP BY u.id, u.username, u.display_name, u.avatar
             ORDER BY spent DESC LIMIT 10",
            [$id]
        ),
        'topPosts' => rows(
            'SELECT p.id, p.body, p.ppv_price_cents,
                (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) AS like_count,
                (SELECT COUNT(*) FROM unlocks x WHERE x.post_id = p.id) AS unlock_count
             FROM posts p WHERE p.creator_id = ? AND p.removed = 0
             ORDER BY unlock_count DESC, like_count DESC LIMIT 10',
            [$id]
        ),
    ]);
}
