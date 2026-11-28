<section class="card narrow">
  <h1>Kreator ol</h1>
  <?php if ($u['creator_status'] === 'approved'): ?>
    <p>Siz artıq kreatorsunuz. <a href="/dashboard">Kreator panelinə keçin</a>.</p>
  <?php elseif ($u['creator_status'] === 'pending'): ?>
    <p>⏳ Müraciətiniz yoxlanılır. Təsdiqləndikdən sonra post paylaşa və qazanc əldə edə biləcəksiniz.</p>
  <?php else: ?>
    <?php if ($u['creator_status'] === 'rejected'): ?><div class="flash error">Əvvəlki müraciətiniz rədd edilib. Məlumatları yoxlayıb yenidən göndərə bilərsiniz.</div><?php endif; ?>
    <p class="muted">Kreator olaraq aylıq abunə qiyməti təyin edə, pullu postlar paylaşa və bəxşiş qəbul edə bilərsiniz. Qazancın <?= 100 - fee_percent() ?>%-i sizindir.</p>
    <form method="post" action="/become-creator" class="form">
      <?= csrf_field() ?>
      <label>Rəsmi ad və soyad (şəxsiyyət vəsiqəsindəki kimi)<input name="legal_name" required maxlength="100"></label>
      <label class="check"><input type="checkbox" name="confirm_age" value="1" required> 18 yaşdan yuxarı olduğumu və şəxsiyyətimi təsdiqləyən sənəd təqdim etməyə hazır olduğumu təsdiq edirəm.</label>
      <label class="check"><input type="checkbox" name="confirm_rights" value="1" required> Paylaşdığım məzmunda görünən hər kəs 18+ yaşdadır, razılıq verib və bunu sübut edən sənədləri saxlayıram.</label>
      <label class="check"><input type="checkbox" name="confirm_rules" value="1" required> <a href="/terms" target="_blank">Məzmun qaydalarını</a> qəbul edirəm.</label>
      <button class="btn block">Müraciət göndər</button>
    </form>
  <?php endif; ?>
</section>
