<section class="card narrow">
  <h1>Qeydiyyat</h1>
  <form method="post" action="/register" class="form">
    <?= csrf_field() ?>
    <label>Email<input type="email" name="email" value="<?= old('email') ?>" required></label>
    <label>İstifadəçi adı<input name="username" value="<?= old('username') ?>" pattern="[A-Za-z0-9_]{3,24}" required></label>
    <label>Görünən ad<input name="display_name" value="<?= old('display_name') ?>" maxlength="50"></label>
    <label>Doğum tarixi<input type="date" name="dob" value="<?= old('dob') ?>" max="<?= gmdate('Y-m-d', strtotime('-18 years')) ?>" required></label>
    <label>Şifrə<input type="password" name="password" minlength="8" required></label>
    <label class="check"><input type="checkbox" name="accept_terms" value="1" required> 18 yaşdan yuxarı olduğumu təsdiq edirəm və <a href="/terms" target="_blank">istifadə şərtlərini</a> qəbul edirəm.</label>
    <button class="btn block">Hesab yarat</button>
  </form>
  <p class="muted center">Hesabınız var? <a href="/login">Daxil olun</a></p>
</section>
