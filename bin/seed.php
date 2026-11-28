<?php
// Demo məlumat: 3 kreator, 1 fan, nümunə postlar (yaradılan placeholder şəkillərlə).
// İstifadə: php bin/seed.php   (əvvəlcə bin/install.php)
declare(strict_types=1);
require dirname(__DIR__) . '/app/bootstrap.php';

function make_image(string $text, array $rgb): string
{
    $im = imagecreatetruecolor(1080, 1080);
    [$r, $g, $b] = $rgb;
    for ($y = 0; $y < 1080; $y++) {
        $k = $y / 1080;
        $c = imagecolorallocate($im, (int) ($r * (1 - $k) + 30 * $k), (int) ($g * (1 - $k) + 20 * $k), (int) ($b * (1 - $k) + 60 * $k));
        imageline($im, 0, $y, 1080, $y, $c);
    }
    $white = imagecolorallocate($im, 255, 255, 255);
    imagestring($im, 5, 40, 1020, $text, $white);
    $name = bin2hex(random_bytes(16)) . '.jpg';
    imagejpeg($im, upload_dir() . '/' . $name, 85);
    imagedestroy($im);
    return $name;
}

$creators = [
    ['aylin_art', 'Aylin Art', 'Rəssam. Hər həftə yeni eskizlər və dərslər 🎨', 499, [124, 92, 255]],
    ['fit_with_murad', 'Murad Fitness', 'Şəxsi məşq proqramları, qidalanma planları 💪', 999, [255, 92, 154]],
    ['chef_lala', 'Şef Lalə', 'Azərbaycan mətbəxinin sirləri 🍲', 0, [255, 160, 60]],
];
$pw = password_hash('password123', PASSWORD_DEFAULT);
foreach ($creators as [$u, $name, $bio, $price, $rgb]) {
    if (row('SELECT 1 FROM users WHERE username = ?', [$u])) {
        continue;
    }
    q(
        "INSERT INTO users (email, username, password_hash, display_name, bio, dob, role, creator_status, legal_name, sub_price_cents, avatar, cover, created_at)
         VALUES (?, ?, ?, ?, ?, '1995-05-05', 'creator', 'approved', ?, ?, ?, ?, ?)",
        ["$u@example.com", $u, $pw, $name, $bio, $name, $price, make_image($name, $rgb), make_image('', $rgb), now()]
    );
    $cid = (int) db()->lastInsertId();
    $posts = [
        ['Salam! Səhifəmə xoş gəldiniz. Bu post hamı üçün açıqdır 👋', 'public', 0],
        ['Yalnız abunəçilər üçün eksklüziv məzmun 🔒', 'subscribers', 0],
        ['Xüsusi paket — tək ödənişlə açın 💎', 'subscribers', 299],
    ];
    foreach ($posts as $i => [$body, $vis, $ppv]) {
        q('INSERT INTO posts (creator_id, body, visibility, ppv_price_cents, removed, created_at) VALUES (?, ?, ?, ?, 0, ?)', [$cid, $body, $vis, $ppv, now()]);
        $pid = (int) db()->lastInsertId();
        q('INSERT INTO media (post_id, filename, mime, created_at) VALUES (?, ?, ?, ?)', [$pid, make_image("$name #" . ($i + 1), $rgb), 'image/jpeg', now()]);
    }
    echo "✔ kreator @$u\n";
}
if (!row("SELECT 1 FROM users WHERE username = 'demo_fan'")) {
    q(
        "INSERT INTO users (email, username, password_hash, display_name, bio, dob, balance_cents, created_at) VALUES ('fan@example.com', 'demo_fan', ?, 'Demo Fan', '', '1998-01-01', 10000, ?)",
        [$pw, now()]
    );
    echo "✔ fan @demo_fan (balans \$100)\n";
}
echo "Bütün demo hesabların şifrəsi: password123\n";
