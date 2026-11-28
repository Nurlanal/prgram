<h1>Kəşf et</h1>
<form class="search" method="get" action="/explore">
  <input name="q" value="<?= e($term) ?>" placeholder="Kreator axtar...">
  <button class="btn">Axtar</button>
</form>
<?php if (!$creators): ?>
  <p class="muted">Kreator tapılmadı.</p>
<?php else: ?>
<div class="grid creators">
  <?php foreach ($creators as $c): require __DIR__ . '/partials/creator_card.php'; endforeach; ?>
</div>
<?php endif; ?>
