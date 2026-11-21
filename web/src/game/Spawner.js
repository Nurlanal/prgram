// Plans obstacle "rows" in time, guaranteeing that every row leaves at least
// one reachable, passable lane (the "safe lane"), then spawns them far ahead
// so that cars visibly approach from the horizon. Also handles coins,
// power-ups and collision tests.
import * as THREE from 'three';
import { LANE_X, SPAWN_DIST, DESPAWN_Z, PLAYER, COIN_SPACING } from '../config.js';
import { OBSTACLE_TYPES } from './obstacleTypes.js';
import { PoolSet } from './pool.js';
import { pick, rand, clamp, lerp, weighted } from './geom.js';
import * as TX from './textures.js';

const D = SPAWN_DIST;
const JUMP_HALF = PLAYER.jumpTime / 2;

export class Spawner {
  constructor(world, coins) {
    this.world = world;
    this.coins = coins;
    this.pool = new PoolSet(world.scroller);
    this.warnMat = new THREE.SpriteMaterial({ map: TX.warning(), transparent: true, depthTest: false, fog: false });
    this.powerMats = {
      magnet: new THREE.SpriteMaterial({ map: TX.powerIcon('magnet'), fog: false }),
      x2: new THREE.SpriteMaterial({ map: TX.powerIcon('x2'), fog: false }),
    };
    this.powerSprites = [];
    for (let i = 0; i < 3; i++) {
      const s = new THREE.Sprite(this.powerMats.magnet);
      s.scale.set(1.1, 1.1, 1); s.visible = false;
      world.scroller.add(s);
      this.powerSprites.push({ s, active: false, type: 'magnet' });
    }
    this.active = [];
    this.queue = [];
    this.reset();
  }

  reset() {
    for (const o of this.active) this._release(o);
    this.active.length = 0;
    this.queue.length = 0;
    for (const p of this.powerSprites) { p.active = false; p.s.visible = false; }
    this.tPlan = 1.6;          // first row arrives a little after start
    this.safe = 1;
    this.occ = [[], [], []];   // time intervals a lane is occupied at the player's position
    this.hist = [[], [], []];  // arrival time + approach speed per lane
    this.minGap = 0;
    this.lastPower = 0;
  }

  // -------------------------------------------------------------- planning
  _free(lane, a, b) {
    for (const i of this.occ[lane]) if (i.b > a && i.a < b) return false;
    return true;
  }

  _crossOk(lane, T, r) {
    for (const h of this.hist[lane]) {
      // a slower object planned after a faster one would be overtaken on screen
      if (h.T <= T && r < h.r - 0.01 && T - h.T < D * (1 / r - 1 / h.r) + 0.35) return false;
    }
    return true;
  }

  _canPlace(lane, T, r, len) {
    return this._free(lane, T - 0.1, T + len / r + 0.1) && this._crossOk(lane, T, r);
  }

  _register(lane, T, r, len) {
    this.hist[lane].push({ T, r });
    if (this.hist[lane].length > 10) this.hist[lane].shift();
    const occ = this.occ[lane];
    occ.push({ a: T - 0.05, b: T + len / r + 0.12 });
    while (occ.length > 10) occ.shift();
  }

  _plan(now, speed) {
    const diff = clamp(now / 160, 0, 1);
    let gap = lerp(1.45, 0.66, diff) * rand(0.85, 1.25);
    gap = Math.max(gap, this.minGap);
    this.minGap = 0;
    const prevT = this.tPlan;
    const T = prevT + gap;
    this.tPlan = T;
    const distAt = this.world.distance + speed * (T - now);
    const theme = this.world.themeAt(distAt);

    // 1) choose next safe lane (reachable from previous, free of long objects)
    const s = this.safe;
    const cands = [];
    for (const l of [s - 1, s, s + 1]) {
      if (l < 0 || l > 2) continue;
      if (this._free(l, l === s ? prevT + 0.2 : prevT + 0.12, T + 0.05)) cands.push(l);
    }
    const ns = cands.length ? (Math.random() < 0.55 ? pick(cands) : (cands.includes(s) ? s : cands[0])) : s;
    this.safe = ns;

    // 2) block other lanes
    const others = [0, 1, 2].filter((l) => l !== ns);
    const rBlock = Math.random();
    let nBlock = rBlock < lerp(0.3, 0.6, diff) ? 2 : rBlock < 0.95 ? 1 : 0;
    if (now < 6) nBlock = Math.min(nBlock, 1);
    const shuffled = Math.random() < 0.5 ? others : others.slice().reverse();
    for (let i = 0; i < nBlock; i++) this._placeObstacle(shuffled[i], T, speed, theme, diff, ns);

    // 3) optional action obstacle in the safe lane (jump / slide)
    let action = null;
    if (Math.random() < lerp(0.18, 0.5, diff) && now > 3) {
      const tk = weighted({ cones: theme.obstacles.cones, barrier: theme.obstacles.barrier, gantry: theme.obstacles.gantry });
      const ty = OBSTACLE_TYPES[tk];
      if (this._canPlace(ns, T, speed, ty.length)) {
        this._queueObstacle(tk, ty, ns, ns, T, 0, speed);
        this._register(ns, T, speed, ty.length);
        action = ty.kind;
        this.minGap = 1.0;
      }
    }

    // 4) coins along the safe path
    if (Math.random() < 0.7) {
      const t0 = prevT + 0.3;
      const step = COIN_SPACING / speed;
      for (let t = t0; t < T + (action ? JUMP_HALF : -0.15); t += step) {
        let y = 0.75;
        if (action === 'low') {
          const tau = (t - (T - JUMP_HALF)) / PLAYER.jumpTime;
          if (tau > 0 && tau < 1) y = Math.max(0.75, 4 * PLAYER.jumpHeight * tau * (1 - tau) + 0.6);
        }
        if (action === 'high' && Math.abs(t - T) < 0.2) continue;
        if (y < 1.4 && !this._staticOk(ns, t, speed)) continue;
        this.queue.push({ T: t, coin: true, lane: ns, y, v: 0 });
      }
    }

    // 5) power-up
    if (now - this.lastPower > 18 && Math.random() < 0.25 && now > 8) {
      this.lastPower = now;
      this.queue.push({ T: (prevT + T) / 2, power: Math.random() < 0.5 ? 'magnet' : 'x2', lane: ns, y: 1.1, v: 0 });
    }
  }

  _staticOk(lane, t, speed) {
    return this._crossOk(lane, t, speed) && this._free(lane, t - 0.06, t + 0.06);
  }

  _placeObstacle(lane, T, speed, theme, diff, safeLane) {
    for (let attempt = 0; attempt < 4; attempt++) {
      const key = weighted(theme.obstacles);
      const ty = OBSTACLE_TYPES[key];
      let v = rand(ty.speed[0], ty.speed[1]);
      if (ty.staticChance && Math.random() < ty.staticChance) v = 0;
      v *= lerp(0.7, 1.15, diff);
      const r = speed + v;
      if (!this._canPlace(lane, T, r, ty.length)) continue;
      // sudden lane-switching car
      let from = lane;
      if (ty.suddenChance && Math.random() < ty.suddenChance * (0.3 + diff) && T > 8) {
        const adj = [lane - 1, lane + 1].filter((m) => m >= 0 && m <= 2 && this.hist[m].every((h) => h.T < T - 3) && this._free(m, T - 3, T + 0.5));
        if (adj.length) from = pick(adj);
      }
      this._queueObstacle(key, ty, lane, from, T, v, r);
      this._register(lane, T, r, ty.length);
      if (from !== lane) this.hist[from].push({ T, r });
      return;
    }
  }

  _queueObstacle(key, ty, lane, from, T, v, r) {
    this.queue.push({ T, type: key, ty, lane, from, v, r });
  }

  // -------------------------------------------------------------- runtime
  update(dt, now, speed, distance, playing) {
    if (playing) while (this.tPlan < now + D / speed + 1.5) this._plan(now, speed);

    // spawn queued items whose lead time has come
    const q = this.queue;
    for (let i = q.length - 1; i >= 0; i--) {
      const it = q[i];
      const r = speed + (it.v || 0);
      const lead = it.T - now;
      if (lead * r > D) continue;
      q[i] = q[q.length - 1]; q.pop();
      const front = distance + r * Math.max(0, lead); // road distance of the front
      if (it.coin) { this.coins.spawn(LANE_X[it.lane], it.y, -front); continue; }
      if (it.power) { this._spawnPower(it, front); continue; }
      this._spawnObstacle(it, front);
    }

    // move obstacles
    for (let i = this.active.length - 1; i >= 0; i--) {
      const o = this.active[i];
      o.z += o.v * dt;
      const wz = o.z + distance;
      if (o.from !== o.lane) {
        const startZ = -46 - o.v * 0.5;
        if (wz > startZ && o.switchT < 1) {
          if (o.switchT === 0) o.honk = true;
          o.switchT = Math.min(1, o.switchT + dt / 0.55);
        }
        const k = o.switchT * o.switchT * (3 - 2 * o.switchT);
        o.x = lerp(LANE_X[o.from], LANE_X[o.lane], k);
        o.obj.rotation.y = Math.sin(k * Math.PI) * 0.25 * Math.sign(LANE_X[o.lane] - LANE_X[o.from]);
        if (o.warn) o.warn.visible = wz > -95 && o.switchT < 1;
      }
      o.obj.position.set(o.x, 0, o.z);
      if (o.knock > 0) {
        o.knock += dt;
        o.obj.position.y = o.knock * 6 - o.knock * o.knock * 12;
        o.obj.rotation.z += dt * 8;
      }
      if (wz - o.len / 2 > DESPAWN_Z) { this._release(o); this.active[i] = this.active[this.active.length - 1]; this.active.pop(); }
    }

    for (const p of this.powerSprites) {
      if (!p.active) continue;
      p.s.position.y = 1.1 + Math.sin(now * 4) * 0.15;
      if (p.s.position.z + distance > DESPAWN_Z) { p.active = false; p.s.visible = false; }
    }
  }

  _spawnPower(it, front) {
    const p = this.powerSprites.find((s) => !s.active);
    if (!p) return;
    p.active = true; p.type = it.power;
    p.s.material = this.powerMats[it.power];
    p.s.visible = true;
    p.s.position.set(LANE_X[it.lane], it.y, -front);
  }

  _spawnObstacle(it, front) {
    const ty = it.ty;
    const key = pick(ty.prefabs);
    const obj = this.pool.get(key);
    obj.rotation.set(0, 0, 0);
    const o = {
      obj, type: it.type, kind: ty.kind, lane: it.lane, from: it.from, x: LANE_X[it.from],
      z: -front - ty.length / 2, len: ty.length, w: ty.width, h: ty.height || 3, bottom: ty.bottom || 0,
      v: it.v, switchT: 0, prevZ: false, ignore: false, knock: 0, honk: false, warn: null,
    };
    if (it.from !== it.lane) {
      if (!obj.userData.warn) {
        const w = new THREE.Sprite(this.warnMat);
        w.scale.set(1.3, 1.3, 1); w.position.set(0, 3.6, 0); w.renderOrder = 5;
        obj.add(w); obj.userData.warn = w;
      }
      o.warn = obj.userData.warn;
    }
    if (obj.userData.warn) obj.userData.warn.visible = false;
    obj.position.set(o.x, 0, o.z);
    this.active.push(o);
  }

  _release(o) {
    if (o.obj.userData.warn) o.obj.userData.warn.visible = false;
    o.obj.position.y = 0; o.obj.rotation.set(0, 0, 0);
    this.pool.release(o.obj);
  }

  // Returns null | {type:'crash'|'side', o}
  collide(player, distance) {
    const b = player.box();
    let res = null;
    for (const o of this.active) {
      if (o.ignore) continue;
      const wz = o.z + distance;
      const z0 = wz - o.len / 2, z1 = wz + o.len / 2;
      const zOver = z1 > b.z0 && z0 < b.z1;
      const xOver = Math.abs(o.x - b.x) < (o.w + b.w) / 2 - 0.18;
      let yOver = true;
      if (o.kind === 'low') yOver = b.y0 < o.h - 0.08;
      else if (o.kind === 'high') yOver = b.y1 > o.bottom;
      if (zOver && xOver && yOver && !res) {
        const side = o.prevZ && o.kind === 'solid' && z1 - b.z0 > 0.9;
        res = { type: side ? 'side' : 'crash', o };
      }
      o.prevZ = zOver;
    }
    return res;
  }

  // power-ups picked up this frame
  collectPowers(player, distance) {
    let got = null;
    for (const p of this.powerSprites) {
      if (!p.active) continue;
      const wz = p.s.position.z + distance;
      if (Math.abs(wz) < 0.9 && Math.abs(p.s.position.x - player.x) < 1.0) {
        p.active = false; p.s.visible = false; got = p.type;
      }
    }
    return got;
  }

  // honk events (sudden cars starting to switch lanes)
  takeHonk() {
    for (const o of this.active) if (o.honk) { o.honk = false; return true; }
    return false;
  }
}
