<h1>Admin panel</h1>
<nav class="tabs">
  <a href="/admin" class="<?= $tab !== 'users' ? 'active' : '' ?>">İcmal</a>
  <a href="/admin?tab=users" class="<?= $tab === 'users' ? 'active' : '' ?>">İstifadəçilər</a>
</nav>
<?php if ($tab === 'users'): ?>
  <form class="search" method="get" action="/admin"><input type="hidden" name="tab" value="users"><input name="q" value="<?= e($term) ?>" placeholder="Ad, email..."><button class="btn">Axtar</button></form>
  <section class="card">
    <table class="table">
      <thead><tr><th>ID</th><th>İstifadəçi</th><th>Email</th><th>Rol</th><th>Balans</th><th></th></tr></thead>
      <tbody>
      <?php foreach ($users as $u): ?>
        <tr>
          <td><?= (int) $u['id'] ?></td>
          <td><a href="/u/<?= e($u['username']) ?>">@<?= e($u['username']) ?></a></td>
          <td class="small"><?= e($u['email']) ?></td>
          <td><?= e($u['role']) ?><?= $u['creator_status'] !== 'none' && $u['role'] !== 'creator' ? ' (' . e($u['creator_status']) . ')' : '' ?></td>
          <td><?= money((int) $u['balance_cents']) ?></td>
          <td><?php if ($u['role'] !== 'admin'): ?>
            <form method="post" action="/admin/user/<?= (int) $u['id'] ?>/<?= $u['banned'] ? 'unban' : 'ban' ?>"><?= csrf_field() ?><button class="btn sm <?= $u['banned'] ? 'ghost' : 'danger' ?>"><?= $u['banned'] ? 'Bloku götür' : 'Blokla' ?></button></form>
          <?php endif; ?></td>
        </tr>
      <?php endforeach; ?>
      </tbody>
    </table>
  </section>
<?php else: ?>
  <div class="grid four">
    <div class="card stat"><span class="muted">İstifadəçi</span><strong><?= $stats['users'] ?></strong></div>
    <div class="card stat"><span class="muted">Kreator</span><strong><?= $stats['creators'] ?></strong></div>
    <div class="card stat"><span class="muted">Aktiv abunə</span><strong><?= $stats['active_subs'] ?></strong></div>
    <div class="card stat"><span class="muted">Komissiya gəliri</span><strong><?= money($stats['fees']) ?></strong><span class="muted small">dövriyyə <?= money($stats['gross']) ?></span></div>
  </div>

  <section class="card">
    <h3>Kreator müraciətləri (<?= count($applications) ?>)</h3>
    <?php if (!$applications): ?><p class="muted">Gözləyən müraciət yoxdur.</p><?php endif; ?>
    <?php foreach ($applications as $a): ?>
      <div class="list-row">
        <span class="grow"><a href="/u/<?= e($a['username']) ?>">@<?= e($a['username']) ?></a> · <?= e($a['legal_name']) ?> · <span class="muted small"><?= e($a['email']) ?> · d.t. <?= e($a['dob']) ?></span></span>
        <form method="post" action="/admin/creator/<?= (int) $a['id'] ?>/approve"><?= csrf_field() ?><button class="btn sm">Təsdiqlə</button></form>
        <form method="post" action="/admin/creator/<?= (int) $a['id'] ?>/reject"><?= csrf_field() ?><button class="btn sm ghost">Rədd et</button></form>
      </div>
    <?php endforeach; ?>
    <p class="muted small">Təsdiqləmədən əvvəl kreatorun şəxsiyyət sənədini və yaşını yoxlayın (KYC).</p>
  </section>

  <section class="card">
    <h3>Şikayətlər (<?= count($reports) ?>)</h3>
    <?php if (!$reports): ?><p class="muted">Açıq şikayət yoxdur.</p><?php endif; ?>
    <?php foreach ($reports as $r): ?>
      <div class="list-row">
        <span class="grow">
          <span class="muted small">@<?= e($r['reporter']) ?> → </span>
          <?php if ($r['post_id']): ?><a href="/post/<?= (int) $r['post_id'] ?>">Post #<?= (int) $r['post_id'] ?></a> (@<?= e($r['post_author']) ?>)<?php else: ?><a href="/u/<?= e($r['target_user']) ?>">@<?= e($r['target_user']) ?></a><?php endif; ?>
          <br><?= e($r['reason']) ?>
        </span>
        <?php if ($r['post_id']): ?><form method="post" action="/admin/report/<?= (int) $r['id'] ?>/remove"><?= csrf_field() ?><button class="btn sm">Postu sil</button></form><?php endif; ?>
        <form method="post" action="/admin/report/<?= (int) $r['id'] ?>/ban" data-confirm="İstifadəçi bloklansın?"><?= csrf_field() ?><button class="btn sm danger">Blokla</button></form>
        <form method="post" action="/admin/report/<?= (int) $r['id'] ?>/dismiss"><?= csrf_field() ?><button class="btn sm ghost">Rədd et</button></form>
      </div>
    <?php endforeach; ?>
  </section>

  <section class="card">
    <h3>Çıxarış sorğuları (<?= count($payouts) ?>)</h3>
    <?php if (!$payouts): ?><p class="muted">Gözləyən sorğu yoxdur.</p><?php endif; ?>
    <?php foreach ($payouts as $t): ?>
      <div class="list-row">
        <span class="grow">@<?= e($t['username']) ?> (<?= e($t['legal_name']) ?>) — <strong><?= money((int) $t['gross_cents']) ?></strong> <span class="muted small"><?= e(format_date($t['created_at'])) ?></span></span>
        <form method="post" action="/admin/payout/<?= (int) $t['id'] ?>/paid"><?= csrf_field() ?><button class="btn sm">Ödənildi</button></form>
        <form method="post" action="/admin/payout/<?= (int) $t['id'] ?>/reject"><?= csrf_field() ?><button class="btn sm ghost">Rədd et</button></form>
      </div>
    <?php endforeach; ?>
  </section>
<?php endif; ?>
