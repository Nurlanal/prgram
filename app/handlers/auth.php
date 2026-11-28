<?php
declare(strict_types=1);

function age_from(string $dob): int
{
    $d = DateTime::createFromFormat('!Y-m-d', $dob);
    if (!$d || $d->format('Y-m-d') !== $dob) {
        return -1;
    }
    return (int) $d->diff(new DateTime('today'))->y;
}

function page_register(): void
{
    if (current_user()) {
        redirect('/');
    }
    view('register', ['title' => 'Qeydiyyat']);
}

function do_register(): void
{
    rate_limit('register', 10, 3600);
    $email = input('email');
    $username = input('username');
    $password = $_POST['password'] ?? '';
    $name = mb_substr(input('display_name') ?: $username, 0, 50);
    $dob = input('dob');
    $_SESSION['old'] = compact('email', 'username', 'dob') + ['display_name' => $name];

    $error = null;
    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
        $error = 'Email düzgün deyil.';
    } elseif (!preg_match('/^[A-Za-z0-9_]{3,24}$/', $username)) {
        $error = 'İstifadəçi adı 3–24 simvol olmalıdır (hərf, rəqəm, _).';
    } elseif (in_array(strtolower($username), ['admin', 'settings', 'messages', 'explore', 'login', 'register'], true)) {
        $error = 'Bu istifadəçi adı rezervdir.';
    } elseif (!is_string($password) || strlen($password) < 8) {
        $error = 'Şifrə ən azı 8 simvol olmalıdır.';
    } elseif (age_from($dob) < 18) {
        $error = 'Platformadan yalnız 18 yaşdan yuxarı şəxslər istifadə edə bilər.';
    } elseif (empty($_POST['accept_terms'])) {
        $error = 'İstifadə şərtlərini qəbul etməlisiniz.';
    } elseif (scalar('SELECT 1 FROM users WHERE LOWER(email) = LOWER(?) OR LOWER(username) = LOWER(?)', [$email, $username])) {
        $error = 'Bu email və ya istifadəçi adı artıq qeydiyyatdadır.';
    }
    if ($error) {
        flash('error', $error);
        redirect('/register');
    }

    q(
        'INSERT INTO users (email, username, password_hash, display_name, bio, dob, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
        [$email, $username, password_hash($password, PASSWORD_DEFAULT), $name, '', $dob, now()]
    );
    login_user((int) db()->lastInsertId());
    unset($_SESSION['old']);
    flash('success', 'Xoş gəldiniz! Hesabınız yaradıldı.');
    redirect('/explore');
}

function page_login(): void
{
    if (current_user()) {
        redirect('/');
    }
    view('login', ['title' => 'Daxil ol']);
}

function do_login(): void
{
    rate_limit('login', 15, 900);
    $login = input('login');
    $password = $_POST['password'] ?? '';
    $_SESSION['old'] = ['login' => $login];
    $u = row('SELECT * FROM users WHERE LOWER(email) = LOWER(?) OR LOWER(username) = LOWER(?)', [$login, $login]);
    if (!$u || !is_string($password) || !password_verify($password, $u['password_hash'])) {
        flash('error', 'Login və ya şifrə yanlışdır.');
        redirect('/login');
    }
    if ($u['banned']) {
        flash('error', 'Hesabınız bloklanıb.');
        redirect('/login');
    }
    if (password_needs_rehash($u['password_hash'], PASSWORD_DEFAULT)) {
        q('UPDATE users SET password_hash = ? WHERE id = ?', [password_hash($password, PASSWORD_DEFAULT), $u['id']]);
    }
    login_user((int) $u['id']);
    unset($_SESSION['old']);
    redirect('/');
}

function do_logout(): void
{
    $_SESSION = [];
    session_regenerate_id(true);
    redirect('/');
}

function page_terms(): void
{
    view('terms', ['title' => 'İstifadə şərtləri']);
}
