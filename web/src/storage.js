// Offline save system. Uses the native Android bridge (SharedPreferences) when
// available, otherwise falls back to localStorage. No network is ever used.

const KEY = 'bakuRunner.save.v1';

const DEFAULTS = {
  coins: 0,
  best: 0,
  bestDistance: 0,
  totalCoins: 0,
  runs: 0,
  ownedChars: ['rashad'],
  selectedChar: 'rashad',
  upgrades: { magnet: 0, x2: 0 },
  boards: 1,
  useBoard: true,
  tutorialDone: false,
  settings: { sound: true, music: true, vibration: true, quality: 'high', showFps: false },
};

function clone(o) { return JSON.parse(JSON.stringify(o)); }

function merge(base, over) {
  if (!over || typeof over !== 'object') return base;
  for (const k of Object.keys(base)) {
    if (!(k in over)) continue;
    const b = base[k], v = over[k];
    if (b && typeof b === 'object' && !Array.isArray(b)) base[k] = merge(b, v);
    else if (typeof v === typeof b || (Array.isArray(b) && Array.isArray(v))) base[k] = v;
  }
  return base;
}

function bridge() {
  return typeof window !== 'undefined' && window.AndroidBridge ? window.AndroidBridge : null;
}

function readRaw() {
  try {
    const b = bridge();
    if (b && b.load) {
      const s = b.load();
      if (s) return s;
    }
  } catch (e) { /* ignore */ }
  try { return window.localStorage.getItem(KEY); } catch (e) { return null; }
}

function writeRaw(str) {
  try { const b = bridge(); if (b && b.save) b.save(str); } catch (e) { /* ignore */ }
  try { window.localStorage.setItem(KEY, str); } catch (e) { /* ignore */ }
}

export const Save = {
  data: clone(DEFAULTS),

  load() {
    const raw = readRaw();
    let parsed = null;
    try { parsed = raw ? JSON.parse(raw) : null; } catch (e) { parsed = null; }
    this.data = merge(clone(DEFAULTS), parsed);
    if (!this.data.ownedChars.includes('rashad')) this.data.ownedChars.push('rashad');
    return this.data;
  },

  save() { writeRaw(JSON.stringify(this.data)); },

  reset() {
    this.data = clone(DEFAULTS);
    this.save();
  },
};
