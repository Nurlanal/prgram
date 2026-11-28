// Small progressive enhancements (no inline scripts — CSP friendly).
document.addEventListener('DOMContentLoaded', () => {
  // Background images from data-bg
  document.querySelectorAll('[data-bg]').forEach((el) => {
    el.style.backgroundImage = `url("${el.dataset.bg}")`;
  });

  // Revenue bars heights
  document.querySelectorAll('.bar[data-h]').forEach((el) => {
    el.style.height = Math.max(2, Number(el.dataset.h)) + '%';
  });

  // Confirm dialogs for forms
  document.querySelectorAll('form[data-confirm]').forEach((f) => {
    f.addEventListener('submit', (e) => {
      if (!confirm(f.dataset.confirm)) e.preventDefault();
    });
  });

  // <dialog> open / close
  document.addEventListener('click', (e) => {
    const opener = e.target.closest('[data-open]');
    if (opener) {
      const d = document.getElementById(opener.dataset.open);
      if (d) d.showModal();
    }
    if (e.target.closest('[data-close]')) e.target.closest('dialog')?.close();
    const chip = e.target.closest('[data-amount]');
    if (chip) chip.closest('form').querySelector('[name=amount]').value = chip.dataset.amount;
    if (e.target.tagName === 'DIALOG') e.target.close(); // click on backdrop
  });

  // AJAX like toggle
  document.querySelectorAll('form.js-like').forEach((f) => {
    f.addEventListener('submit', async (e) => {
      e.preventDefault();
      const res = await fetch(f.action, { method: 'POST', body: new FormData(f), headers: { Accept: 'application/json' } });
      if (!res.ok) return f.submit();
      const data = await res.json();
      const btn = f.querySelector('button');
      btn.classList.toggle('active', data.liked);
      btn.querySelector('span').textContent = data.likes;
    });
  });

  // Upload preview in composer
  document.querySelectorAll('[data-preview-input]').forEach((input) => {
    const box = input.closest('form').querySelector('[data-preview]');
    input.addEventListener('change', () => {
      box.innerHTML = '';
      [...input.files].slice(0, 10).forEach((file) => {
        const url = URL.createObjectURL(file);
        const el = document.createElement(file.type.startsWith('video/') ? 'video' : 'img');
        el.src = url;
        box.appendChild(el);
      });
    });
  });

  // Chat: scroll to latest message
  document.querySelectorAll('[data-scroll-end]').forEach((el) => { el.scrollTop = el.scrollHeight; });

  // Image lightbox
  document.addEventListener('click', (e) => {
    const img = e.target.closest('img.zoomable');
    if (!img) return;
    const d = document.createElement('dialog');
    d.className = 'lightbox';
    const big = document.createElement('img');
    big.src = img.src;
    d.appendChild(big);
    d.addEventListener('click', () => d.remove());
    document.body.appendChild(d);
    d.showModal();
  });

  // Discourage casual right-click saving of paid media
  document.addEventListener('contextmenu', (e) => {
    if (e.target.closest('.media-grid')) e.preventDefault();
  });
});
