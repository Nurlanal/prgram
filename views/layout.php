<?php /** @var string $content */ $flashes = take_flashes(); $unread = $me ? unread_messages((int) $me['id']) : 0; ?>
<!doctype html>
<html lang="az">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title><?= e(($title ?? '') ? $title . ' · ' : '') ?><?= e(config('app_name')) ?></title>
  <link rel="stylesheet" href="/assets/style.css">
  <link rel="icon" href="/assets/logo.svg">
  <script src="/assets/app.js" defer></script>
</head>
<body>
<header class="topbar">
  <div class="wrap topbar-inner">
    <a class="brand" href="/"><img src="/assets/logo.svg" alt="" width="28" height="28"><span><?= e(config('app_name')) ?></span></a>
    <nav class="nav">
      <?php if ($me): ?>
        <a href="/" class="hide-sm">Lent</a>
        <a href="/explore" class="hide-sm">Kəşf et</a>
        <a href="/messages">Mesajlar<?php if ($unread): ?> <span class="badge"><?= $unread ?></span><?php endif; ?></a>
        <a href="/wallet" class="pill"><?= money((int) $me['balance_cents']) ?></a>
        <details class="menu">
          <summary><img class="avatar sm" src="<?= e(avatar_url($me)) ?>" alt=""></summary>
          <div class="menu-list">
            <a href="/u/<?= e($me['username']) ?>">Profilim</a>
            <?php if (is_creator($me)): ?><a href="/dashboard">Kreator paneli</a><?php else: ?><a href="/become-creator">Kreator ol</a><?php endif; ?>
            <a href="/subscriptions">Abunəliklər</a>
            <a href="/wallet">Cüzdan</a>
            <a href="/settings">Tənzimləmələr</a>
            <?php if (is_admin($me)): ?><a href="/admin">Admin panel</a><?php endif; ?>
            <form method="post" action="/logout"><?= csrf_field() ?><button class="linklike">Çıxış</button></form>
          </div>
        </details>
      <?php else: ?>
        <a href="/explore" class="hide-sm">Kəşf et</a>
        <a href="/login">Daxil ol</a>
        <a class="btn" href="/register">Qeydiyyat</a>
      <?php endif; ?>
    </nav>
  </div>
</header>
<main class="wrap">
  <?php foreach ($flashes as $f): ?>
    <div class="flash <?= e($f['type']) ?>"><?= e($f['message']) ?></div>
  <?php endforeach; ?>
  <?= $content ?>
</main>
<footer class="wrap footer">
  <span>© <?= gmdate('Y') ?> <?= e(config('app_name')) ?> · 18+</span>
  <a href="/terms">İstifadə şərtləri</a>
</footer>
</body>
</html>
