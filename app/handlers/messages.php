<?php
declare(strict_types=1);

function page_inbox(): void
{
    $me = require_login();
    $id = (int) $me['id'];
    // Last message per conversation partner.
    $threads = rows(
        'SELECT m.*, u.username, u.display_name, u.avatar,
            (SELECT COUNT(*) FROM messages x WHERE x.sender_id = u.id AND x.recipient_id = ? AND x.read_at IS NULL) AS unread
         FROM messages m
         JOIN users u ON u.id = CASE WHEN m.sender_id = ? THEN m.recipient_id ELSE m.sender_id END
         WHERE m.id IN (
            SELECT MAX(id) FROM messages WHERE sender_id = ? OR recipient_id = ?
            GROUP BY CASE WHEN sender_id = ? THEN recipient_id ELSE sender_id END
         )
         ORDER BY m.id DESC',
        [$id, $id, $id, $id, $id]
    );
    view('inbox', ['title' => 'Mesajlar', 'threads' => $threads]);
}

function thread_partner(string $username): array
{
    $u = row('SELECT * FROM users WHERE LOWER(username) = LOWER(?) AND banned = 0', [$username]);
    if (!$u) {
        throw new HttpError(404, 'İstifadəçi tapılmadı.');
    }
    return $u;
}

function page_thread(string $username): void
{
    $me = require_login();
    $other = thread_partner($username);
    if ((int) $other['id'] === (int) $me['id']) {
        redirect('/messages');
    }
    q('UPDATE messages SET read_at = ? WHERE sender_id = ? AND recipient_id = ? AND read_at IS NULL', [now(), $other['id'], $me['id']]);
    $messages = rows(
        'SELECT * FROM (
            SELECT * FROM messages
            WHERE (sender_id = ? AND recipient_id = ?) OR (sender_id = ? AND recipient_id = ?)
            ORDER BY id DESC LIMIT 200) t ORDER BY id',
        [$me['id'], $other['id'], $other['id'], $me['id']]
    );
    view('thread', [
        'title' => $other['display_name'],
        'other' => $other,
        'messages' => $messages,
        'can_send' => can_message($me, $other),
    ]);
}

function do_send_message(string $username): void
{
    $me = require_login();
    $other = thread_partner($username);
    if (!can_message($me, $other)) {
        throw new HttpError(403, 'Mesaj yazmaq üçün abunəlik tələb olunur.');
    }
    rate_limit('dm', 60, 60);
    $body = mb_substr(input('body'), 0, 2000);
    if ($body === '') {
        throw new HttpError(400, 'Mesaj boşdur.');
    }
    q('INSERT INTO messages (sender_id, recipient_id, body, created_at) VALUES (?, ?, ?, ?)', [$me['id'], $other['id'], $body, now()]);
    redirect('/messages/' . rawurlencode($other['username']) . '#end');
}
