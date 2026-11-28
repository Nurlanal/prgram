<div class="layout-2">
  <div>
    <?php if (is_creator($me)): require __DIR__ . '/partials/composer.php'; endif; ?>
    <?php if (!$cards): ?>
      <div class="card center">
        <h2>Lentiniz boşdur</h2>
        <p class="muted">Kreatorlara abunə olun, onların postları burada görünəcək.</p>
        <a class="btn" href="/explore">Kreatorları kəşf et</a>
      </div>
    <?php endif; ?>
    <?php foreach ($cards as $card): require __DIR__ . '/partials/post_card.php'; endforeach; ?>
    <?php if ($next): ?><a class="btn ghost block" href="/?before=<?= (int) $next ?>">Daha çox</a><?php endif; ?>
  </div>
  <aside>
    <?php if ($suggested): ?>
      <div class="card">
        <h3>Tövsiyə olunanlar</h3>
        <?php foreach ($suggested as $s): ?>
          <a class="who row-link" href="/u/<?= e($s['username']) ?>">
            <img class="avatar" src="<?= e(avatar_url($s)) ?>" alt="">
            <span><strong><?= e($s['display_name']) ?></strong><br><span class="muted">@<?= e($s['username']) ?></span></span>
          </a>
        <?php endforeach; ?>
      </div>
    <?php endif; ?>
    <?php if (!is_creator($me) && !is_admin($me)): ?>
      <div class="card"><h3>Kreator olun</h3><p class="muted">Məzmununuzu paylaşın və qazanın.</p><a class="btn block" href="/become-creator">Başla</a></div>
    <?php endif; ?>
  </aside>
</div>
