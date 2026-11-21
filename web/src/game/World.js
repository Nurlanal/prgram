// The city: sky, lighting, background skyline (Flame Towers etc.), road and
// sidewalk segments, buildings and street props. Everything that scrolls lives
// in `scroller`, whose z position equals the distance run, so placed objects
// never need per-frame movement.
import * as THREE from 'three';
import { ROAD_HALF, SIDEWALK_W, SEGMENT_LEN, SPAWN_DIST, DESPAWN_Z, THEME_LENGTH } from '../config.js';
import { THEMES, themeIndexAt } from './themes.js';
import { Parts, groundPlane, pick, rand, lerp } from './geom.js';
import { MAT, headlightPool } from './materials.js';
import { createPrefab, flameTowerGeometry } from './prefabs.js';
import { PoolSet } from './pool.js';
import * as TX from './textures.js';

const FACE_X = ROAD_HALF + SIDEWALK_W; // building front line

function lambertTex(t) { return new THREE.MeshLambertMaterial({ map: t }); }

export class World {
  constructor(scene, quality = 'high') {
    this.scene = scene;
    this.quality = quality;
    this.scroller = new THREE.Group();
    scene.add(this.scroller);
    this.pool = new PoolSet(this.scroller);
    this.groundPool = new PoolSet(this.scroller, (k) => this._buildSegment(k));
    this.active = [];
    this.distance = 0;
    this.time = 0;

    this._initMaterials();
    this._initSky();
    this._initLights();
    this._initSkyline();

    this.themeIndex = 0;
    this.light = this._lightFrom(THEMES[0].light);
    this._applyLight();
    this.reset();
  }

  // ------------------------------------------------------------ setup
  _initMaterials() {
    this.groundMats = {
      asphalt: lambertTex(TX.roadAsphalt()),
      cobble: lambertTex(TX.roadCobble()),
      tiles: lambertTex(TX.sidewalkTiles()),
      stone: lambertTex(TX.stonePave()),
      plaza: lambertTex(TX.plazaTiles()),
      water: new THREE.MeshLambertMaterial({ map: TX.water(), color: 0xffffff }),
    };
  }

  _initSky() {
    const uniforms = {
      top: { value: new THREE.Color() },
      bottom: { value: new THREE.Color() },
    };
    this.skyMat = new THREE.ShaderMaterial({
      uniforms, side: THREE.BackSide, depthWrite: false, fog: false,
      vertexShader: 'varying vec3 vP; void main(){ vP = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
      fragmentShader: 'uniform vec3 top; uniform vec3 bottom; varying vec3 vP; void main(){ float h = clamp(vP.y*2.2+0.08,0.0,1.0); gl_FragColor = vec4(mix(bottom, top, pow(h,0.8)),1.0); }',
    });
    this.sky = new THREE.Mesh(new THREE.SphereGeometry(900, 24, 12), this.skyMat);
    this.sky.renderOrder = -10;
    this.scene.add(this.sky);

    // stars for the night district
    const n = 350, pos = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) {
      const a = Math.random() * Math.PI * 2, e = 0.08 + Math.random() * 0.9;
      pos[i * 3] = Math.cos(a) * Math.cos(e) * 850;
      pos[i * 3 + 1] = Math.sin(e) * 850;
      pos[i * 3 + 2] = Math.sin(a) * Math.cos(e) * 850;
    }
    const sg = new THREE.BufferGeometry();
    sg.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    this.starMat = new THREE.PointsMaterial({ color: 0xffffff, size: 2.2, sizeAttenuation: false, transparent: true, opacity: 0, fog: false, depthWrite: false });
    this.stars = new THREE.Points(sg, this.starMat);
    this.stars.renderOrder = -9;
    this.scene.add(this.stars);

    // setting sun
    this.sunMat = new THREE.SpriteMaterial({ map: TX.radial('sun', 'rgba(255,230,160,1)', 'rgba(255,140,60,0)'), transparent: true, opacity: 0, fog: false, depthWrite: false });
    this.sunSprite = new THREE.Sprite(this.sunMat);
    this.sunSprite.scale.set(160, 160, 1);
    this.sunSprite.position.set(80, 40, -700);
    this.sunSprite.renderOrder = -8;
    this.scene.add(this.sunSprite);
  }

  _initLights() {
    this.hemi = new THREE.HemisphereLight(0xffffff, 0x887766, 1);
    this.scene.add(this.hemi);
    this.sun = new THREE.DirectionalLight(0xffffff, 1.2);
    this.sun.position.set(-30, 60, 25);
    this.scene.add(this.sun);
    this.scene.fog = new THREE.Fog(0xbfe0f8, 60, 175);
  }

  _farMat(basic = false) {
    const m = basic ? MAT.farGlow.clone() : MAT.farSolid.clone();
    m.transparent = true;
    m.opacity = 0;
    return m;
  }

  _initSkyline() {
    this.skyline = {};
    const g = new THREE.Group();
    this.scene.add(g);
    this.skyGroup = g;
    const add = (key, obj, mats) => { obj.visible = false; g.add(obj); this.skyline[key] = { obj, mats, o: 0 }; };

    // Flame Towers
    {
      const grp = new THREE.Group();
      this.flameMatDay = TX.flameLED();
      this.flameMatNight = TX.flameFire();
      const mat = new THREE.MeshBasicMaterial({ map: this.flameMatDay, fog: false, transparent: true, opacity: 0 });
      this.flameMat = mat;
      const specs = [[-26, 112, 8, 6, 0.9], [2, 138, 9, 7, 0.05], [30, 104, 8, 6, -0.9]];
      for (const [x, h, w, d, lean] of specs) {
        const m = new THREE.Mesh(flameTowerGeometry(h, w, d, lean), mat);
        m.position.set(x, -4, 0);
        grp.add(m);
      }
      const baseP = new Parts();
      baseP.box(90, 8, 30, 2, -2, 0, '#9a8a70');
      const base = new THREE.Mesh(baseP.build().solid, this._farMat());
      grp.add(base);
      grp.position.set(8, 0, -420);
      add('flame', grp, [mat, base.material]);
    }
    // TV tower
    {
      const p = new Parts();
      p.cyl(1.6, 3.5, 150, 10, 0, 75, 0, '#e8e8ea');
      p.cyl(9, 7, 7, 14, 0, 112, 0, '#d0d4da');
      p.cyl(7, 9, 3, 14, 0, 117, 0, '#9aa8b8');
      p.cyl(0.5, 0.8, 40, 6, 0, 170, 0, '#e04040');
      const m = new THREE.Mesh(p.build().solid, this._farMat());
      m.position.set(-95, -5, -520);
      add('tv', m, [m.material]);
    }
    // Hills with small houses (upland Baku)
    {
      const p = new Parts();
      for (let i = 0; i < 16; i++) {
        const x = -480 + i * 64 + rand(-15, 15);
        p.sphere(rand(40, 70), x, -30, rand(-30, 30), i % 2 ? '#6f9a4a' : '#7ea455', { ws: 7, hs: 5, scale: [1.4, 0.75, 0.8] });
      }
      for (let i = 0; i < 90; i++) {
        const x = rand(-450, 450), z = rand(-10, 30);
        const y = 6 + Math.random() * 18;
        p.box(rand(4, 9), rand(4, 10), rand(4, 8), x, y, z, pick(['#e8d6b8', '#f0e4cf', '#d9c2a0', '#ffffff']));
      }
      const m = new THREE.Mesh(p.build().solid, this._farMat());
      m.position.set(0, -6, -360);
      add('hills', m, [m.material]);
    }
    // Modern tower cluster
    {
      const p = new Parts(), q = new Parts();
      const xs = [-150, -125, -100, 90, 115, 150, 180];
      xs.forEach((x, i) => {
        const h = 50 + (i * 37) % 60, w = 16 + (i % 3) * 4;
        p.box(w, h, w, x, h / 2, rand(-20, 20), pick(['#5f86ad', '#4a6f96', '#7a9cc0']));
        for (let y = 6; y < h - 3; y += 6) q.box(w + 0.4, 1.4, w + 0.4, x, y, 0, '#cfe0ff', { ch: 'farGlow' });
      });
      const grp = new THREE.Group();
      const ms = this._farMat(), mg = this._farMat(true);
      grp.add(new THREE.Mesh(p.build().solid, ms));
      grp.add(new THREE.Mesh(q.build().farGlow, mg));
      grp.position.set(0, -5, -470);
      add('towers', grp, [ms, mg]);
    }
    // Maiden Tower (Qız Qalası)
    {
      const p = new Parts();
      p.cyl(14, 15, 34, 16, 0, 17, 0, '#cbb08a');
      p.box(9, 30, 12, 14, 15, 0, '#c4a882');
      for (let i = 0; i < 4; i++) p.box(0.4, 2.5, 1.8, 15.2, 6 + i * 7, -3 + (i % 2) * 6, '#4a3a2a');
      p.cyl(15, 15, 1.2, 16, 0, 34, 0, '#b89c76');
      const m = new THREE.Mesh(p.build().solid, this._farMat());
      m.position.set(26, -4, -210);
      m.scale.setScalar(1.25);
      m.rotation.y = 2.4;
      add('maiden', m, [m.material]);
    }
    // National Flag Square flagpole with a big waving flag
    {
      const grp = new THREE.Group();
      const p = new Parts();
      p.cyl(1, 1.6, 160, 8, 0, 80, 0, '#e5e5e5');
      p.cyl(12, 14, 4, 16, 0, 2, 0, '#b0a090');
      const pm = this._farMat();
      grp.add(new THREE.Mesh(p.build().solid, pm));
      const fg = new THREE.PlaneGeometry(60, 30, 20, 4);
      fg.translate(30, 0, 0);
      const fm = new THREE.MeshBasicMaterial({ map: TX.flag(), side: THREE.DoubleSide, fog: false, transparent: true, opacity: 0 });
      this.bigFlag = new THREE.Mesh(fg, fm);
      this.bigFlag.position.set(0, 142, 0);
      this.bigFlagBase = Float32Array.from(fg.attributes.position.array);
      grp.add(this.bigFlag);
      grp.position.set(75, -6, -430);
      add('bigflag', grp, [pm, fm]);
    }
  }

  // ------------------------------------------------------------ ground segments
  _buildSegment(key) {
    const [road, walk, right, walkR] = key.split('|');
    const g = new THREE.Group();
    const L = SEGMENT_LEN;
    const roadTile = road === 'asphalt' ? [7.2, 10] : [2.2, 2.2];
    const r = new THREE.Mesh(groundPlane(ROAD_HALF * 2, L, roadTile[0], roadTile[1]), this.groundMats[road]);
    g.add(r);
    const tile = walk === 'plaza' ? 3 : 2;
    const lw = 34;
    const lwalk = new THREE.Mesh(groundPlane(lw, L, tile, tile), this.groundMats[walk]);
    lwalk.position.set(-ROAD_HALF - lw / 2, 0.16, 0);
    g.add(lwalk);
    const tileR = walkR === 'plaza' ? 3 : 2;
    const curbs = new Parts();
    curbs.box(0.25, 0.2, L, -ROAD_HALF - 0.05, 0.08, 0, '#d9d4cc');
    curbs.box(0.25, 0.2, L, ROAD_HALF + 0.05, 0.08, 0, '#d9d4cc');
    if (right === 'sea') {
      const rw = 6.0;
      const rwalk = new THREE.Mesh(groundPlane(rw, L, tileR, tileR), this.groundMats[walkR]);
      rwalk.position.set(ROAD_HALF + rw / 2, 0.16, 0);
      g.add(rwalk);
      const sea = new THREE.Mesh(groundPlane(420, L, 14, 14), this.groundMats.water);
      sea.position.set(ROAD_HALF + rw + 210, -0.8, 0);
      g.add(sea);
      curbs.box(0.4, 1.0, L, ROAD_HALF + rw + 0.2, -0.32, 0, '#b8aa96');
    } else {
      const rwalk = new THREE.Mesh(groundPlane(lw, L, tileR, tileR), this.groundMats[walkR]);
      rwalk.position.set(ROAD_HALF + lw / 2, 0.16, 0);
      g.add(rwalk);
    }
    g.add(new THREE.Mesh(curbs.build().solid, MAT.solid));
    for (const m of g.children) { m.updateMatrix(); m.matrixAutoUpdate = false; }
    return g;
  }

  // ------------------------------------------------------------ spawning
  themeAt(roadZ) { return THEMES[themeIndexAt(roadZ, THEME_LENGTH)]; }

  _place(key, x, roadZ, half, rotY = 0) {
    const o = this.pool.get(key);
    o.position.set(x, 0, -roadZ);
    o.rotation.set(0, rotY, 0);
    this.active.push({ obj: o, roadZ, half, pool: this.pool });
    return o;
  }

  _spawnGround(roadZ) {
    const t = this.themeAt(roadZ);
    const key = `${t.road}|${t.walk}|${t.right === 'sea' ? 'sea' : 'land'}|${t.walk}`;
    const g = this.groundPool.get(key);
    g.position.set(0, 0, -(roadZ + SEGMENT_LEN / 2));
    this.active.push({ obj: g, roadZ: roadZ + SEGMENT_LEN / 2, half: SEGMENT_LEN / 2, pool: this.groundPool });
    if (t.right === 'sea') this._place('railing', ROAD_HALF + 5.9, roadZ + SEGMENT_LEN / 2, SEGMENT_LEN / 2);
  }

  _buildingKey(kind) {
    switch (kind) {
      case 'classic': return 'bld_classic_' + Math.floor(Math.random() * 6);
      case 'old': return 'bld_old_' + Math.floor(Math.random() * 4);
      case 'glass': return 'bld_glass_' + Math.floor(Math.random() * 4);
      case 'wall': return 'old_wall';
      default: return null;
    }
  }

  _spawnBuilding(side) {
    const cursorKey = side < 0 ? 'leftCursor' : 'rightCursor';
    const roadZ = this[cursorKey];
    const t = this.themeAt(roadZ + 6);
    const kind = side < 0 ? t.left : t.right;
    const key = this._buildingKey(kind);
    if (!key) { this[cursorKey] += SEGMENT_LEN; return; }
    const probe = this.pool.get(key);
    const L = probe.userData.meta.length || 10;
    this.pool.release(probe);
    const gap = kind === 'glass' ? rand(3, 6) : kind === 'classic' ? rand(0, 1.5) : 0.2;
    const center = roadZ + L / 2;
    this._place(key, side * FACE_X, center, L / 2, side < 0 ? 0 : Math.PI);
    this[cursorKey] = roadZ + L + gap;
  }

  _spawnProps(roadZ, index) {
    const t = this.themeAt(roadZ);
    const lowQ = this.quality === 'low';
    if (index % 2 === 0) {
      this._place(t.lamp, -(ROAD_HALF + 0.7), roadZ, 1, 0);
      this._place(t.lamp, ROAD_HALF + 0.7, roadZ + 8, 1, Math.PI);
      return;
    }
    if (lowQ && index % 4 !== 1) return;
    const lk = pick(t.props);
    this._place(lk, -(ROAD_HALF + 2.8), roadZ, 1.2, lk === 'busstop' || lk === 'bench' ? 0 : Math.random() * 6);
    const rk = pick(t.rightProps);
    const rx = t.right === 'sea' ? ROAD_HALF + 3.6 : ROAD_HALF + 2.8;
    this._place(rk, rx, roadZ + 8, 1.2, rk === 'busstop' || rk === 'bench' ? Math.PI : Math.random() * 6);
  }

  _spawnAll() {
    const horizon = this.distance + SPAWN_DIST;
    while (this.groundCursor < horizon + 20) { this._spawnGround(this.groundCursor); this.groundCursor += SEGMENT_LEN; }
    while (this.leftCursor < horizon) this._spawnBuilding(-1);
    while (this.rightCursor < horizon) this._spawnBuilding(1);
    while (this.propCursor < horizon) { this._spawnProps(this.propCursor, this.propIndex++); this.propCursor += 8; }
  }

  _despawn() {
    const d = this.distance;
    const a = this.active;
    for (let i = a.length - 1; i >= 0; i--) {
      const it = a[i];
      if (d - (it.roadZ + it.half) > DESPAWN_Z) {
        it.pool.release(it.obj);
        a[i] = a[a.length - 1];
        a.pop();
      }
    }
  }

  // start = road distance to (re)start from (0 for a new run; other values are handy for testing districts)
  reset(start = 0) {
    for (const it of this.active) it.pool.release(it.obj);
    this.active.length = 0;
    this.distance = start;
    this.groundCursor = start - 40;
    this.leftCursor = start - 30;
    this.rightCursor = start - 26;
    this.propCursor = start - 24;
    this.propIndex = 0;
    this.scroller.position.z = start;
    this._setTheme(themeIndexAt(start, THEME_LENGTH), true);
    this._spawnAll();
  }

  // ------------------------------------------------------------ lighting
  _lightFrom(l) {
    return {
      skyTop: new THREE.Color(l.skyTop), skyBottom: new THREE.Color(l.skyBottom), fog: new THREE.Color(l.fog),
      hemiSky: new THREE.Color(l.hemiSky), hemiGround: new THREE.Color(l.hemiGround), sunColor: new THREE.Color(l.sunColor),
      glow: new THREE.Color(l.glow), fogNear: l.fogNear, fogFar: l.fogFar, hemi: l.hemi, sun: l.sun, night: l.night,
    };
  }

  _setTheme(i, instant) {
    this.themeIndex = i;
    this.targetLight = this._lightFrom(THEMES[i].light);
    if (instant) { this.light = this._lightFrom(THEMES[i].light); this._applyLight(); }
    const sk = THEMES[i].skyline;
    for (const k of Object.keys(this.skyline)) {
      this.skyline[k].target = sk.includes(k) ? 1 : 0;
      if (instant) this.skyline[k].o = this.skyline[k].target;
    }
    this._applySkyline(0);
  }

  _applyLight() {
    const L = this.light;
    this.skyMat.uniforms.top.value.copy(L.skyTop);
    this.skyMat.uniforms.bottom.value.copy(L.skyBottom);
    this.scene.fog.color.copy(L.fog);
    this.scene.fog.near = L.fogNear; this.scene.fog.far = L.fogFar;
    this.hemi.color.copy(L.hemiSky); this.hemi.groundColor.copy(L.hemiGround); this.hemi.intensity = L.hemi * 2.2;
    this.sun.color.copy(L.sunColor); this.sun.intensity = L.sun * 1.6;
    MAT.glow.color.copy(L.glow);
    MAT.farGlow.color.copy(L.glow);
    headlightPool.opacity = L.night * 0.5;
    this.starMat.opacity = Math.max(0, L.night - 0.5) * 2;
    const sunset = THEMES[this.themeIndex].id === 'gunbatimi' ? 1 : 0;
    this.sunMat.opacity += (sunset - this.sunMat.opacity) * 0.05;
    this.groundMats.water.color.setRGB(1 - L.night * 0.6, 1 - L.night * 0.55, 1 - L.night * 0.3);
  }

  _applySkyline(dt) {
    for (const k of Object.keys(this.skyline)) {
      const s = this.skyline[k];
      s.o += (s.target - s.o) * Math.min(1, dt * 0.8);
      if (s.target === 1 && s.o > 0.995) s.o = 1;
      if (s.target === 0 && s.o < 0.005) s.o = 0;
      s.obj.visible = s.o > 0;
      for (const m of s.mats) { m.opacity = s.o; m.transparent = s.o < 1; m.depthWrite = s.o >= 1; }
    }
  }

  // ------------------------------------------------------------ per frame
  update(dt, distance, camera) {
    this.time += dt;
    this.distance = distance;
    this.scroller.position.z = distance;

    const ti = themeIndexAt(distance, THEME_LENGTH);
    let changed = false;
    if (ti !== this.themeIndex) { this._setTheme(ti, false); changed = true; }

    // ease lighting towards the district's look
    const k = Math.min(1, dt * 0.9), L = this.light, T = this.targetLight;
    for (const c of ['skyTop', 'skyBottom', 'fog', 'hemiSky', 'hemiGround', 'sunColor', 'glow']) L[c].lerp(T[c], k);
    for (const c of ['fogNear', 'fogFar', 'hemi', 'sun', 'night']) L[c] = lerp(L[c], T[c], k);
    this._applyLight();
    this._applySkyline(dt);

    // Flame Towers: LED façade at night shows animated flames
    const night = L.night > 0.5;
    const want = night ? this.flameMatNight : this.flameMatDay;
    if (this.flameMat.map !== want) { this.flameMat.map = want; this.flameMat.needsUpdate = true; }
    if (night) this.flameMatNight.offset.y = (this.time * 0.35) % 1;
    else this.flameMatDay.offset.y = 0;

    if (this.skyline.bigflag.obj.visible) {
      const pos = this.bigFlag.geometry.attributes.position, b = this.bigFlagBase;
      for (let i = 0; i < pos.count; i++) {
        const x = b[i * 3];
        pos.setZ(i, Math.sin(x * 0.12 - this.time * 3) * x * 0.12);
      }
      pos.needsUpdate = true;
    }

    TX.water().offset.x = (this.time * 0.02) % 1;

    // sky & stars follow the camera so they always surround it
    this.sky.position.copy(camera.position);
    this.stars.position.copy(camera.position);
    this.skyGroup.position.x = camera.position.x * 0.8;

    this._spawnAll();
    this._despawn();
    return changed ? THEMES[ti] : null;
  }

  get theme() { return THEMES[this.themeIndex]; }
}
