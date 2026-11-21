// DOM user interface: menus, HUD, pause, game over, shop screens.
import { Save } from './storage.js';
import { Audio } from './audio.js';
import { CHARACTERS } from './game/characters.js';
import { POWERUP } from './config.js';

const $ = (sel, root = document) => root.querySelector(sel);
const fmt = (n) => String(Math.floor(n)).padStart(6, '0');
const BOARD_PRICE = 300;
const MAX_LEVEL = 5;
const upgradePrice = (lvl) => 250 * (lvl + 1) * (lvl + 1);

export class UI {
  constructor() {
    this.root = $('#ui');
    this.screen = 'menu';
    this._hud = {};
    this._charIndex = 0;
    this._bind();
  }

  attach(game) { this.game = game; }

  // ------------------------------------------------------------ helpers
  _show(id) {
    for (const el of this.root.querySelectorAll('.screen')) el.classList.toggle('active', el.id === id);
    this.screen = id;
  }

  hideOverlay() { this._show('hud'); }

  vibrate(ms) {
    if (!Save.data.settings.vibration) return;
    try {
      if (window.AndroidBridge && window.AndroidBridge.vibrate) window.AndroidBridge.vibrate(ms);
      else if (navigator.vibrate) navigator.vibrate(ms);
    } catch (e) { /* ignore */ }
  }

  click() { Audio.unlock(); Audio.play('click'); }

  _bind() {
    this.root.addEventListener('click', (e) => {
      const b = e.target.closest('[data-act]');
      if (!b) return;
      this.click();
      this._act(b.dataset.act, b);
    });
  }

  _act(a, el) {
    const g = this.game;
    switch (a) {
      case 'play': g.startRun(); break;
      case 'characters': this.openChars(); break;
      case 'garage': this.openGarage(); break;
      case 'settings': this.openSettings(); break;
      case 'menu': g.showcase(false); this.showMenu(); break;
      case 'to-menu': g.toMenu(); this.showMenu(); break;
      case 'restart': g.toMenu(); g.startRun(); break;
      case 'pause': g.pause(); break;
      case 'resume': this._show('hud'); g.resume(); break;
      case 'sound': this.toggleSetting('sound'); break;
      case 'music': this.toggleSetting('music'); break;
      case 'vibration': this.toggleSetting('vibration'); break;
      case 'fps': this.toggleSetting('showFps'); break;
      case 'quality': {
        const s = Save.data.settings;
        s.quality = s.quality === 'high' ? 'low' : 'high';
        Save.save(); g.setQuality(s.quality); this.refreshSettings();
        break;
      }
      case 'reset': this.confirm('Bütün irəliləyiş (coin, rekord, personajlar) silinsin?', () => {
        Save.reset(); g.setCharacter(Save.data.selectedChar); this.applySettings(); this.refreshSettings(); this.toast('Sıfırlandı');
      }); break;
      case 'char-prev': this._charIndex = (this._charIndex + CHARACTERS.length - 1) % CHARACTERS.length; this.refreshChars(); break;
      case 'char-next': this._charIndex = (this._charIndex + 1) % CHARACTERS.length; this.refreshChars(); break;
      case 'char-select': this.selectChar(); break;
      case 'upgrade': this.buyUpgrade(el.dataset.item); break;
      case 'buy-board': this.buyBoard(); break;
      case 'use-board': Save.data.useBoard = !Save.data.useBoard; Save.save(); this.refreshGarage(); break;
      case 'confirm-yes': $('#confirm').classList.remove('active'); if (this._onConfirm) this._onConfirm(); break;
      case 'confirm-no': $('#confirm').classList.remove('active'); break;
    }
  }

  back() {
    if ($('#confirm').classList.contains('active')) { $('#confirm').classList.remove('active'); return 'handled'; }
    if (this.screen === 'chars' || this.screen === 'garage' || this.screen === 'settings') { this._act('menu'); return 'handled'; }
    if (this.screen === 'gameover') { this._act('to-menu'); return 'handled'; }
    if (this.screen === 'menu') return 'exit';
    return 'handled';
  }

  confirm(text, cb) {
    $('#confirm-text').textContent = text;
    this._onConfirm = cb;
    $('#confirm').classList.add('active');
  }

  // ------------------------------------------------------------ settings
  applySettings() {
    const s = Save.data.settings;
    Audio.setSfx(s.sound);
    Audio.setMusic(s.music);
    if (!s.music) Audio.stopMusic();
    else if (this.game && this.game.state === 'playing') Audio.startMusic();
    $('#fps').style.display = s.showFps ? 'block' : 'none';
    for (const b of this.root.querySelectorAll('[data-act="sound"]')) b.classList.toggle('off', !s.sound);
  }

  toggleSetting(k) {
    const s = Save.data.settings;
    s[k] = !s[k];
    Save.save();
    this.applySettings();
    this.refreshSettings();
  }

  openSettings() { this.refreshSettings(); this._show('settings'); }

  refreshSettings() {
    const s = Save.data.settings;
    const set = (id, on, txt) => { const el = $('#' + id); el.classList.toggle('on', on); el.textContent = txt || (on ? 'AÇIQ' : 'BAĞLI'); };
    set('set-sound', s.sound);
    set('set-music', s.music);
    set('set-vibration', s.vibration);
    set('set-fps', s.showFps);
    set('set-quality', s.quality === 'high', s.quality === 'high' ? 'YÜKSƏK' : 'AŞAĞI');
  }

  // ------------------------------------------------------------ menu
  showMenu() {
    const d = Save.data;
    $('#menu-best').textContent = fmt(d.best);
    $('#menu-coins').textContent = d.coins;
    $('#menu-dist').textContent = d.bestDistance + ' m';
    this._show('menu');
  }

  // ------------------------------------------------------------ characters
  openChars() {
    this._charIndex = Math.max(0, CHARACTERS.findIndex((c) => c.id === Save.data.selectedChar));
    this.game.showcase(true);
    this.refreshChars();
    this._show('chars');
  }

  refreshChars() {
    const c = CHARACTERS[this._charIndex], d = Save.data;
    this.game.setCharacter(c.id);
    $('#char-name').textContent = c.name;
    $('#char-desc').textContent = c.desc;
    $('#chars-coins').textContent = d.coins;
    $('#char-count').textContent = `${this._charIndex + 1} / ${CHARACTERS.length}`;
    const btn = $('#char-btn');
    const owned = d.ownedChars.includes(c.id);
    btn.classList.remove('disabled', 'selected');
    if (d.selectedChar === c.id) { btn.textContent = 'SEÇİLİB ✓'; btn.classList.add('selected'); }
    else if (owned) btn.textContent = 'SEÇ';
    else { btn.innerHTML = `AL  <span class="coin-ico"></span> ${c.price}`; if (d.coins < c.price) btn.classList.add('disabled'); }
  }

  selectChar() {
    const c = CHARACTERS[this._charIndex], d = Save.data;
    if (!d.ownedChars.includes(c.id)) {
      if (d.coins < c.price) { Audio.play('error'); this.toast('Kifayət qədər coin yoxdur'); return; }
      d.coins -= c.price;
      d.ownedChars.push(c.id);
      Audio.play('buy');
    }
    d.selectedChar = c.id;
    Save.save();
    this.refreshChars();
  }

  // ------------------------------------------------------------ garage
  openGarage() { this.refreshGarage(); this._show('garage'); }

  refreshGarage() {
    const d = Save.data;
    $('#garage-coins').textContent = d.coins;
    for (const item of ['magnet', 'x2']) {
      const lvl = d.upgrades[item];
      const card = $(`#g-${item}`);
      card.querySelector('.lvl').innerHTML = Array.from({ length: MAX_LEVEL }, (_, i) => `<i class="${i < lvl ? 'f' : ''}"></i>`).join('');
      const base = item === 'magnet' ? POWERUP.magnetBase : POWERUP.x2Base;
      card.querySelector('.dur').textContent = `${base + lvl * POWERUP.perLevel} san`;
      const b = card.querySelector('button');
      if (lvl >= MAX_LEVEL) { b.textContent = 'MAKS'; b.classList.add('disabled'); }
      else { b.innerHTML = `<span class="coin-ico"></span> ${upgradePrice(lvl)}`; b.classList.toggle('disabled', d.coins < upgradePrice(lvl)); }
    }
    $('#g-board-count').textContent = d.boards;
    $('#g-board-buy').innerHTML = `<span class="coin-ico"></span> ${BOARD_PRICE}`;
    $('#g-board-buy').classList.toggle('disabled', d.coins < BOARD_PRICE);
    const ub = $('#g-board-use');
    ub.textContent = d.useBoard ? 'AÇIQ' : 'BAĞLI';
    ub.classList.toggle('on', d.useBoard);
  }

  buyUpgrade(item) {
    const d = Save.data, lvl = d.upgrades[item];
    if (lvl >= MAX_LEVEL) return;
    const price = upgradePrice(lvl);
    if (d.coins < price) { Audio.play('error'); this.toast('Kifayət qədər coin yoxdur'); return; }
    d.coins -= price; d.upgrades[item] = lvl + 1; Save.save();
    Audio.play('buy');
    this.refreshGarage();
  }

  buyBoard() {
    const d = Save.data;
    if (d.coins < BOARD_PRICE) { Audio.play('error'); this.toast('Kifayət qədər coin yoxdur'); return; }
    d.coins -= BOARD_PRICE; d.boards += 1; Save.save();
    Audio.play('buy');
    this.refreshGarage();
  }

  // ------------------------------------------------------------ HUD
  showHud(run) {
    this._hud = {};
    $('#pw-magnet').style.display = 'none';
    $('#pw-x2').style.display = 'none';
    $('#pw-shield').style.display = 'none';
    this.hint('');
    this._show('hud');
    this.updateHud(run, Save.data.best);
  }

  _set(key, el, val) {
    if (this._hud[key] === val) return;
    this._hud[key] = val;
    el.textContent = val;
  }

  updateHud(r, best) {
    this._set('score', $('#hud-score'), fmt(r.score));
    this._set('best', $('#hud-best'), 'BEST ' + fmt(Math.max(best, r.score)));
    this._set('coins', $('#hud-coins'), String(r.coins));
    this._set('dist', $('#hud-dist'), Math.floor(r.distance) + ' m');
    this._set('speed', $('#hud-speed'), Math.round(r.speed * 3.6) + ' km/saat');
    const mult = r.x2T > 0 ? 'x2' : 'x1';
    if (this._hud.mult !== mult) { this._hud.mult = mult; const m = $('#hud-mult'); m.textContent = mult; m.classList.toggle('hot', r.x2T > 0); }
    this._bar('magnet', r.magnetT, r.magnetMax || 1);
    this._bar('x2', r.x2T, r.x2Max || 1);
    this._bar('shield', r.shieldT, POWERUP.shieldTime);
  }

  _bar(k, t, max) {
    const el = $('#pw-' + k);
    const vis = t > 0;
    if (this._hud['v' + k] !== vis) { this._hud['v' + k] = vis; el.style.display = vis ? 'flex' : 'none'; }
    if (vis) {
      const pct = Math.round((t / max) * 100);
      if (this._hud['p' + k] !== pct) { this._hud['p' + k] = pct; el.querySelector('i').style.width = pct + '%'; }
    }
  }

  coinPulse() {
    const el = $('#hud-coinbox');
    el.classList.remove('pulse'); void el.offsetWidth; el.classList.add('pulse');
  }

  fps(v) { $('#fps').textContent = v + ' FPS'; }

  banner(text) {
    const el = $('#banner');
    el.textContent = text;
    el.classList.remove('show'); void el.offsetWidth; el.classList.add('show');
  }

  toast(text) {
    const el = $('#toast');
    el.textContent = text;
    el.classList.remove('show'); void el.offsetWidth; el.classList.add('show');
  }

  hint(text) {
    const el = $('#hint');
    el.textContent = text;
    el.style.display = text ? 'block' : 'none';
  }

  countdown(n) {
    const el = $('#countdown');
    if (n <= 0) { el.style.display = 'none'; return; }
    el.style.display = 'flex';
    el.textContent = n;
    el.classList.remove('pop'); void el.offsetWidth; el.classList.add('pop');
  }

  showPause() { this._show('pause'); }

  showGameOver(res) {
    $('#go-score').textContent = fmt(res.score);
    $('#go-best').textContent = fmt(res.best);
    $('#go-coins').textContent = '+' + res.coins;
    $('#go-dist').textContent = res.distance + ' m';
    $('#go-total').textContent = Save.data.coins;
    $('#go-newbest').style.display = res.isBest ? 'block' : 'none';
    this._show('gameover');
  }
}
