// The runner: procedural cartoon character with run / jump / slide / crash
// animations, lane movement and its collision box.
import * as THREE from 'three';
import { LANE_X, LANE_WIDTH, PLAYER } from '../config.js';
import * as TX from './textures.js';

const PI = Math.PI;

function lam(color) { return new THREE.MeshLambertMaterial({ color }); }

function capsule(r, len, mat) {
  const g = new THREE.CapsuleGeometry(r, len, 4, 8);
  return new THREE.Mesh(g, mat);
}

export class Player {
  constructor(scene) {
    this.root = new THREE.Group();
    scene.add(this.root);
    this.model = new THREE.Group();
    this.root.add(this.model);

    // blob shadow
    const sm = new THREE.MeshBasicMaterial({ map: TX.radial('shadow', 'rgba(0,0,0,0.55)', 'rgba(0,0,0,0)'), transparent: true, depthWrite: false });
    this.shadow = new THREE.Mesh(new THREE.PlaneGeometry(1.4, 1.4), sm);
    this.shadow.rotation.x = -PI / 2;
    this.shadow.position.y = 0.2;
    this.shadow.renderOrder = 1;
    scene.add(this.shadow);

    // skateboard (shield)
    const board = new THREE.Group();
    const deck = new THREE.Mesh(new THREE.BoxGeometry(0.55, 0.06, 1.3), lam(0x00b5e2));
    board.add(deck);
    const stripe = new THREE.Mesh(new THREE.BoxGeometry(0.56, 0.065, 0.3), lam(0xef3340));
    board.add(stripe);
    const glow = new THREE.Mesh(new THREE.PlaneGeometry(1.2, 1.8), new THREE.MeshBasicMaterial({
      map: TX.radial('board', 'rgba(120,220,255,0.9)', 'rgba(0,150,255,0)'), transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
    }));
    glow.rotation.x = -PI / 2; glow.position.y = -0.08;
    board.add(glow);
    board.position.y = 0.25;
    board.visible = false;
    this.board = board;
    this.root.add(board);

    this.reset();
  }

  setCharacter(c) {
    this.char = c;
    while (this.model.children.length) this.model.remove(this.model.children[0]);
    const skin = lam(c.skin), top = lam(c.top), pants = lam(c.pants), shoe = lam(c.shoes), sole = lam(c.sole), vestM = lam(c.vest);

    const hips = new THREE.Group(); hips.position.y = 0.78; this.model.add(hips); this.hips = hips;
    const body = new THREE.Group(); hips.add(body); this.body = body;

    const torso = capsule(0.27, 0.32, top); torso.position.y = 0.34; torso.scale.set(1.05, 1, 0.75); body.add(torso);
    const vest = new THREE.Mesh(new THREE.BoxGeometry(0.6, 0.55, 0.36), vestM); vest.position.y = 0.36; body.add(vest);
    const backMat = new THREE.MeshLambertMaterial({ map: TX.vestTexture(c.vestPattern, c.vest) });
    const back = new THREE.Mesh(new THREE.PlaneGeometry(0.58, 0.53), backMat); back.position.set(0, 0.36, 0.182); body.add(back);
    const front = new THREE.Mesh(new THREE.PlaneGeometry(0.58, 0.53), backMat); front.position.set(0, 0.36, -0.182); front.rotation.y = PI; body.add(front);
    const belt = new THREE.Mesh(new THREE.BoxGeometry(0.56, 0.1, 0.34), pants); belt.position.y = 0.05; body.add(belt);

    // head
    const head = new THREE.Group(); head.position.y = 0.95; body.add(head); this.head = head;
    const skull = new THREE.Mesh(new THREE.SphereGeometry(0.34, 16, 12), skin); skull.scale.set(1, 1.05, 0.98); head.add(skull);
    const hairM = lam(c.hair);
    const hair = new THREE.Mesh(new THREE.SphereGeometry(0.35, 14, 10, 0, PI * 2, 0, PI * 0.55), hairM); hair.position.y = 0.02; hair.rotation.x = 0.25; head.add(hair);
    for (const s of [-1, 1]) {
      const ear = new THREE.Mesh(new THREE.SphereGeometry(0.08, 8, 6), skin); ear.position.set(s * 0.33, -0.02, 0.02); head.add(ear);
      const eye = new THREE.Mesh(new THREE.SphereGeometry(0.045, 8, 6), lam(0x1a1a1a)); eye.position.set(s * 0.12, 0.03, -0.31); head.add(eye);
    }
    const hatM = lam(c.hatColor);
    if (c.hat === 'cap') {
      const cap = new THREE.Mesh(new THREE.SphereGeometry(0.36, 14, 10, 0, PI * 2, 0, PI * 0.5), hatM); cap.position.y = 0.04; head.add(cap);
      const brim = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.22, 0.04, 14, 1, false, 0, PI), hatM);
      brim.position.set(0, 0.06, 0.28); brim.scale.z = 1.4; head.add(brim); // cap worn backwards
      const btn = new THREE.Mesh(new THREE.SphereGeometry(0.05, 6, 4), lam(0xffffff)); btn.position.y = 0.4; head.add(btn);
    } else if (c.hat === 'headphones') {
      const band = new THREE.Mesh(new THREE.TorusGeometry(0.37, 0.04, 6, 16, PI), hatM); band.position.y = 0.02; head.add(band);
      for (const s of [-1, 1]) { const cup = new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.12, 0.1, 12), hatM); cup.rotation.z = PI / 2; cup.position.set(s * 0.36, 0, 0); head.add(cup); }
    } else if (c.hat === 'ponytail') {
      const tail = capsule(0.1, 0.35, hairM); tail.position.set(0, -0.05, 0.38); tail.rotation.x = 0.5; head.add(tail); this.tail = tail;
      const tie = new THREE.Mesh(new THREE.TorusGeometry(0.08, 0.03, 6, 10), hatM); tie.position.set(0, 0.12, 0.33); head.add(tie);
    } else if (c.hat === 'kelaghayi') {
      const scarf = new THREE.Mesh(new THREE.SphereGeometry(0.37, 14, 10, 0, PI * 2, 0, PI * 0.62), hatM); scarf.position.y = 0.02; scarf.rotation.x = 0.35; head.add(scarf);
      const knot = new THREE.Mesh(new THREE.ConeGeometry(0.16, 0.4, 8), hatM); knot.position.set(0, -0.28, 0.26); knot.rotation.x = 2.6; head.add(knot);
    } else if (c.hat === 'papaq') {
      const pap = new THREE.Mesh(new THREE.CylinderGeometry(0.33, 0.37, 0.42, 14), hatM); pap.position.y = 0.32; head.add(pap);
      const fur = new THREE.Mesh(new THREE.TorusGeometry(0.36, 0.06, 6, 14), lam(0x3a322c)); fur.rotation.x = PI / 2; fur.position.y = 0.13; head.add(fur);
    }
    if (c.beard) { const b = new THREE.Mesh(new THREE.BoxGeometry(0.28, 0.07, 0.05), hairM); b.position.set(0, -0.12, -0.32); head.add(b); }

    // arms
    this.arms = [];
    for (const s of [-1, 1]) {
      const sh = new THREE.Group(); sh.position.set(s * 0.37, 0.58, 0); body.add(sh);
      const upper = capsule(0.09, 0.26, top); upper.position.y = -0.18; sh.add(upper);
      const elbow = new THREE.Group(); elbow.position.y = -0.34; sh.add(elbow);
      const fore = capsule(0.085, 0.2, top); fore.position.y = -0.12; elbow.add(fore);
      const hand = new THREE.Mesh(new THREE.SphereGeometry(0.1, 8, 6), skin); hand.position.y = -0.3; elbow.add(hand);
      sh.rotation.z = s * 0.15;
      this.arms.push({ sh, elbow, s });
    }
    // legs
    this.legs = [];
    for (const s of [-1, 1]) {
      const hip = new THREE.Group(); hip.position.set(s * 0.15, 0, 0); hips.add(hip);
      const thigh = capsule(0.11, 0.24, pants); thigh.position.y = -0.17; hip.add(thigh);
      const knee = new THREE.Group(); knee.position.y = -0.36; hip.add(knee);
      const shin = capsule(0.1, 0.2, pants); shin.position.y = -0.13; knee.add(shin);
      const shoeG = new THREE.Group(); shoeG.position.y = -0.33; knee.add(shoeG);
      const sh = new THREE.Mesh(new THREE.BoxGeometry(0.2, 0.14, 0.34), shoe); sh.position.z = -0.05; shoeG.add(sh);
      const so = new THREE.Mesh(new THREE.BoxGeometry(0.21, 0.05, 0.35), sole); so.position.set(0, -0.08, -0.05); shoeG.add(so);
      this.legs.push({ hip, knee, s });
    }
    this.model.traverse((o) => { if (o.isMesh) o.frustumCulled = false; });
  }

  reset() {
    this.lane = 1;
    this.prevLane = 1;
    this.x = LANE_X[1];
    this.y = 0;
    this.jumpT = -1;      // >=0 while jumping
    this.slideT = -1;     // >=0 while sliding
    this.dropping = false;
    this.phase = 0;
    this.dead = false;
    this.deadT = 0;
    this.stumble = 0;
    this.tilt = 0;
    this.root.position.set(this.x, 0, 0);
    this.root.rotation.set(0, 0, 0);
    this.model.rotation.set(0, 0, 0);
    this.model.position.set(0, 0, 0);
    this.board.visible = false;
  }

  // ---- controls
  moveLane(dir) {
    const n = this.lane + dir;
    if (n < 0 || n > 2 || this.dead) return false;
    this.prevLane = this.lane;
    this.lane = n;
    return true;
  }

  bounceBack() { this.lane = this.prevLane; this.stumble = 0.4; }

  jump() {
    if (this.dead) return false;
    if (this.jumpT >= 0) return false;
    this.slideT = -1;
    this.dropping = false;
    this.jumpT = 0;
    return true;
  }

  slide() {
    if (this.dead) return false;
    if (this.jumpT >= 0) { this.jumpT = -1; this.dropping = true; }
    this.slideT = 0;
    return true;
  }

  get sliding() { return this.slideT >= 0; }
  get airborne() { return this.y > 0.05; }

  // Collision box in world space
  box() {
    const h = this.sliding ? PLAYER.slideHeight : PLAYER.height;
    return { x: this.x, w: PLAYER.width, y0: this.y, y1: this.y + h, z0: -PLAYER.depth / 2, z1: PLAYER.depth / 2 };
  }

  update(dt, speed, mode) {
    if (mode === 'idle' || mode === 'show') { this._animIdle(dt, mode); return; }
    if (this.dead) { this._animDead(dt); return; }

    // horizontal lane movement
    const tx = LANE_X[this.lane];
    const vx = LANE_WIDTH / PLAYER.laneTime;
    const dx = tx - this.x;
    const step = vx * dt;
    this.x = Math.abs(dx) <= step ? tx : this.x + Math.sign(dx) * step;
    this.tilt += ((Math.abs(dx) > 0.01 ? -Math.sign(dx) * 0.25 : 0) - this.tilt) * Math.min(1, dt * 14);

    // vertical
    if (this.jumpT >= 0) {
      this.jumpT += dt / PLAYER.jumpTime;
      if (this.jumpT >= 1) { this.jumpT = -1; this.y = 0; }
      else this.y = 4 * PLAYER.jumpHeight * this.jumpT * (1 - this.jumpT);
    } else if (this.dropping) {
      this.y = Math.max(0, this.y - dt * 16);
      if (this.y === 0) this.dropping = false;
    } else this.y = 0;
    if (this.slideT >= 0) {
      this.slideT += dt / PLAYER.slideTime;
      if (this.slideT >= 1) this.slideT = -1;
    }
    this.stumble = Math.max(0, this.stumble - dt);

    this.phase += dt * (9 + speed * 0.22);
    this._pose(dt);
    this.root.position.set(this.x, this.y, 0);
    this.root.rotation.z = this.tilt * 0.35;
    this._shadow();
  }

  _shadow() {
    this.shadow.position.x = this.x;
    const s = Math.max(0.4, 1 - this.y * 0.25);
    this.shadow.scale.set(s, s, s);
  }

  _pose() {
    const p = this.phase, sn = Math.sin(p);
    const L = this.legs, A = this.arms;
    let hipY = 0.78 + Math.abs(Math.cos(p)) * 0.07, bodyX = -0.22, bodyY = 0;
    if (this.slideT >= 0) {
      const k = Math.min(1, this.slideT * 6, (1 - this.slideT) * 6);
      hipY = 0.78 - 0.45 * k; bodyX = -0.22 + 1.3 * k;
      for (const l of L) { l.hip.rotation.x = 1.4 * k + (1 - k) * 0.3; l.knee.rotation.x = -0.2; }
      for (const a of A) { a.sh.rotation.x = -0.6 * k; a.sh.rotation.z = a.s * (0.15 + 0.9 * k); a.elbow.rotation.x = 0; }
    } else if (this.jumpT >= 0 || this.dropping) {
      const t = this.jumpT >= 0 ? this.jumpT : 0.9;
      const tuck = Math.sin(t * PI);
      L[0].hip.rotation.x = 0.9 * tuck; L[0].knee.rotation.x = -1.6 * tuck;
      L[1].hip.rotation.x = 0.3 * tuck; L[1].knee.rotation.x = -1.0 * tuck;
      for (const a of A) { a.sh.rotation.x = PI * 0.85 * tuck; a.sh.rotation.z = a.s * (0.15 + 0.4 * tuck); a.elbow.rotation.x = 0.4 * tuck; }
      bodyX = -0.1 - 0.2 * tuck;
    } else {
      L[0].hip.rotation.x = sn * 0.95; L[1].hip.rotation.x = -sn * 0.95;
      L[0].knee.rotation.x = -Math.max(0, -Math.sin(p + 0.9)) * 1.6 - 0.15;
      L[1].knee.rotation.x = -Math.max(0, Math.sin(p + 0.9)) * 1.6 - 0.15;
      A[0].sh.rotation.x = -sn * 0.9; A[1].sh.rotation.x = sn * 0.9;
      for (const a of A) { a.sh.rotation.z = a.s * 0.15; a.elbow.rotation.x = 1.3; }
      bodyY = sn * 0.08;
    }
    if (this.stumble > 0) bodyX += Math.sin(this.stumble * 30) * 0.15;
    this.hips.position.y = hipY;
    this.body.rotation.x = bodyX;
    this.body.rotation.y = bodyY;
    if (this.tail) this.tail.rotation.x = 0.5 + Math.sin(p * 2) * 0.25;
  }

  _animIdle(dt, mode) {
    this.phase += dt * 2;
    const b = Math.sin(this.phase);
    this.hips.position.y = 0.78 + b * 0.01;
    this.body.rotation.set(0, 0, 0);
    for (const l of this.legs) { l.hip.rotation.x = 0; l.knee.rotation.x = 0; }
    for (const a of this.arms) { a.sh.rotation.x = 0.1 + b * 0.05; a.sh.rotation.z = a.s * 0.12; a.elbow.rotation.x = 0.3; }
    this.head.rotation.y = Math.sin(this.phase * 0.5) * 0.15;
    this.root.position.set(this.x, 0, 0);
    this.root.rotation.z = 0;
    if (mode === 'show') this.model.rotation.y += dt * 0.6;
    else this.model.rotation.y = 0;
    this._shadow();
  }

  _animDead(dt) {
    this.deadT += dt;
    const k = Math.min(1, this.deadT * 3);
    this.body.rotation.x = 1.3 * k;
    this.hips.position.y = 0.78 - 0.5 * k;
    this.model.position.z = 0.9 * k;
    for (const a of this.arms) { a.sh.rotation.x = 2.6 * k; a.sh.rotation.z = a.s * 0.9 * k; }
    for (const l of this.legs) { l.hip.rotation.x = 0.8 * k; l.knee.rotation.x = -0.3 * k; }
    this.y = Math.max(0, this.y - dt * 8);
    this.root.position.set(this.x, this.y, 0);
    this._shadow();
  }
}
