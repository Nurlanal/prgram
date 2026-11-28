<section class="card chat">
  <header class="row gap">
    <a href="/messages" class="btn ghost sm">←</a>
    <a class="who" href="/u/<?= e($other['username']) ?>"><img class="avatar" src="<?= e(avatar_url($other)) ?>" alt=""><strong><?= e($other['display_name']) ?></strong></a>
  </header>
  <div class="chat-log" data-scroll-end>
    <?php if (!$messages): ?><p class="muted center">Söhbətə başlayın.</p><?php endif; ?>
    <?php foreach ($messages as $m): $mine = (int) $m['sender_id'] === (int) $me['id']; ?>
      <div class="bubble <?= $mine ? 'mine' : 'theirs' ?>">
        <?= nl2br_e($m['body']) ?>
        <span class="time"><?= e(gmdate('d.m H:i', strtotime($m['created_at'] . ' UTC'))) ?><?= $mine && $m['read_at'] ? ' ✓✓' : '' ?></span>
      </div>
    <?php endforeach; ?>
    <span id="end"></span>
  </div>
  <?php if ($can_send): ?>
    <form method="post" action="/messages/<?= e($other['username']) ?>" class="row gap">
      <?= csrf_field() ?>
      <input name="body" class="grow" maxlength="2000" placeholder="Mesaj yazın..." required autofocus>
      <button class="btn">Göndər</button>
    </form>
  <?php else: ?>
    <p class="muted center">Mesaj yazmaq üçün <a href="/u/<?= e($other['username']) ?>">abunə olun</a>.</p>
  <?php endif; ?>
</section>
