// Procedural audio: every sound effect and the music loop are synthesised with
// WebAudio at runtime, so no audio files or network are needed.

class AudioSystem {
  constructor() {
    this.ctx = null;
    this.sfxOn = true;
    this.musicOn = true;
    this.musicPlaying = false;
    this._step = 0;
    this._nextTime = 0;
    this._timer = null;
    this._noise = null;
  }

  // Must be called from a user gesture (first tap) to unlock audio.
  unlock() {
    if (!this.ctx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      this.ctx = new AC();
      this.master = this.ctx.createGain();
      this.master.gain.value = 0.8;
      this.master.connect(this.ctx.destination);
      this.sfx = this.ctx.createGain();
      this.sfx.gain.value = this.sfxOn ? 0.9 : 0;
      this.sfx.connect(this.master);
      this.music = this.ctx.createGain();
      this.music.gain.value = this.musicOn ? 0.32 : 0;
      this.music.connect(this.master);
      const len = this.ctx.sampleRate * 1;
      this._noise = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
      const d = this._noise.getChannelData(0);
      for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
    }
    if (this.ctx.state === 'suspended') this.ctx.resume();
  }

  suspend() { if (this.ctx && this.ctx.state === 'running') this.ctx.suspend(); }
  resume() { if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume(); }

  setSfx(on) { this.sfxOn = on; if (this.sfx) this.sfx.gain.value = on ? 0.9 : 0; }
  setMusic(on) {
    this.musicOn = on;
    if (this.music) this.music.gain.value = on ? 0.32 : 0;
  }

  _tone(type, f0, f1, dur, vol, when = 0, dest = this.sfx) {
    const c = this.ctx, t = c.currentTime + when;
    const o = c.createOscillator(), g = c.createGain();
    o.type = type;
    o.frequency.setValueAtTime(f0, t);
    if (f1 !== f0) o.frequency.exponentialRampToValueAtTime(Math.max(1, f1), t + dur);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(vol, t + 0.008);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(dest);
    o.start(t); o.stop(t + dur + 0.02);
  }

  _noiseBurst(dur, vol, freq, when = 0, q = 1, dest = this.sfx, type = 'bandpass') {
    const c = this.ctx, t = c.currentTime + when;
    const s = c.createBufferSource(); s.buffer = this._noise;
    const f = c.createBiquadFilter(); f.type = type; f.frequency.value = freq; f.Q.value = q;
    const g = c.createGain();
    g.gain.setValueAtTime(vol, t);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    s.connect(f); f.connect(g); g.connect(dest);
    s.start(t, Math.random() * 0.5); s.stop(t + dur + 0.02);
  }

  play(name) {
    if (!this.ctx || !this.sfxOn || this.ctx.state !== 'running') return;
    switch (name) {
      case 'coin':
        this._tone('square', 988, 988, 0.06, 0.08);
        this._tone('square', 1319, 1319, 0.16, 0.08, 0.055);
        break;
      case 'jump':
        this._tone('triangle', 300, 760, 0.2, 0.25);
        break;
      case 'slide':
        this._noiseBurst(0.3, 0.35, 1400, 0, 0.8);
        break;
      case 'lane':
        this._noiseBurst(0.12, 0.18, 2600, 0, 1.2);
        break;
      case 'bump':
        this._tone('sine', 180, 60, 0.25, 0.5);
        this._noiseBurst(0.15, 0.3, 600, 0, 1);
        break;
      case 'crash':
        this._noiseBurst(0.6, 0.9, 500, 0, 0.5, this.sfx, 'lowpass');
        this._tone('sine', 140, 40, 0.5, 0.8);
        this._tone('square', 220, 110, 0.35, 0.15, 0.05);
        break;
      case 'powerup':
        [523, 659, 784, 1047].forEach((f, i) => this._tone('square', f, f, 0.1, 0.09, i * 0.06));
        break;
      case 'horn':
        this._tone('sawtooth', 392, 392, 0.22, 0.12);
        this._tone('sawtooth', 494, 494, 0.22, 0.1);
        this._tone('sawtooth', 392, 392, 0.3, 0.12, 0.28);
        this._tone('sawtooth', 494, 494, 0.3, 0.1, 0.28);
        break;
      case 'click':
        this._tone('square', 660, 660, 0.05, 0.08);
        break;
      case 'buy':
        [784, 988, 1175, 1568].forEach((f, i) => this._tone('triangle', f, f, 0.12, 0.15, i * 0.07));
        break;
      case 'error':
        this._tone('square', 200, 150, 0.18, 0.12);
        break;
      case 'count':
        this._tone('square', 880, 880, 0.09, 0.1);
        break;
      case 'go':
        this._tone('square', 1320, 1320, 0.2, 0.12);
        break;
    }
  }

  // --- Music: light arcade loop in an Azerbaijani "Shur"-flavoured scale ---
  startMusic() {
    if (!this.ctx || this.musicPlaying) return;
    this.musicPlaying = true;
    this._step = 0;
    this._nextTime = this.ctx.currentTime + 0.1;
    this._timer = setInterval(() => this._schedule(), 30);
  }

  stopMusic() {
    this.musicPlaying = false;
    if (this._timer) clearInterval(this._timer);
    this._timer = null;
  }

  _schedule() {
    if (!this.ctx || this.ctx.state !== 'running') return;
    const stepDur = 60 / 132 / 4;
    // Scale degrees in semitones from D: D Eb F G A Bb C D
    const melody = [
      12, null, 10, 12, 13, null, 12, 10, 8, null, 7, 8, 10, null, 8, 7,
      5, null, 7, 8, 10, null, 8, 7, 5, 3, 1, 3, 0, null, null, null,
      12, null, 13, 15, 13, null, 12, 10, 12, null, 10, 8, 7, null, 8, 10,
      8, null, 7, 5, 7, null, 5, 3, 1, null, 3, 1, 0, null, null, null,
    ];
    const bass = [0, 0, 7, 0, -2, -2, 5, -2, -4, -4, 3, -4, -5, -5, 2, -5];
    while (this._nextTime < this.ctx.currentTime + 0.15) {
      const s = this._step, t = this._nextTime - this.ctx.currentTime;
      const base = 293.66; // D4
      const m = melody[s % melody.length];
      if (m !== null) this._tone('square', base * Math.pow(2, m / 12), base * Math.pow(2, m / 12), stepDur * 1.6, 0.06, t, this.music);
      if (s % 4 === 0) {
        const b = bass[(s / 4) % bass.length];
        const fb = base / 4 * Math.pow(2, b / 12);
        this._tone('triangle', fb, fb, stepDur * 3, 0.32, t, this.music);
      }
      if (s % 8 === 0) this._tone('sine', 120, 45, 0.18, 0.45, t, this.music);
      if (s % 8 === 4) this._noiseBurst(0.12, 0.18, 1800, t, 0.7, this.music);
      if (s % 2 === 1) this._noiseBurst(0.04, 0.06, 8000, t, 1, this.music, 'highpass');
      this._nextTime += stepDur;
      this._step++;
    }
  }
}

export const Audio = new AudioSystem();
