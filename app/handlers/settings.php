<?php
declare(strict_types=1);

function page_settings(): void
{
    require_login();
    view('settings', ['title' => 'Tənzimləmələr', 'u' => refresh_user()]);
}

function do_settings_profile(): void
{
    $me = require_login();
    $name = mb_substr(input('display_name'), 0, 50);
    $bio = mb_substr(input('bio'), 0, 1000);
    if ($name === '') {
        throw new HttpError(400, 'Ad boş ola bilməz.');
    }
    $price = (int) $me['sub_price_cents'];
    if (is_creator($me) && isset($_POST['sub_price'])) {
        $raw = input('sub_price');
        $price = $raw === '' ? 0 : to_cents($raw);
        if ($price === null || ($price !== 0 && ($price < 300 || $price > 100000))) {
            throw new HttpError(400, 'Abunə qiyməti 0 (pulsuz) və ya $3.00 – $1000.00 arası olmalıdır.');
        }
    }
    q('UPDATE users SET display_name = ?, bio = ?, sub_price_cents = ? WHERE id = ?', [$name, $bio, $price, $me['id']]);
    flash('success', 'Profil yeniləndi.');
    redirect('/settings');
}

function do_settings_images(): void
{
    $me = require_login();
    $changed = false;
    foreach (['avatar', 'cover'] as $field) {
        $files = uploaded_files($field);
        if (!$files) {
            continue;
        }
        $s = store_upload($files[0], false);
        q("UPDATE users SET $field = ? WHERE id = ?", [$s['filename'], $me['id']]);
        delete_upload($me[$field]);
        $changed = true;
    }
    foreach (['avatar', 'cover'] as $field) {
        if (!empty($_POST['remove_' . $field]) && $me[$field]) {
            q("UPDATE users SET $field = NULL WHERE id = ?", [$me['id']]);
            delete_upload($me[$field]);
            $changed = true;
        }
    }
    flash($changed ? 'success' : 'error', $changed ? 'Şəkillər yeniləndi.' : 'Fayl seçilməyib.');
    redirect('/settings');
}

function do_settings_password(): void
{
    $me = require_login();
    $current = $_POST['current'] ?? '';
    $next = $_POST['next'] ?? '';
    if (!is_string($current) || !password_verify($current, $me['password_hash'])) {
        throw new HttpError(400, 'Cari şifrə yanlışdır.');
    }
    if (!is_string($next) || strlen($next) < 8) {
        throw new HttpError(400, 'Yeni şifrə ən azı 8 simvol olmalıdır.');
    }
    q('UPDATE users SET password_hash = ? WHERE id = ?', [password_hash($next, PASSWORD_DEFAULT), $me['id']]);
    session_regenerate_id(true);
    flash('success', 'Şifrə dəyişdirildi.');
    redirect('/settings');
}

function page_become_creator(): void
{
    $me = require_login();
    view('become_creator', ['title' => 'Kreator ol', 'u' => $me]);
}

/**
 * Creator application. In production, connect an ID-verification (KYC) provider here:
 * creators must prove identity and age before they can earn money.
 */
function do_become_creator(): void
{
    $me = require_login();
    if (is_admin($me)) {
        throw new HttpError(400, 'Admin hesabı kreator ola bilməz.');
    }
    if ($me['creator_status'] === 'approved') {
        redirect('/dashboard');
    }
    if ($me['creator_status'] === 'pending') {
        throw new HttpError(400, 'Müraciətiniz artıq yoxlanılır.');
    }
    $legal = mb_substr(input('legal_name'), 0, 100);
    if (mb_strlen($legal) < 3) {
        throw new HttpError(400, 'Rəsmi ad və soyadınızı yazın.');
    }
    if (empty($_POST['confirm_age']) || empty($_POST['confirm_rights']) || empty($_POST['confirm_rules'])) {
        throw new HttpError(400, 'Bütün təsdiqləri qəbul etməlisiniz.');
    }
    q("UPDATE users SET creator_status = 'pending', legal_name = ? WHERE id = ?", [$legal, $me['id']]);
    flash('success', 'Müraciətiniz qəbul edildi. Admin yoxladıqdan sonra kreator funksiyaları aktivləşəcək.');
    redirect('/become-creator');
}
