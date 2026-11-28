<?php
// Verilənlər bazası cədvəllərini yaradır və admin hesabını əlavə edir.
// İstifadə: php bin/install.php
declare(strict_types=1);
require dirname(__DIR__) . '/app/bootstrap.php';

$driver = config('db.driver');
$sql = file_get_contents(ROOT . '/database/schema.' . ($driver === 'mysql' ? 'mysql' : 'sqlite') . '.sql');
foreach (array_filter(array_map('trim', explode(';', $sql))) as $stmt) {
    db()->exec($stmt);
}
echo "✔ Cədvəllər yaradıldı ($driver)\n";

$a = config('admin');
if (!row('SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)', [$a['email']])) {
    q(
        "INSERT INTO users (email, username, password_hash, display_name, bio, dob, role, created_at) VALUES (?, ?, ?, 'Admin', '', '1990-01-01', 'admin', ?)",
        [$a['email'], $a['username'], password_hash($a['password'], PASSWORD_DEFAULT), now()]
    );
    echo "✔ Admin yaradıldı: {$a['email']}\n";
} else {
    echo "• Admin artıq mövcuddur\n";
}
upload_dir();
echo "✔ Hazırdır. İşə salmaq üçün: php -S localhost:8000 -t public public/index.php\n";
