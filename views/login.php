<section class="card narrow">
  <h1>Daxil ol</h1>
  <form method="post" action="/login" class="form">
    <?= csrf_field() ?>
    <label>Email və ya istifadəçi adı<input name="login" value="<?= old('login') ?>" required autofocus></label>
    <label>Şifrə<input type="password" name="password" required></label>
    <button class="btn block">Daxil ol</button>
  </form>
  <p class="muted center">Hesabınız yoxdur? <a href="/register">Qeydiyyatdan keçin</a></p>
</section>
