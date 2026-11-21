// Procedural low-poly models: Baku buildings, street furniture, vehicles and
// road obstacles. Each prefab is built once, merged, then cheaply instanced.
// To add a new model: register a builder in DEFS and reference its key from a
// theme (themes.js) or obstacle type (obstacleTypes.js).
import * as THREE from 'three';
import { Parts, pick, rand, randi } from './geom.js';
import { MAT, texMat, headlightPool } from './materials.js';
import * as TX from './textures.js';

const PI = Math.PI;
const DEFS = {};
const built = new Map();

export function registerPrefab(key, fn) { DEFS[key] = fn; }

export function createPrefab(key) {
  let def = built.get(key);
  if (!def) {
    if (!DEFS[key]) throw new Error('Unknown prefab ' + key);
    const r = DEFS[key]();
    def = { geos: r.parts.build(), extras: r.extras, meta: r.meta || {} };
    built.set(key, def);
  }
  const g = new THREE.Group();
  for (const ch of Object.keys(def.geos)) {
    const m = new THREE.Mesh(def.geos[ch], MAT[ch]);
    m.matrixAutoUpdate = false;
    g.add(m);
  }
  if (def.extras) def.extras(g);
  g.userData.meta = def.meta;
  return g;
}

function texPlane(w, h, mat, x, y, z, ry = 0, rx = 0) {
  const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat);
  m.position.set(x, y, z); m.rotation.set(rx, ry, 0);
  m.updateMatrix(); m.matrixAutoUpdate = false;
  return m;
}

// ---------------------------------------------------------------- buildings
// Buildings face +X (toward road when placed on the left side). Length runs along Z.
const SAND = ['#e3bb7c', '#d8a867', '#ebc68e', '#d39f6c', '#e6c79f', '#cfa577'];

function classicBuilding(L, floors, color, variant) {
  const p = new Parts();
  const D = 9, fh = 3.6, H = floors * fh + 1.0;
  const dark = new THREE.Color(color).multiplyScalar(0.82).getStyle();
  p.box(D, H, L, -D / 2, H / 2, 0, color);
  p.box(0.35, fh, L + 0.05, 0.1, fh / 2, 0, dark);
  for (let f = 1; f < floors; f++) p.box(0.4, 0.28, L + 0.2, 0.15, f * fh, 0, '#f1d9ae');
  p.box(0.8, 0.55, L + 0.5, 0.3, H - 0.45, 0, '#f1d9ae');
  p.box(0.4, 0.8, L + 0.2, 0.0, H + 0.4, 0, color);
  if (variant % 2 === 0) p.box(D - 1.5, 1.6, L - 1.5, -D / 2, H + 0.8, 0, '#7c5d4a');
  else p.box(D - 1.5, 0.6, L - 1.5, -D / 2, H + 0.3, 0, '#a8866a');
  const n = Math.max(2, Math.floor(L / 2.6));
  const step = L / n;
  for (let i = 0; i < n; i++) {
    const z = -L / 2 + step * (i + 0.5);
    // ground floor shop arches
    p.box(0.12, 2.3, step * 0.62, 0.06, 1.35, z, '#e9f2ff', { ch: 'glow' });
    p.box(0.18, 0.5, step * 0.75, 0.08, 2.65, z, dark);
    for (let f = 1; f < floors; f++) {
      const y = f * fh + 1.75;
      p.box(0.22, 2.2, 1.2, 0.04, y, z, '#f4e2c0');
      p.box(0.12, 1.85, 0.88, 0.1, y, z, '#e9f2ff', { ch: 'glow' });
      p.box(0.3, 0.25, 1.4, 0.1, y + 1.25, z, '#f4e2c0');
      if (f === 1 && (i + variant) % 2 === 0) {
        p.box(1.0, 0.16, 1.7, 0.5, y - 1.0, z, '#d9c4a0');
        p.box(0.05, 0.75, 1.7, 0.98, y - 0.55, z, '#1e1e22');
        p.box(0.9, 0.05, 0.05, 0.5, y - 0.2, z - 0.82, '#1e1e22');
        p.box(0.9, 0.05, 0.05, 0.5, y - 0.2, z + 0.82, '#1e1e22');
      }
    }
  }
  return p;
}

for (let v = 0; v < 6; v++) {
  registerPrefab('bld_classic_' + v, () => {
    const L = [9, 11, 13, 10, 12, 14][v];
    const floors = [3, 4, 3, 4, 5, 3][v];
    const p = classicBuilding(L, floors, SAND[v], v);
    return { parts: p, meta: { length: L } };
  });
}

// Old City (Icherisheher) houses with wooden "eyvan" balconies
for (let v = 0; v < 4; v++) {
  registerPrefab('bld_old_' + v, () => {
    const p = new Parts();
    const L = [8, 10, 9, 11][v], D = 8, H = [6.5, 7.5, 6, 8][v];
    const col = ['#dcc59c', '#d4b88a', '#e2cda6', '#cdb083'][v];
    p.box(D, H, L, -D / 2, H / 2, 0, col);
    p.box(0.3, 0.5, L + 0.2, 0.1, H, 0, '#c4a77c');
    // arched door
    p.box(0.12, 2.4, 1.3, 0.06, 1.2, -L / 4, '#6b4426');
    p.cyl(0.65, 0.65, 0.12, 10, 0.06, 2.4, -L / 4, '#6b4426', { rot: [0, 0, PI / 2] });
    // small windows
    for (let i = 0; i < 3; i++) p.box(0.1, 0.9, 0.7, 0.05, 2.1, L / 8 + i * 1.4 - 1, '#ffe2a8', { ch: 'glow' });
    // wooden eyvan balcony
    const bl = L * 0.55;
    p.box(1.2, 2.4, bl, 0.6, H - 2.2, 0.5, '#7a4a28');
    for (let i = 0; i < 4; i++) p.box(0.06, 1.3, bl / 4 - 0.25, 1.22, H - 1.9, 0.5 - bl / 2 + bl / 8 + i * bl / 4, '#ffe2a8', { ch: 'glow' });
    p.box(1.4, 0.2, bl + 0.2, 0.6, H - 0.9, 0.5, '#5c341a');
    return { parts: p, meta: { length: L } };
  });
}

registerPrefab('old_wall', () => {
  const p = new Parts();
  const L = 20, H = 6.5, c = '#c8a674';
  p.box(3, H, L, -1.5, H / 2, 0, c);
  for (let z = -L / 2 + 0.6; z < L / 2; z += 1.7) p.box(3, 1.1, 0.95, -1.5, H + 0.55, z, '#bf9c6a');
  p.cyl(2.6, 3.0, H + 2.5, 12, -1.2, (H + 2.5) / 2, 0, '#bf9b67');
  for (let a = 0; a < 8; a++) p.box(0.8, 1, 0.8, -1.2 + Math.cos(a * PI / 4) * 2.4, H + 3, Math.sin(a * PI / 4) * 2.4, '#b8945f');
  p.box(0.15, 1.2, 0.5, 0.0, H - 1.5, -5, '#3a2a1a');
  p.box(0.15, 1.2, 0.5, 0.0, H - 1.5, 5, '#3a2a1a');
  return { parts: p, meta: { length: L } };
});

// Modern glass towers
const GLASS = ['#4f7fae', '#3f6f9a', '#6a93b8', '#55729a'];
for (let v = 0; v < 4; v++) {
  registerPrefab('bld_glass_' + v, () => {
    const p = new Parts();
    const L = [12, 14, 11, 13][v], D = 11, H = [32, 46, 26, 58][v];
    p.box(D, H, L, -D / 2, H / 2, 0, GLASS[v]);
    p.box(D + 0.4, 4, L + 0.4, -D / 2, 2, 0, '#2f3d4d');
    p.box(0.1, 3, L - 1, 0.25, 1.7, 0, '#e9f2ff', { ch: 'glow' });
    for (let y = 6; y < H - 2; y += 3.4) p.box(0.08, 1.7, L - 0.8, 0.04, y, 0, '#dfeaff', { ch: 'glow' });
    for (let z = -L / 2 + 1; z <= L / 2 - 1; z += 2.4) p.box(0.25, H - 4, 0.18, 0.1, H / 2 + 2, z, '#c7d3df');
    p.box(D - 2, 2.5, L - 2, -D / 2, H + 1.25, 0, '#8a99a8');
    if (v % 2) p.cyl(0.15, 0.15, 6, 6, -D / 2, H + 5, 0, '#ddd');
    return { parts: p, meta: { length: L } };
  });
}

// ---------------------------------------------------------------- street props
registerPrefab('lamp_classic', () => {
  const p = new Parts();
  p.cyl(0.32, 0.38, 0.6, 8, 0, 0.3, 0, '#151515');
  p.cyl(0.1, 0.14, 4.4, 8, 0, 2.6, 0, '#1b1b1b');
  p.box(0.08, 0.08, 1.4, 0, 4.4, 0, '#1b1b1b');
  for (const z of [-0.7, 0.7]) {
    p.cyl(0.32, 0.18, 0.2, 8, 0, 4.15, z, '#1b1b1b');
    p.cyl(0.2, 0.28, 0.6, 8, 0, 3.85, z, '#fff2c4', { ch: 'glow' });
    p.cone(0.36, 0.35, 8, 0, 4.35, z, '#1b1b1b');
  }
  p.cyl(0.26, 0.2, 0.9, 8, 0, 4.9, 0, '#fff2c4', { ch: 'glow' });
  p.cone(0.36, 0.45, 8, 0, 5.55, 0, '#1b1b1b');
  return { parts: p };
});

registerPrefab('lamp_old', () => {
  const p = new Parts();
  p.cyl(0.08, 0.1, 3.4, 6, 0, 1.7, 0, '#2a2018');
  p.box(0.9, 0.08, 0.08, 0.35, 3.3, 0, '#2a2018');
  p.box(0.35, 0.5, 0.35, 0.75, 3.0, 0, '#ffd58a', { ch: 'glow' });
  p.cone(0.3, 0.3, 4, 0.75, 3.4, 0, '#2a2018', { rot: [0, PI / 4, 0] });
  return { parts: p };
});

registerPrefab('lamp_modern', () => {
  const p = new Parts();
  p.cyl(0.09, 0.13, 6.5, 8, 0, 3.25, 0, '#9aa4ae');
  p.box(1.8, 0.12, 0.25, 0.85, 6.5, 0, '#9aa4ae');
  p.box(0.9, 0.06, 0.22, 1.3, 6.42, 0, '#f2f8ff', { ch: 'glow' });
  return { parts: p };
});

registerPrefab('tree', () => {
  const p = new Parts();
  p.cyl(0.14, 0.22, 2.4, 6, 0, 1.2, 0, '#6b4a2e');
  p.ico(1.5, 0, 3.4, 0, '#4f9a35', { detail: 1 });
  p.ico(1.1, 0.6, 4.3, 0.3, '#5fae3e', { detail: 0 });
  p.ico(1.0, -0.5, 4.0, -0.5, '#468a2f', { detail: 0 });
  return { parts: p };
});

registerPrefab('tree_round', () => {
  const p = new Parts();
  p.cyl(0.12, 0.18, 1.8, 6, 0, 0.9, 0, '#5d4027');
  p.sphere(1.3, 0, 2.8, 0, '#3f8f3a', { ws: 8, hs: 6 });
  p.box(1.2, 0.5, 1.2, 0, 0.25, 0, '#8a8f96');
  return { parts: p };
});

registerPrefab('palm', () => {
  const p = new Parts();
  for (let i = 0; i < 6; i++) p.cyl(0.17 - i * 0.01, 0.2 - i * 0.01, 1.0, 6, i * 0.07, 0.5 + i * 0.95, 0, '#8b6a43', { rot: [0, 0, -0.07] });
  for (let a = 0; a < 7; a++) {
    const ang = a / 7 * PI * 2;
    p.box(2.6, 0.06, 0.5, 0.45 + Math.cos(ang) * 1.1, 5.7, Math.sin(ang) * 1.1, '#3f8f2c', { rot: [0, -ang, -0.45] });
  }
  return { parts: p };
});

registerPrefab('planter', () => {
  const p = new Parts();
  p.box(1.1, 0.7, 1.1, 0, 0.35, 0, '#b49580');
  p.box(1.0, 0.1, 1.0, 0, 0.72, 0, '#4a3020');
  const cols = ['#ff3d7f', '#ff7a1a', '#e81f4e', '#ff9ed2', '#ffd23a'];
  for (let i = 0; i < 9; i++) p.ico(0.17, (i % 3 - 1) * 0.3, 0.85 + Math.random() * 0.1, (Math.floor(i / 3) - 1) * 0.3, pick(cols));
  p.ico(0.2, 0, 0.82, 0, '#3c8a2a');
  return { parts: p };
});

registerPrefab('bench', () => {
  const p = new Parts();
  p.box(0.5, 0.08, 1.8, 0, 0.5, 0, '#8a5a32');
  p.box(0.08, 0.5, 1.8, -0.25, 0.8, 0, '#8a5a32');
  p.box(0.4, 0.5, 0.08, 0, 0.25, -0.8, '#222');
  p.box(0.4, 0.5, 0.08, 0, 0.25, 0.8, '#222');
  return { parts: p };
});

registerPrefab('railing', () => {
  const p = new Parts();
  for (let z = -10; z < 10; z += 2) p.box(0.1, 1.1, 0.1, 0, 0.55, z, '#1c1c1c');
  p.box(0.08, 0.08, 20, 0, 1.05, 0, '#1c1c1c');
  p.box(0.05, 0.05, 20, 0, 0.6, 0, '#1c1c1c');
  p.box(0.6, 0.3, 20, 0.3, 0.15, 0, '#b8b0a6');
  return { parts: p };
});

registerPrefab('busstop', () => {
  const p = new Parts();
  p.box(1.6, 0.1, 4, 0, 2.6, 0, '#c0262d');
  p.box(0.1, 2.5, 4, -0.75, 1.3, 0, '#bfe0ff', { ch: 'glow' });
  for (const z of [-1.9, 1.9]) p.cyl(0.05, 0.05, 2.6, 6, -0.7, 1.3, z, '#555');
  p.box(0.4, 0.08, 3, -0.4, 0.5, 0, '#888');
  p.box(0.2, 0.7, 0.7, 0.7, 2.9, 1.6, '#c0262d');
  return {
    parts: p,
    extras: (g) => g.add(texPlane(0.65, 0.65, texMat(TX.textSign('BUS', '#c0262d', '#fff', 64, 64, 'bold 24px sans-serif')), 0.81, 2.9, 1.6, PI / 2)),
  };
});

registerPrefab('flagpole_small', () => {
  const p = new Parts();
  p.cyl(0.05, 0.06, 6, 6, 0, 3, 0, '#d8d8d8');
  return {
    parts: p,
    extras: (g) => {
      const m = new THREE.Mesh(new THREE.PlaneGeometry(1.8, 0.9), texMat(TX.flag(), { side: THREE.DoubleSide }));
      m.position.set(0, 5.4, 0.92); m.rotation.y = PI / 2;
      g.add(m);
    },
  };
});

// ---------------------------------------------------------------- vehicles
// Vehicles face +Z (toward the player). Length along Z.
const PLATES = [];
function plateMat() {
  if (PLATES.length < 10) {
    const L = 'ABCDEJKLMNOPRSTVXYZ';
    const t = `${randi(10, 99)}-${L[randi(0, L.length - 1)]}${L[randi(0, L.length - 1)]}-${randi(100, 999)}`;
    PLATES.push(texMat(TX.licensePlate(t)));
  }
  return pick(PLATES);
}

function addHeadlightPool(g, w, len) {
  if (!headlightPool.map) headlightPool.map = TX.radial('hl', 'rgba(255,255,255,1)', 'rgba(255,255,255,0)');
  const m = new THREE.Mesh(new THREE.PlaneGeometry(w * 1.8, 7), headlightPool);
  m.rotation.x = -PI / 2; m.position.set(0, 0.04, len / 2 + 3.6);
  m.renderOrder = 2;
  g.add(m);
}

function sedan(color, o = {}) {
  const p = new Parts();
  const L = 4.4, W = 1.8;
  const dark = new THREE.Color(color).multiplyScalar(0.7).getStyle();
  p.box(W, 0.62, L, 0, 0.62, 0, color);
  p.box(W - 0.06, 0.2, L - 0.3, 0, 1.0, -0.05, color);
  // cabin (glass) + roof
  p.box(W - 0.22, 0.52, 2.1, 0, 1.33, -0.3, '#1d2733');
  p.box(W - 0.3, 0.1, 1.8, 0, 1.62, -0.35, color);
  p.box(W - 0.1, 0.05, 1.0, 0, 1.14, 1.0, color, { rot: [-0.35, 0, 0] });
  // bumpers, grille
  p.box(W + 0.04, 0.25, 0.2, 0, 0.42, L / 2, '#2a2a2e');
  p.box(W + 0.04, 0.25, 0.2, 0, 0.42, -L / 2, '#2a2a2e');
  p.box(0.9, 0.2, 0.05, 0, 0.75, L / 2 + 0.01, '#15171a');
  // lights
  p.box(0.42, 0.16, 0.05, -0.6, 0.78, L / 2 + 0.02, '#fffbe0', { ch: 'light' });
  p.box(0.42, 0.16, 0.05, 0.6, 0.78, L / 2 + 0.02, '#fffbe0', { ch: 'light' });
  p.box(0.4, 0.14, 0.05, -0.62, 0.8, -L / 2 - 0.02, '#ff2020', { ch: 'light' });
  p.box(0.4, 0.14, 0.05, 0.62, 0.8, -L / 2 - 0.02, '#ff2020', { ch: 'light' });
  // mirrors
  p.box(0.25, 0.15, 0.1, -W / 2 - 0.1, 1.15, 0.6, dark);
  p.box(0.25, 0.15, 0.1, W / 2 + 0.1, 1.15, 0.6, dark);
  // wheels
  for (const x of [-W / 2 + 0.05, W / 2 - 0.05]) for (const z of [-1.35, 1.35]) {
    p.cyl(0.36, 0.36, 0.28, 12, x, 0.36, z, '#16161a', { rot: [0, 0, PI / 2] });
    p.cyl(0.2, 0.2, 0.3, 8, x, 0.36, z, '#b8bcc4', { rot: [0, 0, PI / 2] });
  }
  if (o.taxiSign) {
    p.box(0.7, 0.25, 0.3, 0, 1.8, -0.3, '#ffe15a', { ch: 'light' });
    p.box(W + 0.02, 0.12, 2.4, 0, 0.95, 0, '#222'); // checker band
  }
  return p;
}

const CAR_COLORS = { silver: '#b9bec6', white: '#eef0f2', black: '#25272c', red: '#c92a2a', blue: '#2a5bc9', green: '#2f8f5b' };
for (const [name, col] of Object.entries(CAR_COLORS)) {
  registerPrefab('car_' + name, () => ({
    parts: sedan(col),
    meta: { length: 4.4, width: 1.8 },
    extras: (g) => { g.add(texPlane(0.62, 0.14, plateMat(), 0, 0.42, 2.312)); addHeadlightPool(g, 1.8, 4.4); },
  }));
}

registerPrefab('taxi_yellow', () => ({
  parts: sedan('#ffc81e', { taxiSign: true }),
  meta: { length: 4.4, width: 1.8 },
  extras: (g) => {
    g.add(texPlane(0.62, 0.14, plateMat(), 0, 0.42, 2.312));
    g.add(texPlane(0.66, 0.2, texMat(TX.textSign('TAXI', '#ffe15a', '#111', 128, 40, 'bold 30px sans-serif'), { basic: true }), 0, 1.8, -0.14));
    addHeadlightPool(g, 1.8, 4.4);
  },
}));

// Classic purple Baku cab (London-style)
registerPrefab('taxi_cab', () => {
  const p = new Parts();
  const L = 4.6, W = 1.85, c = '#6a2b8f';
  p.box(W, 0.75, L, 0, 0.7, 0, c);
  p.box(W - 0.1, 0.85, 2.6, 0, 1.45, -0.25, c);
  p.box(W - 0.05, 0.55, 2.4, 0, 1.5, -0.25, '#1b2230');
  p.box(W - 0.2, 0.08, 2.5, 0, 1.93, -0.25, c);
  p.box(0.6, 0.22, 0.3, 0, 2.05, 0.4, '#ffb000', { ch: 'light' });
  p.box(W + 0.06, 0.25, 0.2, 0, 0.42, L / 2, '#c9c9c9');
  p.box(1.0, 0.45, 0.06, 0, 0.85, L / 2 + 0.01, '#c9c9c9');
  for (const x of [-0.65, 0.65]) {
    p.cyl(0.17, 0.17, 0.08, 10, x, 0.92, L / 2 + 0.02, '#fffbe0', { ch: 'light', rot: [PI / 2, 0, 0] });
    p.box(0.3, 0.12, 0.05, x, 0.9, -L / 2 - 0.02, '#ff2020', { ch: 'light' });
  }
  for (const x of [-W / 2 + 0.05, W / 2 - 0.05]) for (const z of [-1.45, 1.45]) {
    p.cyl(0.38, 0.38, 0.28, 12, x, 0.38, z, '#16161a', { rot: [0, 0, PI / 2] });
    p.cyl(0.2, 0.2, 0.3, 8, x, 0.38, z, '#c4c4c4', { rot: [0, 0, PI / 2] });
  }
  return {
    parts: p, meta: { length: 4.6, width: 1.85 },
    extras: (g) => { g.add(texPlane(0.62, 0.14, plateMat(), 0, 0.42, 2.412)); addHeadlightPool(g, 1.85, 4.6); },
  };
});

const ROUTES = ['5', '13', '65', '88', '1', '3', '125'];
for (let v = 0; v < 2; v++) {
  registerPrefab('bus_' + v, () => {
    const p = new Parts();
    const L = 11, W = 2.5, H = 3.1;
    const red = v ? '#d5202a' : '#c41820';
    p.box(W, 1.3, L, 0, 0.95, 0, red);
    p.box(W, 1.35, L - 0.2, 0, 2.27, -0.1, '#1a2028');
    p.box(W, 0.4, L, 0, 3.0, 0, '#f0f0f0');
    p.box(W - 0.3, 0.3, L * 0.4, 0, 3.35, -1, '#d8d8d8');
    // windscreen frame and sign
    p.box(W, 0.45, 0.06, 0, 2.95, L / 2 + 0.02, '#111');
    p.box(W, 1.3, 0.08, 0, 0.95, L / 2, '#1a1a1a');
    p.box(W - 0.3, 0.6, 0.06, 0, 0.95, L / 2 + 0.03, red);
    for (const x of [-0.95, 0.95]) {
      p.box(0.38, 0.2, 0.06, x, 0.55, L / 2 + 0.05, '#fffbe0', { ch: 'light' });
      p.box(0.3, 0.3, 0.06, x, 1.2, -L / 2 - 0.03, '#ff2020', { ch: 'light' });
    }
    p.box(0.25, 0.4, 0.15, -W / 2 - 0.18, 2.3, L / 2 - 0.2, '#111');
    p.box(0.25, 0.4, 0.15, W / 2 + 0.18, 2.3, L / 2 - 0.2, '#111');
    for (const x of [-W / 2 + 0.08, W / 2 - 0.08]) for (const z of [-3.6, 3.4]) {
      p.cyl(0.5, 0.5, 0.32, 12, x, 0.5, z, '#121214', { rot: [0, 0, PI / 2] });
      p.cyl(0.26, 0.26, 0.34, 8, x, 0.5, z, '#aaaaaa', { rot: [0, 0, PI / 2] });
    }
    return {
      parts: p, meta: { length: L, width: W },
      extras: (g) => {
        const signMat = texMat(TX.busSign(ROUTES[v * 3 % ROUTES.length]), { basic: true });
        g.add(texPlane(W - 0.25, 0.38, signMat, 0, 2.95, L / 2 + 0.06));
        const side = texMat(TX.busSide(), { transparent: true });
        g.add(texPlane(5, 1.25, side, W / 2 + 0.01, 0.95, 0, PI / 2));
        g.add(texPlane(5, 1.25, side, -W / 2 - 0.01, 0.95, 0, -PI / 2));
        const brand = texMat(TX.textSign('BakuBus', '#c41820', '#fff', 256, 64, 'bold 40px sans-serif'));
        g.add(texPlane(1.5, 0.38, brand, 0, 1.0, L / 2 + 0.07));
        g.add(texPlane(0.7, 0.16, plateMat(), 0, 0.34, L / 2 + 0.07));
        addHeadlightPool(g, 2.5, L);
      },
    };
  });
}

// ---------------------------------------------------------------- road obstacles
registerPrefab('cones', () => {
  const p = new Parts();
  for (const x of [-0.65, 0, 0.65]) {
    p.box(0.55, 0.06, 0.55, x, 0.03, 0, '#1a1a1a');
    p.cone(0.24, 0.75, 10, x, 0.42, 0, '#ff6a00');
    p.cyl(0.14, 0.18, 0.12, 10, x, 0.45, 0, '#ffffff');
  }
  return { parts: p, meta: { length: 0.6, width: 1.9 } };
});

registerPrefab('barrier', () => {
  const p = new Parts();
  for (const x of [-0.85, 0.85]) {
    p.box(0.08, 1.0, 0.08, x, 0.5, 0, '#ddd');
    p.box(0.5, 0.06, 0.6, x, 0.03, 0, '#333');
  }
  p.box(1.9, 0.1, 0.12, 0, 0.35, 0, '#ddd');
  return {
    parts: p, meta: { length: 0.5, width: 1.9 },
    extras: (g) => {
      const m = texMat(TX.stripes(), { side: THREE.DoubleSide });
      g.add(texPlane(1.9, 0.4, m, 0, 0.78, 0.07));
      g.add(texPlane(1.9, 0.4, m, 0, 0.78, -0.07, PI));
      const l = new THREE.Mesh(new THREE.SphereGeometry(0.09, 8, 6), new THREE.MeshBasicMaterial({ color: 0xffa000 }));
      l.position.set(-0.85, 1.06, 0); g.add(l);
      const l2 = l.clone(); l2.position.x = 0.85; g.add(l2);
    },
  };
});

// Overhead road-repair gantry: you must slide under it
registerPrefab('gantry', () => {
  const p = new Parts();
  for (const x of [-0.95, 0.95]) {
    p.box(0.14, 2.6, 0.14, x, 1.3, 0, '#e0e0e0');
    p.box(0.6, 0.12, 0.7, x, 0.06, 0, '#444');
  }
  p.box(2.0, 0.12, 0.2, 0, 2.6, 0, '#cfcfcf');
  return {
    parts: p, meta: { length: 0.5, width: 2.0 },
    extras: (g) => {
      const m = texMat(TX.hazard(), { side: THREE.DoubleSide });
      g.add(texPlane(1.95, 1.0, m, 0, 1.75, 0.08));
      g.add(texPlane(1.95, 1.0, m, 0, 1.75, -0.08, PI));
      const lm = new THREE.MeshBasicMaterial({ color: 0xff3a2a });
      for (const x of [-0.75, 0.75]) {
        const l = new THREE.Mesh(new THREE.SphereGeometry(0.12, 8, 6), lm);
        l.position.set(x, 2.35, 0.12); g.add(l);
      }
    },
  };
});

// Tall construction fence (cannot be jumped)
registerPrefab('fence', () => {
  const p = new Parts();
  p.box(2.0, 0.5, 0.9, 0, 0.25, 0, '#c9c9c9');
  for (const x of [-0.9, 0, 0.9]) p.box(0.08, 2.3, 0.08, x, 1.4, 0, '#888');
  return {
    parts: p, meta: { length: 0.9, width: 2.0 },
    extras: (g) => {
      const m = texMat(TX.stripes(), { side: THREE.DoubleSide });
      g.add(texPlane(1.95, 1.6, m, 0, 1.5, 0.05));
      g.add(texPlane(1.95, 1.6, m, 0, 1.5, -0.05, PI));
      const w = new THREE.Mesh(new THREE.PlaneGeometry(0.9, 0.8), texMat(TX.warning(), { transparent: true, basic: true }));
      w.position.set(0, 1.5, 0.07); g.add(w);
    },
  };
});

// ---------------------------------------------------------------- skyline (far background)
// Flame Towers built by deforming a cylinder into a curved flame shape.
export function flameTowerGeometry(H, W, D, lean) {
  const g = new THREE.CylinderGeometry(1, 1, 1, 28, 30);
  const pos = g.attributes.position;
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
    const t = y + 0.5;
    const w = Math.pow(Math.max(0, 1 - t), 0.55) * (1 + 0.28 * Math.sin(PI * t * 0.9));
    const front = z > 0 ? 1.25 : 0.8;
    pos.setXYZ(i, x * w * W + lean * t * t * H * 0.18, t * H, z * w * D * front);
  }
  g.computeVertexNormals();
  return g;
}

export function addPrefabRandomRot(obj) { obj.rotation.y = rand(0, PI * 2); }
