<?php
// Bu faylı config.php adı ilə kopyalayın və dəyərləri dəyişin.
return [
    'app_name' => 'Fanora',
    'base_url' => 'http://localhost:8000',

    // SQLite (default) və ya MySQL
    'db' => [
        'driver' => 'sqlite',                       // 'sqlite' | 'mysql'
        'sqlite_path' => __DIR__ . '/storage/fanora.sqlite',
        'mysql' => [
            'host' => '127.0.0.1',
            'port' => 3306,
            'name' => 'fanora',
            'user' => 'root',
            'pass' => '',
        ],
    ],

    'upload_dir' => __DIR__ . '/storage/uploads',
    'max_upload_mb' => 200,

    // Platform komissiyası (%)
    'fee_percent' => 20,
    // Demo: istifadəçi cüzdanı saxta ödənişlə artırıla bilər. Produksiyada false edin
    // və real ödəniş provayderinin webhook-u ilə balansı artırın.
    'demo_payments' => true,

    // `php bin/install.php` bu admini yaradır
    'admin' => [
        'email' => 'admin@example.com',
        'username' => 'admin',
        'password' => 'change-me-please',
    ],

    // HTTPS istifadə edirsinizsə true
    'cookie_secure' => false,
];
