<?php $creator = is_creator($user); $self = $me && (int) $me['id'] === (int) $user['id']; $c = $user; ?>
<section class="profile card">
  <div class="cover big"<?php if (cover_url($user)): ?> data-bg="<?= e(cover_url($user)) ?>"<?php endif; ?>></div>
  <div class="profile-main">
    <img class="avatar xl" src="<?= e(avatar_url($user)) ?>" alt="">
    <div class="profile-info">
      <h1><?= e($user['display_name']) ?></h1>
      <div class="muted">@<?= e($user['username']) ?></div>
      <?php if ($creator): ?>
        <div class="stats muted small">
          <span><?= $counts['posts'] ?> post</span> · <span><?= $counts['photos'] ?> foto</span> · <span><?= $counts['videos'] ?> video</span> · <span>♥ <?= $counts['likes'] ?></span>
        </div>
      <?php endif; ?>
    </div>
    <div class="profile-actions">
      <?php if ($self): ?>
        <a class="btn ghost" href="/settings">Profili redaktə et</a>
      <?php elseif ($creator && $me): ?>
        <?php if ($can_dm): ?><a class="btn ghost" href="/messages/<?= e($user['username']) ?>">💬 Mesaj</a><?php endif; ?>
        <button class="btn ghost" data-open="tip-<?= (int) $user['id'] ?>">💸 Bəxşiş</button>
      <?php endif; ?>
    </div>
  </div>
  <?php if ($user['bio'] !== ''): ?><div class="bio"><?= nl2br_e($user['bio']) ?></div><?php endif; ?>

  <?php if ($creator && !$self): ?>
    <div class="subscribe-box">
      <?php if ($active): ?>
        <div>
          <strong>✓ Abunəsiniz</strong>
          <span class="muted small"><?= format_date($sub['expires_at']) ?> tarixinədək · avto-yeniləmə <?= $sub['auto_renew'] ? 'açıq' : 'bağlı' ?></span>
        </div>
        <form method="post" action="/subscription/<?= (int) $user['id'] ?>/renew">
          <?= csrf_field() ?><input type="hidden" name="on" value="<?= $sub['auto_renew'] ? '0' : '1' ?>">
          <button class="btn ghost sm"><?= $sub['auto_renew'] ? 'Avto-yeniləməni dayandır' : 'Avto-yeniləməni aç' ?></button>
        </form>
      <?php elseif ($me): ?>
        <form method="post" action="/subscribe/<?= (int) $user['id'] ?>" data-confirm="<?= e((int) $user['sub_price_cents'] ? money((int) $user['sub_price_cents']) . ' ödəyib 30 günlük abunə olmaq istəyirsiniz?' : 'Pulsuz abunə olmaq istəyirsiniz?') ?>">
          <?= csrf_field() ?>
          <button class="btn lg block">Abunə ol · <?= (int) $user['sub_price_cents'] ? money((int) $user['sub_price_cents']) . ' / ay' : 'Pulsuz' ?></button>
        </form>
      <?php else: ?>
        <a class="btn lg block" href="/register">Abunə olmaq üçün qeydiyyatdan keçin</a>
      <?php endif; ?>
    </div>
  <?php endif; ?>
  <?php if ($me && !$self): ?>
    <div class="right small"><button class="linklike muted" data-open="report-user-<?= (int) $user['id'] ?>">İstifadəçidən şikayət et</button></div>
    <dialog id="report-user-<?= (int) $user['id'] ?>">
      <form method="post" action="/report" class="form">
        <?= csrf_field() ?><input type="hidden" name="user_id" value="<?= (int) $user['id'] ?>">
        <h3>@<?= e($user['username']) ?> haqqında şikayət</h3>
        <textarea name="reason" required maxlength="500"></textarea>
        <div class="row gap end"><button type="button" class="btn ghost" data-close>Ləğv et</button><button class="btn">Göndər</button></div>
      </form>
    </dialog>
    <?php if ($creator): require __DIR__ . '/partials/tip_dialog.php'; endif; ?>
  <?php endif; ?>
</section>

<?php if ($creator): ?>
  <nav class="tabs">
    <a href="/u/<?= e($user['username']) ?>" class="<?= $tab === 'posts' ? 'active' : '' ?>">Postlar</a>
    <a href="/u/<?= e($user['username']) ?>?tab=media" class="<?= $tab === 'media' ? 'active' : '' ?>">Media</a>
  </nav>
  <?php if ($self): require __DIR__ . '/partials/composer.php'; endif; ?>
  <?php if (!$cards): ?><p class="muted center">Hələ post yoxdur.</p><?php endif; ?>
  <?php foreach ($cards as $card): require __DIR__ . '/partials/post_card.php'; endforeach; ?>
  <?php if ($next): ?><a class="btn ghost block" href="?<?= $tab === 'media' ? 'tab=media&' : '' ?>before=<?= (int) $next ?>">Daha çox</a><?php endif; ?>
<?php endif; ?>
