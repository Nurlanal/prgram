<h1>Tənzimləmələr</h1>
<div class="grid two">
<section class="card">
  <h3>Profil</h3>
  <form method="post" action="/settings/profile" class="form">
    <?= csrf_field() ?>
    <label>Görünən ad<input name="display_name" value="<?= e($u['display_name']) ?>" maxlength="50" required></label>
    <label>Bio<textarea name="bio" maxlength="1000"><?= e($u['bio']) ?></textarea></label>
    <?php if (is_creator($u)): ?>
      <label>Aylıq abunə qiyməti ($) <span class="muted small">— 0 və ya boş = pulsuz, 3.00 – 1000.00</span>
        <input name="sub_price" inputmode="decimal" value="<?= (int) $u['sub_price_cents'] ? number_format($u['sub_price_cents'] / 100, 2, '.', '') : '' ?>"></label>
    <?php endif; ?>
    <button class="btn">Yadda saxla</button>
  </form>
</section>
<section class="card">
  <h3>Şəkillər</h3>
  <form method="post" action="/settings/images" class="form" enctype="multipart/form-data">
    <?= csrf_field() ?>
    <div class="row gap"><img class="avatar lg" src="<?= e(avatar_url($u)) ?>" alt="">
      <label class="grow">Profil şəkli<input type="file" name="avatar" accept="image/*"></label></div>
    <?php if ($u['avatar']): ?><label class="check"><input type="checkbox" name="remove_avatar" value="1"> Profil şəklini sil</label><?php endif; ?>
    <label>Örtük şəkli<input type="file" name="cover" accept="image/*"></label>
    <?php if ($u['cover']): ?><label class="check"><input type="checkbox" name="remove_cover" value="1"> Örtük şəklini sil</label><?php endif; ?>
    <button class="btn">Yüklə</button>
  </form>
  <h3>Şifrə</h3>
  <form method="post" action="/settings/password" class="form">
    <?= csrf_field() ?>
    <label>Cari şifrə<input type="password" name="current" required></label>
    <label>Yeni şifrə<input type="password" name="next" minlength="8" required></label>
    <button class="btn">Dəyiş</button>
  </form>
</section>
</div>
