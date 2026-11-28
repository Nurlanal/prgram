<h1>Mesajlar</h1>
<section class="card">
  <?php if (!$threads): ?><p class="muted">Hələ mesajınız yoxdur. Abunə olduğunuz kreatorun profilindən yazışmağa başlaya bilərsiniz.</p><?php endif; ?>
  <?php foreach ($threads as $t): ?>
    <a class="list-row thread-row<?= $t['unread'] ? ' unread' : '' ?>" href="/messages/<?= e($t['username']) ?>">
      <img class="avatar" src="<?= e(avatar_url($t)) ?>" alt="">
      <span class="grow"><strong><?= e($t['display_name']) ?></strong> <span class="muted small">@<?= e($t['username']) ?></span><br>
        <span class="muted"><?= (int) $t['sender_id'] === (int) $me['id'] ? 'Siz: ' : '' ?><?= e(mb_strimwidth($t['body'], 0, 70, '…')) ?></span></span>
      <span class="muted small"><?= e(time_ago($t['created_at'])) ?></span>
      <?php if ($t['unread']): ?><span class="badge"><?= (int) $t['unread'] ?></span><?php endif; ?>
    </a>
  <?php endforeach; ?>
</section>
