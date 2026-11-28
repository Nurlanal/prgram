<a class="creator-card card" href="/u/<?= e($c['username']) ?>">
  <div class="cover"<?php if (cover_url($c)): ?> data-bg="<?= e(cover_url($c)) ?>"<?php endif; ?>></div>
  <img class="avatar lg" src="<?= e(avatar_url($c)) ?>" alt="">
  <strong><?= e($c['display_name']) ?></strong>
  <span class="muted">@<?= e($c['username']) ?></span>
  <span class="price"><?= (int) $c['sub_price_cents'] ? money((int) $c['sub_price_cents']) . ' / ay' : 'Pulsuz' ?></span>
</a>
