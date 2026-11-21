// Geometry helpers: build a prefab from many primitive parts and merge them
// into one vertex-coloured mesh per material channel -> very few draw calls.
import * as THREE from 'three';

const _m = new THREE.Matrix4();
const _q = new THREE.Quaternion();
const _e = new THREE.Euler();
const _s = new THREE.Vector3();
const _p = new THREE.Vector3();
const _c = new THREE.Color();

export class Parts {
  constructor() { this.ch = {}; }

  add(geo, color, x = 0, y = 0, z = 0, o = {}) {
    const g = geo.index ? geo.toNonIndexed() : geo.clone();
    geo.dispose();
    const r = o.rot || [0, 0, 0];
    _e.set(r[0], r[1], r[2]);
    _q.setFromEuler(_e);
    const sc = o.scale || [1, 1, 1];
    _m.compose(_p.set(x, y, z), _q, _s.set(sc[0], sc[1], sc[2]));
    g.applyMatrix4(_m);
    _c.set(color);
    const n = g.attributes.position.count;
    const col = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) { col[i * 3] = _c.r; col[i * 3 + 1] = _c.g; col[i * 3 + 2] = _c.b; }
    g.setAttribute('color', new THREE.BufferAttribute(col, 3));
    const ch = o.ch || 'solid';
    (this.ch[ch] || (this.ch[ch] = [])).push(g);
    return this;
  }

  box(w, h, d, x, y, z, color, o) { return this.add(new THREE.BoxGeometry(w, h, d), color, x, y, z, o); }
  cyl(rt, rb, h, seg, x, y, z, color, o) { return this.add(new THREE.CylinderGeometry(rt, rb, h, seg), color, x, y, z, o); }
  sphere(r, x, y, z, color, o = {}) { return this.add(new THREE.SphereGeometry(r, o.ws || 8, o.hs || 6), color, x, y, z, o); }
  ico(r, x, y, z, color, o = {}) { return this.add(new THREE.IcosahedronGeometry(r, o.detail || 0), color, x, y, z, o); }
  cone(r, h, seg, x, y, z, color, o) { return this.add(new THREE.ConeGeometry(r, h, seg), color, x, y, z, o); }
  torus(r, t, x, y, z, color, o = {}) { return this.add(new THREE.TorusGeometry(r, t, o.rs || 6, o.ts || 12, o.arc || Math.PI * 2), color, x, y, z, o); }

  build() {
    const out = {};
    for (const k of Object.keys(this.ch)) out[k] = mergeColored(this.ch[k]);
    return out;
  }
}

export function mergeColored(list) {
  let total = 0;
  for (const g of list) total += g.attributes.position.count;
  const pos = new Float32Array(total * 3), nor = new Float32Array(total * 3), col = new Float32Array(total * 3);
  let off = 0;
  for (const g of list) {
    pos.set(g.attributes.position.array, off * 3);
    nor.set(g.attributes.normal.array, off * 3);
    col.set(g.attributes.color.array, off * 3);
    off += g.attributes.position.count;
    g.dispose();
  }
  const out = new THREE.BufferGeometry();
  out.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  out.setAttribute('normal', new THREE.BufferAttribute(nor, 3));
  out.setAttribute('color', new THREE.BufferAttribute(col, 3));
  out.computeBoundingSphere();
  return out;
}

// Plane lying on the ground (XZ), with UVs scaled so a texture tiles every tileW x tileL metres.
export function groundPlane(w, l, tileW, tileL) {
  const g = new THREE.PlaneGeometry(w, l);
  g.rotateX(-Math.PI / 2);
  const uv = g.attributes.uv;
  for (let i = 0; i < uv.count; i++) uv.setXY(i, uv.getX(i) * (w / tileW), uv.getY(i) * (l / tileL));
  return g;
}

export const rand = (a, b) => a + Math.random() * (b - a);
export const randi = (a, b) => Math.floor(rand(a, b + 1));
export const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];
export const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
export const lerp = (a, b, t) => a + (b - a) * t;

export function weighted(weights) {
  let sum = 0;
  for (const k in weights) sum += weights[k];
  let r = Math.random() * sum;
  for (const k in weights) { r -= weights[k]; if (r <= 0) return k; }
  return Object.keys(weights)[0];
}
