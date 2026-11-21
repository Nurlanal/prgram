// Game orchestration: render loop, state machine, scoring, power-ups, camera.
import * as THREE from 'three';
import { SPEED, SCORE, POWERUP } from '../config.js';
import { World } from './World.js';
import { Player } from './Player.js';
import { Coins } from './Coins.js';
import { Spawner } from './Spawner.js';
import { Effects } from './Effects.js';
import { getCharacter } from './characters.js';
import { Audio } from '../audio.js';
import { Save } from '../storage.js';
import { lerp } from './geom.js';

const _look = new THREE.Vector3();

export class Game {
  constructor(canvas, ui) {
    this.ui = ui;
    const q = Save.data.settings.quality;
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: q === 'high', powerPreference: 'high-performance', stencil: false });
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.scene = new THREE.Scene();
    this.camera = new THREE.PerspectiveCamera(70, 1, 0.3, 1200);
    this.camera.position.set(0, 4, 7.5);
    this.camLook = new THREE.Vector3(0, 1.3, -8);

    this.world = new World(this.scene, q);
    this.player = new Player(this.scene);
    this.coins = new Coins(this.world.scroller);
    this.spawner = new Spawner(this.world, this.coins);
    this.effects = new Effects(this.scene);
    this.setQuality(q);
    this.setCharacter(Save.data.selectedChar);

    this.state = 'menu';
    this.camMode = 'menu';
    this.distance = 0;
    this.shake = 0;
    this._collected = [];
    this._fps = { frames: 0, t: 0, value: 60 };
    this._resetRun();

    window.addEventListener('resize', () => this.resize());
    this.resize();
    this._last = performance.now();
    requestAnimationFrame((t) => this._frame(t));
  }

  setQuality(q) {
    const dpr = window.devicePixelRatio || 1;
    this.renderer.setPixelRatio(q === 'high' ? Math.min(dpr, 2) : Math.min(dpr, 1));
    this.world.quality = q;
    this.effects.speedLines = q === 'high';
  }

  setCharacter(id) {
    this.player.setCharacter(getCharacter(id));
  }

  resize() {
    const w = window.innerWidth, h = window.innerHeight;
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    // keep all three lanes visible on narrow (portrait) screens
    const halfW = 3.0, dist = 7.0;
    const vfov = 2 * Math.atan(halfW / (dist * this.camera.aspect)) * 180 / Math.PI;
    this.camera.fov = Math.min(80, Math.max(55, vfov));
    this.camera.updateProjectionMatrix();
  }

  _resetRun() {
    this.run = {
      time: 0, distance: 0, speed: SPEED.start, coins: 0, score: 0,
      magnetT: 0, x2T: 0, shieldT: 0, invulnT: 0, lastStumble: -99, hints: 0,
    };
  }

  // ------------------------------------------------------------ flow
  startRun() {
    Audio.unlock();
    this._resetRun();
    this.distance = 0;
    this.world.reset();
    this.spawner.reset();
    this.coins.reset();
    this.effects.clear();
    this.player.reset();
    this.setCharacter(Save.data.selectedChar);
    if (Save.data.useBoard && Save.data.boards > 0) {
      Save.data.boards -= 1;
      Save.save();
      this.run.shieldT = POWERUP.shieldTime;
    }
    this.state = 'playing';
    this.camMode = 'run';
    if (Save.data.settings.music) Audio.startMusic();
    this.ui.showHud(this.run);
    this.ui.banner(this.world.theme.name);
  }

  pause() {
    if (this.state !== 'playing' && this.state !== 'countdown') return;
    this.state = 'paused';
    Audio.suspend();
    this.ui.showPause();
  }

  resume() {
    if (this.state !== 'paused') return;
    Audio.resume();
    this.state = 'countdown';
    this.countdown = 3;
    this.ui.countdown(3);
    Audio.play('count');
  }

  toMenu() {
    Audio.resume();
    Audio.stopMusic();
    this.state = 'menu';
    this.camMode = 'menu';
    this.distance = 0;
    this.world.reset();
    this.spawner.reset();
    this.coins.reset();
    this.effects.clear();
    this.player.reset();
    this.setCharacter(Save.data.selectedChar);
  }

  showcase(on) {
    this.state = on ? 'show' : 'menu';
    this.camMode = on ? 'show' : 'menu';
    if (!on) this.player.model.rotation.y = 0;
  }

  _gameOver() {
    this.state = 'dying';
    this.dyingT = 0;
    Audio.stopMusic();
    Audio.play('crash');
    this.ui.vibrate(250);
    this.shake = 0.6;
    this.player.dead = true;
    this.effects.burst(this.player.x, 1, -0.5, 10, 'dust');
    const r = this.run;
    const d = Save.data;
    d.coins += r.coins;
    d.totalCoins += r.coins;
    d.runs += 1;
    const score = Math.floor(r.score);
    const isBest = score > d.best;
    if (isBest) d.best = score;
    d.bestDistance = Math.max(d.bestDistance, Math.floor(r.distance));
    d.tutorialDone = true;
    Save.save();
    this.lastResult = { score, coins: r.coins, distance: Math.floor(r.distance), best: d.best, isBest };
  }

  action(a) {
    if (this.state !== 'playing') return;
    const p = this.player;
    if (a === 'left' && p.moveLane(-1)) Audio.play('lane');
    else if (a === 'right' && p.moveLane(1)) Audio.play('lane');
    else if (a === 'up' && p.jump()) Audio.play('jump');
    else if (a === 'down' && p.slide()) Audio.play('slide');
  }

  // ------------------------------------------------------------ loop
  _frame(t) {
    requestAnimationFrame((tt) => this._frame(tt));
    let dt = (t - this._last) / 1000;
    this._last = t;
    if (dt <= 0) return;
    dt = Math.min(dt, 1 / 20);

    this._fps.frames++; this._fps.t += dt;
    if (this._fps.t >= 0.5) { this._fps.value = Math.round(this._fps.frames / this._fps.t); this._fps.frames = 0; this._fps.t = 0; this.ui.fps(this._fps.value); }

    if (this.state === 'paused') return; // fully frozen, nothing updates or renders

    if (this.state === 'countdown') {
      const prev = Math.ceil(this.countdown);
      this.countdown -= dt;
      const now = Math.ceil(this.countdown);
      if (now !== prev && now > 0) { this.ui.countdown(now); Audio.play('count'); }
      if (this.countdown <= 0) { this.ui.countdown(0); Audio.play('go'); this.state = 'playing'; }
      this._render(dt);
      return;
    }

    if (this.state === 'playing') this._updateRun(dt);
    else if (this.state === 'dying') {
      this.dyingT += dt;
      this.player.update(dt, 0, 'run');
      if (this.dyingT > 1.3) { this.state = 'gameover'; this.ui.showGameOver(this.lastResult); }
    } else if (this.state === 'menu') {
      this.distance += SPEED.menu * dt;
      this.world.update(dt, this.distance, this.camera);
      this.player.update(dt, SPEED.menu, 'idle');
    } else if (this.state === 'show') {
      this.player.update(dt, 0, 'show');
      this.world.update(dt, this.distance, this.camera);
    }
    this.effects.update(dt, this.state === 'playing' ? this.run.speed : 0);
    this._render(dt);
  }

  _updateRun(dt) {
    const r = this.run;
    r.time += dt;
    r.speed = SPEED.start + (SPEED.max - SPEED.start) * (1 - Math.exp(-r.time / SPEED.tau));
    const step = r.speed * dt;
    r.distance += step;
    this.distance += step;
    const mult = r.x2T > 0 ? 2 : 1;
    r.score += step * SCORE.perMetre * mult;

    const newTheme = this.world.update(dt, this.distance, this.camera);
    if (newTheme) this.ui.banner(newTheme.name);
    this.spawner.update(dt, r.time, r.speed, this.distance, true);
    if (this.spawner.takeHonk()) Audio.play('horn');
    this.player.update(dt, r.speed, 'run');
    this.player.board.visible = r.shieldT > 0;

    // timers
    r.magnetT = Math.max(0, r.magnetT - dt);
    r.x2T = Math.max(0, r.x2T - dt);
    r.shieldT = Math.max(0, r.shieldT - dt);
    r.invulnT = Math.max(0, r.invulnT - dt);

    // coins
    const out = this._collected; out.length = 0;
    this.coins.update(dt, this.player, this.distance, r.magnetT > 0, out);
    for (let i = 0; i < out.length; i += 3) {
      r.coins += mult;
      r.score += SCORE.perCoin * mult;
      this.effects.coin(out[i], out[i + 1], out[i + 2]);
      Audio.play('coin');
      this.ui.coinPulse();
    }

    const pw = this.spawner.collectPowers(this.player, this.distance);
    if (pw) {
      const lvl = Save.data.upgrades[pw] || 0;
      const dur = (pw === 'magnet' ? POWERUP.magnetBase : POWERUP.x2Base) + lvl * POWERUP.perLevel;
      if (pw === 'magnet') r.magnetT = dur; else r.x2T = dur;
      r[pw + 'Max'] = dur;
      Audio.play('powerup');
      this.effects.burst(this.player.x, 1.2, 0, 8);
      this.ui.toast(pw === 'magnet' ? 'MAQNİT!' : 'x2 XAL!');
    }

    // collisions
    const hit = this.spawner.collide(this.player, this.distance);
    if (hit && r.invulnT <= 0) {
      if (hit.type === 'side') {
        hit.o.ignore = true;
        this.player.bounceBack();
        Audio.play('bump');
        this.ui.vibrate(60);
        this.shake = 0.25;
        if (r.time - r.lastStumble < 4 && r.shieldT <= 0) { this._gameOver(); return; }
        r.lastStumble = r.time;
        this.ui.toast('Ehtiyatlı ol!');
      } else if (r.shieldT > 0) {
        r.shieldT = 0;
        r.invulnT = 1.5;
        hit.o.ignore = true;
        hit.o.knock = 0.01;
        Audio.play('bump');
        this.ui.vibrate(80);
        this.shake = 0.35;
        this.effects.burst(this.player.x, 1, -1, 10);
        this.ui.toast('Kaykay səni qorudu!');
      } else { this._gameOver(); return; }
    }

    // tutorial hints for the very first run
    if (!Save.data.tutorialDone) {
      const msgs = [[0.3, '⇦ ⇨  Sürüşdür — zolağı dəyiş'], [4.5, '⇧  Yuxarı — tullan'], [8.5, '⇩  Aşağı — maneənin altından keç'], [12.5, '']];
      while (r.hints < msgs.length && r.time > msgs[r.hints][0]) this.ui.hint(msgs[r.hints++][1]);
    }

    this.ui.updateHud(r, Save.data.best);
  }

  _render(dt) {
    this._updateCamera(dt);
    this.renderer.render(this.scene, this.camera);
  }

  _updateCamera(dt) {
    const p = this.player, cam = this.camera;
    let tx, ty, tz, lx, ly, lz;
    if (this.camMode === 'show') {
      tx = p.x; ty = 1.7; tz = -4.6; lx = p.x; ly = 1.1; lz = 0;
    } else if (this.camMode === 'menu') {
      const s = Math.sin(performance.now() / 4000);
      tx = p.x + s * 1.2; ty = 3.4; tz = 6.6; lx = p.x * 0.8; ly = 1.6; lz = -10;
    } else {
      tx = p.x * 0.72; ty = 3.7 + p.y * 0.35 - (p.sliding ? 0.35 : 0); tz = 6.8;
      lx = p.x * 0.85; ly = 1.35 + p.y * 0.25; lz = -9;
    }
    const k = Math.min(1, dt * (this.camMode === 'run' ? 9 : 3));
    cam.position.x = lerp(cam.position.x, tx, k);
    cam.position.y = lerp(cam.position.y, ty, k);
    cam.position.z = lerp(cam.position.z, tz, k);
    this.camLook.x = lerp(this.camLook.x, lx, k);
    this.camLook.y = lerp(this.camLook.y, ly, k);
    this.camLook.z = lerp(this.camLook.z, lz, k);
    _look.copy(this.camLook);
    if (this.shake > 0) {
      this.shake = Math.max(0, this.shake - dt);
      const a = this.shake * 0.5;
      cam.position.x += (Math.random() - 0.5) * a;
      cam.position.y += (Math.random() - 0.5) * a;
    }
    cam.lookAt(_look);
  }

  // Android back button: returns 'exit' when the app may close
  onBack() {
    if (this.state === 'playing' || this.state === 'countdown') { this.pause(); return 'handled'; }
    if (this.state === 'paused') { this.resume(); this.ui.hideOverlay(); return 'handled'; }
    return this.ui.back();
  }
}
