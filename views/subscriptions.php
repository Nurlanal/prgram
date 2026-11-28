<h1>Abunəliklər</h1>
<section class="card">
  <h3>Abunə olduğum kreatorlar</h3>
  <?php if (!$subs): ?><p class="muted">Hələ abunəliyiniz yoxdur. <a href="/explore">Kreatorları kəşf edin</a>.</p><?php endif; ?>
  <?php foreach ($subs as $s): $active = $s['expires_at'] > now(); ?>
    <div class="list-row">
      <a class="who" href="/u/<?= e($s['username']) ?>">
        <img class="avatar" src="<?= e(avatar_url($s)) ?>" alt="">
        <span><strong><?= e($s['display_name']) ?></strong><br><span class="muted">@<?= e($s['username']) ?></span></span>
      </a>
      <span class="grow muted small">
        <?php if ($active): ?>Aktiv · <?= format_date($s['expires_at']) ?> tarixinədək<?php else: ?>Bitib (<?= format_date($s['expires_at']) ?>)<?php endif; ?>
        · <?= (int) $s['sub_price_cents'] ? money((int) $s['sub_price_cents']) . '/ay' : 'Pulsuz' ?>
      </span>
      <?php if ($active): ?>
        <form method="post" action="/subscription/<?= (int) $s['id'] ?>/renew">
          <?= csrf_field() ?><input type="hidden" name="on" value="<?= $s['auto_renew'] ? '0' : '1' ?>">
          <button class="btn ghost sm"><?= $s['auto_renew'] ? 'Avto-yeniləməni dayandır' : 'Avto-yeniləməni aç' ?></button>
        </form>
      <?php else: ?>
        <form method="post" action="/subscribe/<?= (int) $s['id'] ?>"><?= csrf_field() ?><button class="btn sm">Yenidən abunə ol</button></form>
      <?php endif; ?>
    </div>
  <?php endforeach; ?>
</section>
<?php if (is_creator($me)): ?>
<section class="card">
  <h3>Mənim abunəçilərim (<?= count($fans) ?>)</h3>
  <?php if (!$fans): ?><p class="muted">Hələ abunəçiniz yoxdur.</p><?php endif; ?>
  <?php foreach ($fans as $f): ?>
    <div class="list-row">
      <a class="who" href="/u/<?= e($f['username']) ?>"><img class="avatar" src="<?= e(avatar_url($f)) ?>" alt=""><span><strong><?= e($f['display_name']) ?></strong><br><span class="muted">@<?= e($f['username']) ?></span></span></a>
      <span class="grow muted small"><?= format_date($f['expires_at']) ?> tarixinədək</span>
      <a class="btn ghost sm" href="/messages/<?= e($f['username']) ?>">Mesaj</a>
    </div>
  <?php endforeach; ?>
</section>
<?php endif; ?>
