<?php
/** @var array $card */
$p = $card['post']; $c = $card['creator']; $ppv = (int) $p['ppv_price_cents'];
?>
<article class="card post" id="post-<?= (int) $p['id'] ?>">
  <header class="post-head">
    <a href="/u/<?= e($c['username']) ?>" class="who">
      <img class="avatar" src="<?= e(avatar_url($c)) ?>" alt="">
      <span><strong><?= e($c['display_name']) ?></strong><br><span class="muted">@<?= e($c['username']) ?></span></span>
    </a>
    <span class="muted small">
      <?php if ($ppv): ?><span class="tag gold">PPV <?= money($ppv) ?></span><?php elseif ($p['visibility'] === 'public'): ?><span class="tag">Pulsuz</span><?php endif; ?>
      <a href="/post/<?= (int) $p['id'] ?>"><?= e(time_ago($p['created_at'])) ?></a>
    </span>
  </header>

  <?php if ($card['can_view'] || $ppv): ?>
    <?php if ($p['body'] !== ''): ?><div class="post-body"><?= nl2br_e($p['body']) ?></div><?php endif; ?>
  <?php endif; ?>

  <?php if ($card['can_view']): ?>
    <?php if ($card['media']): ?>
      <div class="media-grid n<?= min(count($card['media']), 4) ?>">
        <?php foreach ($card['media'] as $m): ?>
          <?php if ($m['type'] === 'video'): ?>
            <video src="/media/<?= $m['id'] ?>" controls preload="metadata" playsinline controlslist="nodownload"></video>
          <?php else: ?>
            <img src="/media/<?= $m['id'] ?>" alt="" loading="lazy" class="zoomable">
          <?php endif; ?>
        <?php endforeach; ?>
      </div>
    <?php endif; ?>
  <?php else: ?>
    <div class="locked">
      <div class="lock-icon">🔒</div>
      <?php
        $imgs = count(array_filter($card['media'], fn ($m) => $m['type'] === 'image'));
        $vids = count($card['media']) - $imgs;
      ?>
      <p class="muted"><?= $imgs ? $imgs . ' foto ' : '' ?><?= $vids ? $vids . ' video' : '' ?></p>
      <?php if (!$me): ?>
        <a class="btn" href="/login">Baxmaq üçün daxil olun</a>
      <?php elseif ($ppv): ?>
        <form method="post" action="/post/<?= (int) $p['id'] ?>/unlock" data-confirm="<?= e(money($ppv)) ?> ödəyib bu postu açmaq istəyirsiniz?">
          <?= csrf_field() ?><button class="btn">Aç — <?= money($ppv) ?></button>
        </form>
      <?php else: ?>
        <form method="post" action="/subscribe/<?= (int) $c['id'] ?>" data-confirm="<?= e((int) $c['sub_price_cents'] ? money((int) $c['sub_price_cents']) . ' ilə 30 günlük abunə olmaq istəyirsiniz?' : 'Pulsuz abunə olmaq istəyirsiniz?') ?>">
          <?= csrf_field() ?><button class="btn">Abunə ol — <?= (int) $c['sub_price_cents'] ? money((int) $c['sub_price_cents']) . '/ay' : 'Pulsuz' ?></button>
        </form>
      <?php endif; ?>
    </div>
  <?php endif; ?>

  <footer class="post-actions">
    <?php if ($me && $card['can_view']): ?>
      <form method="post" action="/post/<?= (int) $p['id'] ?>/like" class="js-like">
        <?= csrf_field() ?><button class="icon-btn<?= $card['liked'] ? ' active' : '' ?>">♥ <span><?= $card['likes'] ?></span></button>
      </form>
    <?php else: ?>
      <span class="icon-btn">♥ <?= $card['likes'] ?></span>
    <?php endif; ?>
    <a class="icon-btn" href="/post/<?= (int) $p['id'] ?>#comments">💬 <?= $card['comments'] ?></a>
    <?php if ($me && !$card['is_owner'] && is_creator($c)): ?>
      <button class="icon-btn" data-open="tip-<?= (int) $c['id'] ?>">💸 Bəxşiş</button>
    <?php endif; ?>
    <span class="spacer"></span>
    <?php if ($me && ($card['is_owner'] || is_admin($me))): ?>
      <form method="post" action="/post/<?= (int) $p['id'] ?>/delete" data-confirm="Postu silmək istəyirsiniz?">
        <?= csrf_field() ?><button class="icon-btn danger">Sil</button>
      </form>
    <?php elseif ($me): ?>
      <button class="icon-btn muted" data-open="report-<?= (int) $p['id'] ?>">Şikayət</button>
    <?php endif; ?>
  </footer>

  <?php if ($me && !$card['is_owner']): ?>
    <dialog id="report-<?= (int) $p['id'] ?>">
      <form method="post" action="/report" class="form">
        <?= csrf_field() ?><input type="hidden" name="post_id" value="<?= (int) $p['id'] ?>">
        <h3>Postdan şikayət</h3>
        <textarea name="reason" required maxlength="500" placeholder="Səbəbi yazın..."></textarea>
        <div class="row gap end"><button type="button" class="btn ghost" data-close>Ləğv et</button><button class="btn">Göndər</button></div>
      </form>
    </dialog>
    <?php if (is_creator($c)): require __DIR__ . '/tip_dialog.php'; endif; ?>
  <?php endif; ?>
</article>
