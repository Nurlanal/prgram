<form class="card composer form" method="post" action="/post" enctype="multipart/form-data">
  <?= csrf_field() ?>
  <textarea name="body" maxlength="5000" placeholder="Abunəçilərinizlə nə paylaşmaq istəyirsiniz?"></textarea>
  <div class="composer-preview" data-preview></div>
  <div class="row gap wrap-row">
    <label class="btn ghost file-btn">📎 Foto / video<input type="file" name="media[]" accept="image/*,video/mp4,video/webm,video/quicktime" multiple hidden data-preview-input></label>
    <select name="visibility">
      <option value="subscribers">Yalnız abunəçilər</option>
      <option value="public">Hamı (pulsuz)</option>
    </select>
    <label class="inline">PPV $<input name="ppv_price" inputmode="decimal" placeholder="0" class="w80"></label>
    <span class="spacer"></span>
    <button class="btn">Dərc et</button>
  </div>
  <p class="muted small">PPV qiyməti yazsanız, abunəçilər də daxil olmaqla hamı postu ayrıca alaraq açır.</p>
</form>
