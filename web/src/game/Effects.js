// Lightweight particle effects (coin sparkles, crash dust) and speed lines.
import * as THREE from 'three';
import * as TX from './textures.js';

export class Effects {
  constructor(scene) {
    this.scene = scene;
    this.parts = [];
    const sparkMat = new THREE.SpriteMaterial({ map: TX.sparkle(), transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, fog: false });
    const dustMat = new THREE.SpriteMaterial({ map: TX.radial('dust', 'rgba(230,220,200,0.9)', 'rgba(200,190,170,0)'), transparent: true, depthWrite: false });
    for (let i = 0; i < 40; i++) {
      const s = new THREE.Sprite(i < 28 ? sparkMat.clone() : dustMat.clone());
      s.visible = false;
      scene.add(s);
      this.parts.push({ s, life: 0, max: 1, vx: 0, vy: 0, vz: 0, size: 1, kind: i < 28 ? 'spark' : 'dust' });
    }
    // speed lines
    this.lines = [];
    const lm = new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.0, depthWrite: false, fog: false });
    this.lineMat = lm;
    const lg = new THREE.PlaneGeometry(0.04, 3.5);
    lg.rotateX(-Math.PI / 2);
    for (let i = 0; i < 14; i++) {
      const m = new THREE.Mesh(lg, lm);
      this._resetLine(m, true);
      scene.add(m);
      this.lines.push(m);
    }
    this.speedLines = true;
  }

  _resetLine(m, initial) {
    const side = Math.random() < 0.5 ? -1 : 1;
    m.position.set(side * (1.8 + Math.random() * 3), 0.3 + Math.random() * 3.5, initial ? -Math.random() * 30 : -30 - Math.random() * 10);
  }

  _get(kind) {
    for (const p of this.parts) if (p.kind === kind && p.life <= 0) return p;
    return null;
  }

  coin(x, y, z) {
    for (let i = 0; i < 2; i++) {
      const p = this._get('spark');
      if (!p) return;
      p.s.position.set(x, y, z);
      p.vx = (Math.random() - 0.5) * 2; p.vy = 2 + Math.random() * 2; p.vz = 2;
      p.life = p.max = 0.35; p.size = 0.9;
      p.s.visible = true;
    }
  }

  burst(x, y, z, n = 10, kind = 'spark') {
    for (let i = 0; i < n; i++) {
      const p = this._get(kind);
      if (!p) return;
      p.s.position.set(x, y, z);
      const a = Math.random() * Math.PI * 2;
      p.vx = Math.cos(a) * 3; p.vy = 1 + Math.random() * 3; p.vz = Math.sin(a) * 3;
      p.life = p.max = 0.6; p.size = kind === 'dust' ? 1.6 : 1.1;
      p.s.visible = true;
    }
  }

  clear() {
    for (const p of this.parts) { p.life = 0; p.s.visible = false; }
  }

  update(dt, speed) {
    for (const p of this.parts) {
      if (p.life <= 0) continue;
      p.life -= dt;
      if (p.life <= 0) { p.s.visible = false; continue; }
      const k = p.life / p.max;
      p.s.position.x += p.vx * dt; p.s.position.y += p.vy * dt; p.s.position.z += p.vz * dt;
      p.vy -= 4 * dt;
      const sz = p.size * (p.kind === 'dust' ? 2 - k : 0.5 + k);
      p.s.scale.set(sz, sz, 1);
      p.s.material.opacity = k;
    }
    const target = this.speedLines ? Math.max(0, (speed - 22) / 25) * 0.35 : 0;
    this.lineMat.opacity += (target - this.lineMat.opacity) * Math.min(1, dt * 3);
    const show = this.lineMat.opacity > 0.01;
    for (const m of this.lines) {
      m.visible = show;
      if (!show) continue;
      m.position.z += speed * 1.6 * dt;
      if (m.position.z > 8) this._resetLine(m, false);
    }
  }
}
