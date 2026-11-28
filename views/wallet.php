<?php
$labels = ['topup' => 'Balans artırma', 'subscription' => 'Abunəlik', 'tip' => 'Bəxşiş', 'ppv' => 'Pullu post', 'payout' => 'Çıxarış'];
$statusLabels = ['pending' => 'gözləmədə', 'paid' => 'ödənildi', 'rejected' => 'rədd edildi'];
?>
<h1>Cüzdan</h1>
<div class="grid two">
  <section class="card">
    <div class="muted">Balans</div>
    <div class="big-number"><?= money($balance) ?></div>
    <?php if (config('demo_payments')): ?>
      <form method="post" action="/wallet/topup" class="row gap">
        <?= csrf_field() ?>
        <input name="amount" inputmode="decimal" placeholder="25.00" required class="grow">
        <button class="btn">Balansı artır</button>
      </form>
      <p class="muted small">⚠ Demo rejim: real ödəniş alınmır. Produksiyada ödəniş provayderi qoşun (bax README).</p>
    <?php else: ?>
      <p class="muted">Balans artırma tezliklə.</p>
    <?php endif; ?>
  </section>
  <?php if (is_creator($me)): ?>
  <section class="card">
    <div class="muted">Pul çıxarmaq</div>
    <form method="post" action="/wallet/payout" class="row gap" data-confirm="Çıxarış sorğusu göndərilsin?">
      <?= csrf_field() ?>
      <input name="amount" inputmode="decimal" placeholder="Min. 20.00" required class="grow">
      <button class="btn">Sorğu göndər</button>
    </form>
    <p class="muted small">Sorğu admin tərəfindən emal olunur və bank hesabınıza köçürülür.</p>
  </section>
  <?php endif; ?>
</div>
<section class="card">
  <h3>Əməliyyatlar</h3>
  <?php if (!$tx): ?><p class="muted">Əməliyyat yoxdur.</p><?php else: ?>
  <table class="table">
    <thead><tr><th>Tarix</th><th>Növ</th><th>Qarşı tərəf</th><th class="right">Məbləğ</th></tr></thead>
    <tbody>
    <?php foreach ($tx as $t):
        $incoming = (int) $t['to_user'] === (int) $me['id'];
        $amount = $incoming && $t['type'] !== 'topup' ? (int) $t['net_cents'] : (int) $t['gross_cents'];
        $other = $incoming ? $t['from_username'] : $t['to_username'];
    ?>
      <tr>
        <td class="muted small"><?= e(gmdate('d.m.Y H:i', strtotime($t['created_at'] . ' UTC'))) ?></td>
        <td><?= e($labels[$t['type']] ?? $t['type']) ?><?php if ($t['type'] === 'payout'): ?> <span class="tag"><?= e($statusLabels[$t['status']] ?? $t['status']) ?></span><?php endif; ?>
          <?php if ($t['note']): ?><div class="muted small"><?= e($t['note']) ?></div><?php endif; ?></td>
        <td><?= $other ? '<a href="/u/' . e($other) . '">@' . e($other) . '</a>' : '—' ?></td>
        <td class="right <?= $incoming ? 'pos' : 'neg' ?>"><?= $incoming ? '+' : '−' ?><?= money($amount) ?>
          <?php if ($incoming && (int) $t['fee_cents']): ?><div class="muted small">komissiya <?= money((int) $t['fee_cents']) ?></div><?php endif; ?></td>
      </tr>
    <?php endforeach; ?>
    </tbody>
  </table>
  <?php endif; ?>
</section>
