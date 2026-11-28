<?php if (!empty($GLOBALS['tip_rendered'][(int) $c['id']])) { return; } $GLOBALS['tip_rendered'][(int) $c['id']] = true; ?>
<dialog id="tip-<?= (int) $c['id'] ?>">
  <form method="post" action="/tip/<?= (int) $c['id'] ?>" class="form">
    <?= csrf_field() ?>
    <h3>@<?= e($c['username']) ?> üçün bəxşiş</h3>
    <div class="row gap chips">
      <?php foreach ([5, 10, 20, 50] as $amt): ?><button type="button" class="chip" data-amount="<?= $amt ?>">$<?= $amt ?></button><?php endforeach; ?>
    </div>
    <label>Məbləğ ($)<input name="amount" inputmode="decimal" required placeholder="10.00"></label>
    <label>Mesaj (istəyə görə)<input name="note" maxlength="200"></label>
    <div class="row gap end"><button type="button" class="btn ghost" data-close>Ləğv et</button><button class="btn">Göndər</button></div>
  </form>
</dialog>
