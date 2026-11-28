<?php require __DIR__ . '/partials/post_card.php'; $p = $card['post']; ?>
<?php if ($card['can_view']): ?>
<section class="card" id="comments">
  <h3>Şərhlər (<?= count($comments) ?>)</h3>
  <?php foreach ($comments as $cm): ?>
    <div class="comment">
      <img class="avatar sm" src="<?= e(avatar_url($cm)) ?>" alt="">
      <div class="grow">
        <a href="/u/<?= e($cm['username']) ?>"><strong><?= e($cm['display_name']) ?></strong></a>
        <span class="muted small"><?= e(time_ago($cm['created_at'])) ?></span>
        <div><?= nl2br_e($cm['body']) ?></div>
      </div>
      <?php if ($me && ((int) $me['id'] === (int) $cm['user_id'] || $card['is_owner'] || is_admin($me))): ?>
        <form method="post" action="/comment/<?= (int) $cm['id'] ?>/delete"><?= csrf_field() ?><button class="icon-btn danger small">✕</button></form>
      <?php endif; ?>
    </div>
  <?php endforeach; ?>
  <?php if ($me): ?>
    <form method="post" action="/post/<?= (int) $p['id'] ?>/comment" class="row gap">
      <?= csrf_field() ?>
      <input name="body" class="grow" maxlength="1000" placeholder="Şərh yazın..." required>
      <button class="btn">Göndər</button>
    </form>
  <?php endif; ?>
</section>
<?php endif; ?>
