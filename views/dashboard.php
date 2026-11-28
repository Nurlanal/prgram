<?php $max = max(1, max($series)); $labels = ['subscription' => 'Abunəliklər', 'tip' => 'Bəxşişlər', 'ppv' => 'Pullu postlar']; ?>
<h1>Kreator paneli</h1>
<div class="grid four">
  <div class="card stat"><span class="muted">Balans</span><strong><?= money($balance) ?></strong></div>
  <div class="card stat"><span class="muted">Aktiv abunəçi</span><strong><?= $subs ?></strong></div>
  <div class="card stat"><span class="muted">Son 30 gün</span><strong><?= money($last30) ?></strong></div>
  <div class="card stat"><span class="muted">Ümumi qazanc</span><strong><?= money($total) ?></strong></div>
</div>
<section class="card">
  <h3>Son 30 günün gəliri</h3>
  <div class="bars" role="img" aria-label="Gündəlik gəlir qrafiki">
    <?php foreach ($series as $day => $cents): ?>
      <div class="bar" data-h="<?= round($cents / $max * 100) ?>" title="<?= e(gmdate('d.m', strtotime($day))) ?>: <?= e(money($cents)) ?>"></div>
    <?php endforeach; ?>
  </div>
  <div class="row between muted small"><span><?= e(gmdate('d.m', strtotime(array_key_first($series)))) ?></span><span>bu gün</span></div>
  <div class="row gap wrap-row">
    <?php foreach ($labels as $k => $label): ?>
      <span class="tag"><?= $label ?>: <?= money((int) ($byType[$k]['net'] ?? 0)) ?> (<?= (int) ($byType[$k]['n'] ?? 0) ?>)</span>
    <?php endforeach; ?>
  </div>
  <p class="muted small">Məbləğlər <?= fee_percent() ?>% platforma komissiyası çıxıldıqdan sonradır.</p>
</section>
<div class="grid two">
  <section class="card">
    <h3>Ən aktiv fanatlar</h3>
    <?php if (!$topFans): ?><p class="muted">Hələ yoxdur.</p><?php endif; ?>
    <?php foreach ($topFans as $f): ?>
      <div class="list-row"><a class="who" href="/u/<?= e($f['username']) ?>"><img class="avatar sm" src="<?= e(avatar_url($f)) ?>" alt=""><span><?= e($f['display_name']) ?></span></a><span class="grow"></span><strong><?= money((int) $f['spent']) ?></strong></div>
    <?php endforeach; ?>
  </section>
  <section class="card">
    <h3>Ən yaxşı postlar</h3>
    <?php if (!$topPosts): ?><p class="muted">Hələ post yoxdur.</p><?php endif; ?>
    <?php foreach ($topPosts as $p): ?>
      <div class="list-row"><a class="grow" href="/post/<?= (int) $p['id'] ?>"><?= e(mb_strimwidth($p['body'] ?: '(media)', 0, 50, '…')) ?></a><span class="muted small">♥ <?= (int) $p['like_count'] ?><?php if ((int) $p['ppv_price_cents']): ?> · <?= (int) $p['unlock_count'] ?> satış<?php endif; ?></span></div>
    <?php endforeach; ?>
  </section>
</div>
