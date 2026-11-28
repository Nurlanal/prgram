<?php
// Bitmiş abunəlikləri avtomatik yeniləyir. Cron-a əlavə edin (hər saat):
//   0 * * * * php /path/to/project/bin/cron.php
declare(strict_types=1);
require dirname(__DIR__) . '/app/bootstrap.php';

$r = process_renewals();
q('DELETE FROM rate_limits WHERE reset_at < ?', [time()]);
echo gmdate('c') . " yeniləndi: {$r['renewed']}, bitdi: {$r['lapsed']}\n";
