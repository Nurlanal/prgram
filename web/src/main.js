// Entry point: boots the save system, UI and 3D game, and exposes the
// small API used by the Android wrapper (pause on app switch, back button).
import { Save } from './storage.js';
import { Audio } from './audio.js';
import { Input } from './input.js';
import { UI } from './ui.js';
import { Game } from './game/Game.js';

function boot() {
  Save.load();
  const ui = new UI();
  let game;
  try {
    game = new Game(document.getElementById('gl'), ui);
  } catch (e) {
    document.getElementById('loading').innerHTML = '<p>3D qrafika (WebGL) bu cihazda işləmir.</p>';
    console.error(e);
    return;
  }
  ui.attach(game);
  ui.applySettings();
  ui.showMenu();

  const input = new Input(document.getElementById('app'), (a) => {
    if (a === 'pause') {
      if (game.state === 'paused') { ui.hideOverlay(); game.resume(); } else game.pause();
      return;
    }
    game.action(a);
  });
  // swipes only steer the runner while a run is active
  const syncInput = () => { input.enabled = game.state === 'playing'; requestAnimationFrame(syncInput); };
  syncInput();

  document.addEventListener('visibilitychange', () => {
    if (document.hidden) { game.pause(); Audio.suspend(); }
  });
  // First tap anywhere unlocks audio (WebView/browser autoplay policy)
  window.addEventListener('touchstart', () => Audio.unlock(), { once: true, passive: true });
  window.addEventListener('mousedown', () => Audio.unlock(), { once: true });

  window.BakuRunner = {
    onAppPause() { game.pause(); Audio.suspend(); },
    onAppResume() { /* stays paused until the player taps Continue */ },
    onBack() { return game.onBack(); },
    game,
  };
  document.getElementById('loading').classList.add('hidden');
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
else boot();
