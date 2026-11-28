<?php
declare(strict_types=1);

require dirname(__DIR__) . '/app/bootstrap.php';

// PHP built-in server: serve real static files directly.
if (PHP_SAPI === 'cli-server') {
    $file = __DIR__ . parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
    if ($file !== __FILE__ && is_file($file)) {
        return false;
    }
}

header('X-Frame-Options: DENY');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: same-origin');
header("Content-Security-Policy: default-src 'self'; img-src 'self' data:; media-src 'self'; style-src 'self'; script-src 'self'; frame-ancestors 'none'; form-action 'self'");

start_session();

foreach (glob(ROOT . '/app/handlers/*.php') as $h) {
    require $h;
}

$routes = [
    // public / auth
    ['GET',  '#^/$#', 'page_home'],
    ['GET',  '#^/register$#', 'page_register'],
    ['POST', '#^/register$#', 'do_register'],
    ['GET',  '#^/login$#', 'page_login'],
    ['POST', '#^/login$#', 'do_login'],
    ['POST', '#^/logout$#', 'do_logout'],
    ['GET',  '#^/terms$#', 'page_terms'],
    ['GET',  '#^/explore$#', 'page_explore'],
    ['GET',  '#^/u/([A-Za-z0-9_]{3,24})$#', 'page_profile'],

    // posts
    ['GET',  '#^/post/(\d+)$#', 'page_post'],
    ['POST', '#^/post$#', 'do_create_post'],
    ['POST', '#^/post/(\d+)/delete$#', 'do_delete_post'],
    ['POST', '#^/post/(\d+)/like$#', 'do_like'],
    ['POST', '#^/post/(\d+)/comment$#', 'do_comment'],
    ['POST', '#^/post/(\d+)/unlock$#', 'do_unlock'],
    ['POST', '#^/comment/(\d+)/delete$#', 'do_delete_comment'],
    ['POST', '#^/report$#', 'do_report'],

    // money
    ['POST', '#^/subscribe/(\d+)$#', 'do_subscribe'],
    ['POST', '#^/subscription/(\d+)/renew$#', 'do_toggle_renew'],
    ['GET',  '#^/subscriptions$#', 'page_subscriptions'],
    ['POST', '#^/tip/(\d+)$#', 'do_tip'],
    ['GET',  '#^/wallet$#', 'page_wallet'],
    ['POST', '#^/wallet/topup$#', 'do_topup'],
    ['POST', '#^/wallet/payout$#', 'do_payout'],
    ['GET',  '#^/dashboard$#', 'page_dashboard'],

    // settings
    ['GET',  '#^/settings$#', 'page_settings'],
    ['POST', '#^/settings/profile$#', 'do_settings_profile'],
    ['POST', '#^/settings/images$#', 'do_settings_images'],
    ['POST', '#^/settings/password$#', 'do_settings_password'],
    ['GET',  '#^/become-creator$#', 'page_become_creator'],
    ['POST', '#^/become-creator$#', 'do_become_creator'],

    // messages
    ['GET',  '#^/messages$#', 'page_inbox'],
    ['GET',  '#^/messages/([A-Za-z0-9_]{3,24})$#', 'page_thread'],
    ['POST', '#^/messages/([A-Za-z0-9_]{3,24})$#', 'do_send_message'],

    // admin
    ['GET',  '#^/admin$#', 'page_admin'],
    ['POST', '#^/admin/creator/(\d+)/(approve|reject)$#', 'do_admin_creator'],
    ['POST', '#^/admin/report/(\d+)/(remove|ban|dismiss)$#', 'do_admin_report'],
    ['POST', '#^/admin/user/(\d+)/(ban|unban)$#', 'do_admin_user'],
    ['POST', '#^/admin/payout/(\d+)/(paid|reject)$#', 'do_admin_payout'],

    // protected files
    ['GET',  '#^/media/(\d+)$#', 'serve_media'],
    ['GET',  '#^/pfile/([a-f0-9]{32}\.\w{3,4})$#', 'serve_profile_file'],
];

$method = $_SERVER['REQUEST_METHOD'] === 'HEAD' ? 'GET' : $_SERVER['REQUEST_METHOD'];
$path = rawurldecode(parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH) ?: '/');
if ($path !== '/' && str_ends_with($path, '/')) {
    $path = rtrim($path, '/');
}

try {
    foreach ($routes as [$m, $pattern, $handler]) {
        if ($m === $method && preg_match($pattern, $path, $args)) {
            array_shift($args);
            if ($method === 'POST') {
                verify_csrf();
            }
            $handler(...$args);
            exit;
        }
    }
    throw new HttpError(404, 'Səhifə tapılmadı.');
} catch (HttpError $e) {
    // Form submissions: show the error on the previous page.
    if ($method === 'POST' && $e->status !== 419 && !empty($_SERVER['HTTP_REFERER'])) {
        flash('error', $e->getMessage());
        back();
    }
    view('error', ['status' => $e->status, 'message' => $e->getMessage()], $e->status);
} catch (Throwable $e) {
    error_log((string) $e);
    view('error', ['status' => 500, 'message' => 'Daxili xəta baş verdi.'], 500);
}
