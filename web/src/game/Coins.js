// Coins rendered as a single InstancedMesh (one draw call for all coins).
import * as THREE from 'three';
import { COIN_POOL } from '../config.js';
import * as TX from './textures.js';

const _m = new THREE.Matrix4();
const _q = new THREE.Quaternion();
const _e = new THREE.Euler(0, 0, 0, 'YXZ');
const _p = new THREE.Vector3();
const _s = new THREE.Vector3();

export class Coins {
  constructor(parent) {
    const geo = new THREE.CylinderGeometry(0.4, 0.4, 0.09, 20);
    const face = new THREE.MeshLambertMaterial({ map: TX.coinFace(), emissive: 0x553300 });
    const side = new THREE.MeshLambertMaterial({ color: 0xffb000, emissive: 0x442200 });
    this.mesh = new THREE.InstancedMesh(geo, [side, face, face], COIN_POOL);
    this.mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    this.mesh.frustumCulled = false;
    parent.add(this.mesh);
    this.items = [];
    for (let i = 0; i < COIN_POOL; i++) this.items.push({ active: false, x: 0, y: 0, z: 0, fly: false });
    this.spin = 0;
    this.reset();
  }

  reset() {
    for (const c of this.items) c.active = false;
    this._write();
  }

  // z is in scroller-local coordinates
  spawn(x, y, z) {
    for (const c of this.items) {
      if (!c.active) { c.active = true; c.x = x; c.y = y; c.z = z; c.fly = false; return true; }
    }
    return false;
  }

  // Returns array of world positions of collected coins
  update(dt, player, distance, magnet, out) {
    this.spin += dt * 4;
    const pz = -distance; // player in local coords
    const py = player.y + 0.8;
    for (const c of this.items) {
      if (!c.active) continue;
      const wz = c.z + distance;
      if (wz > 10) { c.active = false; continue; }
      if (magnet && !c.fly && wz > -28 && wz < 1 && Math.abs(c.x - player.x) < 5) c.fly = true;
      if (c.fly) {
        const k = Math.min(1, dt * 12);
        c.x += (player.x - c.x) * k;
        c.y += (py - c.y) * k;
        c.z += (pz - c.z) * k + dt * 6;
      }
      const dz = c.z - pz;
      if (Math.abs(dz) < 0.75 && Math.abs(c.x - player.x) < 0.85 && c.y > player.y - 0.4 && c.y < player.y + 2.0) {
        c.active = false;
        out.push(c.x, c.y, wz);
      }
    }
    this._write();
  }

  _write() {
    let n = 0;
    _e.set(Math.PI / 2, this.spin, 0);
    _q.setFromEuler(_e);
    _s.set(1, 1, 1);
    for (const c of this.items) {
      if (!c.active) continue;
      _p.set(c.x, c.y, c.z);
      _m.compose(_p, _q, _s);
      this.mesh.setMatrixAt(n++, _m);
    }
    this.mesh.count = n;
    this.mesh.instanceMatrix.needsUpdate = true;
  }
}
