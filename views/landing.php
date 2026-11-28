<section class="hero">
  <h1>Sevdiyiniz kreatorları <span class="accent">birbaşa</span> dəstəkləyin</h1>
  <p>Eksklüziv foto və videolar, şəxsi mesajlar, bəxşişlər. Kreatorlar qazancın <?= 100 - fee_percent() ?>%-ni saxlayır.</p>
  <div class="row gap">
    <a class="btn lg" href="/register">Pulsuz qoşul</a>
    <a class="btn lg ghost" href="/explore">Kreatorlara bax</a>
  </div>
</section>
<?php if ($featured): ?>
<h2>Yeni kreatorlar</h2>
<div class="grid creators">
  <?php foreach ($featured as $c): require __DIR__ . '/partials/creator_card.php'; endforeach; ?>
</div>
<?php endif; ?>
<section class="features">
  <div class="card"><h3>🔒 Abunəlik</h3><p>Aylıq abunə ilə kreatorun bütün eksklüziv postlarına giriş.</p></div>
  <div class="card"><h3>💎 Pullu postlar</h3><p>Xüsusi məzmunu tək ödənişlə açın (PPV).</p></div>
  <div class="card"><h3>💬 Mesajlar</h3><p>Abunə olduğunuz kreatorla birbaşa yazışın, bəxşiş göndərin.</p></div>
</section>
