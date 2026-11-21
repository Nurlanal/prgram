// Low-latency swipe input. The swipe fires as soon as the finger has travelled
// far enough (on touchmove), not on touchend, so controls feel instant.
// Keyboard arrows / WASD / space are supported for desktop testing.

export class Input {
  constructor(target, onAction) {
    this.onAction = onAction;
    this.enabled = false;
    this._start = null;
    this._fired = false;

    const threshold = () => Math.max(24, Math.min(window.innerWidth, window.innerHeight) * 0.055);

    const isUi = (e) => e.target && e.target.closest && e.target.closest('button, .no-swipe');

    target.addEventListener('touchstart', (e) => {
      if (isUi(e)) return;
      const t = e.changedTouches[0];
      this._start = { x: t.clientX, y: t.clientY, id: t.identifier, time: performance.now() };
      this._fired = false;
      if (this.enabled) e.preventDefault();
    }, { passive: false });

    target.addEventListener('touchmove', (e) => {
      if (!this._start || this._fired) { if (this.enabled) e.preventDefault(); return; }
      let t = null;
      for (const ct of e.changedTouches) if (ct.identifier === this._start.id) t = ct;
      if (!t) return;
      const dx = t.clientX - this._start.x, dy = t.clientY - this._start.y;
      if (Math.hypot(dx, dy) >= threshold()) {
        this._fired = true;
        this._emit(dx, dy);
      }
      if (this.enabled) e.preventDefault();
    }, { passive: false });

    const end = (e) => {
      if (this._start && !this._fired && !isUi(e)) {
        const t = e.changedTouches[0];
        const dx = t.clientX - this._start.x, dy = t.clientY - this._start.y;
        if (Math.hypot(dx, dy) >= threshold() * 0.6) this._emit(dx, dy);
        else if (performance.now() - this._start.time < 250) this._tap();
      }
      this._start = null;
    };
    target.addEventListener('touchend', end, { passive: true });
    target.addEventListener('touchcancel', () => { this._start = null; }, { passive: true });

    // Mouse drag (desktop browsers)
    target.addEventListener('mousedown', (e) => {
      if (isUi(e)) return;
      this._start = { x: e.clientX, y: e.clientY, time: performance.now() };
      this._fired = false;
    });
    target.addEventListener('mousemove', (e) => {
      if (!this._start || this._fired) return;
      const dx = e.clientX - this._start.x, dy = e.clientY - this._start.y;
      if (Math.hypot(dx, dy) >= threshold()) { this._fired = true; this._emit(dx, dy); }
    });
    target.addEventListener('mouseup', () => { this._start = null; });

    window.addEventListener('keydown', (e) => {
      const map = {
        ArrowLeft: 'left', KeyA: 'left', ArrowRight: 'right', KeyD: 'right',
        ArrowUp: 'up', KeyW: 'up', Space: 'up', ArrowDown: 'down', KeyS: 'down',
        Escape: 'pause', KeyP: 'pause',
      };
      const a = map[e.code];
      if (!a) return;
      e.preventDefault();
      if (e.repeat) return;
      if (a === 'pause') this.onAction('pause');
      else if (this.enabled) this.onAction(a);
    });
  }

  _emit(dx, dy) {
    if (!this.enabled) return;
    if (Math.abs(dx) > Math.abs(dy)) this.onAction(dx < 0 ? 'left' : 'right');
    else this.onAction(dy < 0 ? 'up' : 'down');
  }

  _tap() { if (this.enabled) this.onAction('tap'); }
}
