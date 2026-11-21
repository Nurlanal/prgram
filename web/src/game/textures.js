// All textures are painted procedurally on canvases at startup (no image files).
import * as THREE from 'three';

const cache = new Map();

function canvas(w, h) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  return [c, c.getContext('2d')];
}

function tex(c, repeat = true) {
  const t = new THREE.CanvasTexture(c);
  if (repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; }
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 4;
  return t;
}

function cached(key, fn) {
  if (!cache.has(key)) cache.set(key, fn());
  return cache.get(key);
}

function noise(ctx, w, h, amount, alpha = 0.08) {
  for (let i = 0; i < amount; i++) {
    const v = Math.random() < 0.5 ? 0 : 255;
    ctx.fillStyle = `rgba(${v},${v},${v},${Math.random() * alpha})`;
    ctx.fillRect(Math.random() * w, Math.random() * h, 2, 2);
  }
}

// Road: 7.2 m wide, tile of 10 m length. Lane dividers at +-1.05 m.
export const roadAsphalt = () => cached('road', () => {
  const [c, x] = canvas(256, 512);
  x.fillStyle = '#4a4c52'; x.fillRect(0, 0, 256, 512);
  noise(x, 256, 512, 9000, 0.12);
  // tyre tracks
  x.fillStyle = 'rgba(20,20,25,0.18)';
  for (const lx of [-2.1, 0, 2.1]) {
    const px = (lx + 3.6) / 7.2 * 256;
    x.fillRect(px - 18, 0, 8, 512); x.fillRect(px + 10, 0, 8, 512);
  }
  // edge lines
  x.fillStyle = '#e8e8e0';
  x.fillRect(6, 0, 5, 512); x.fillRect(245, 0, 5, 512);
  // dashed lane dividers
  for (const lx of [-1.05, 1.05]) {
    const px = (lx + 3.6) / 7.2 * 256;
    x.fillRect(px - 3, 40, 6, 200);
    x.fillRect(px - 3, 296, 6, 200);
  }
  return tex(c);
});

export const roadCobble = () => cached('cobble', () => {
  const [c, x] = canvas(256, 256);
  x.fillStyle = '#6e6458'; x.fillRect(0, 0, 256, 256);
  const s = 32;
  for (let j = 0; j < 8; j++) for (let i = -1; i < 9; i++) {
    const ox = (j % 2) * s / 2;
    const l = 105 + Math.random() * 40;
    x.fillStyle = `rgb(${l + 18},${l + 8},${l - 8})`;
    x.beginPath();
    x.roundRect ? x.roundRect(i * s + ox + 2, j * s + 2, s - 4, s - 4, 6) : x.rect(i * s + ox + 2, j * s + 2, s - 4, s - 4);
    x.fill();
  }
  noise(x, 256, 256, 3000, 0.1);
  return tex(c);
});

export const sidewalkTiles = () => cached('sidewalk', () => {
  const [c, x] = canvas(128, 128);
  x.fillStyle = '#c9a898'; x.fillRect(0, 0, 128, 128);
  const cols = ['#d8b8a6', '#c49c8c', '#e0c4b2', '#b98f80'];
  for (let j = 0; j < 4; j++) for (let i = 0; i < 4; i++) {
    x.fillStyle = cols[(i + j * 3) % 4];
    x.fillRect(i * 32 + 1, j * 32 + 1, 30, 30);
  }
  noise(x, 128, 128, 1200, 0.08);
  return tex(c);
});

export const stonePave = () => cached('stone', () => {
  const [c, x] = canvas(128, 128);
  x.fillStyle = '#a8916f'; x.fillRect(0, 0, 128, 128);
  for (let j = 0; j < 4; j++) for (let i = 0; i < 3; i++) {
    const l = 165 + Math.random() * 30;
    x.fillStyle = `rgb(${l},${l - 18},${l - 45})`;
    x.fillRect(i * 44 + (j % 2) * 20 - 10, j * 32 + 2, 40, 28);
  }
  noise(x, 128, 128, 1500, 0.1);
  return tex(c);
});

export const plazaTiles = () => cached('plaza', () => {
  const [c, x] = canvas(128, 128);
  x.fillStyle = '#9a9ca4'; x.fillRect(0, 0, 128, 128);
  for (let j = 0; j < 2; j++) for (let i = 0; i < 2; i++) {
    x.fillStyle = (i + j) % 2 ? '#b4b6bd' : '#a5a7ae';
    x.fillRect(i * 64 + 1, j * 64 + 1, 62, 62);
  }
  noise(x, 128, 128, 1200, 0.08);
  return tex(c);
});

export const water = () => cached('water', () => {
  const [c, x] = canvas(256, 256);
  const g = x.createLinearGradient(0, 0, 0, 256);
  g.addColorStop(0, '#1d8fd0'); g.addColorStop(1, '#2aa4e0');
  x.fillStyle = g; x.fillRect(0, 0, 256, 256);
  for (let i = 0; i < 260; i++) {
    x.strokeStyle = `rgba(255,255,255,${0.08 + Math.random() * 0.25})`;
    x.lineWidth = 1 + Math.random() * 2;
    const px = Math.random() * 256, py = Math.random() * 256, l = 6 + Math.random() * 18;
    x.beginPath(); x.moveTo(px, py); x.quadraticCurveTo(px + l / 2, py - 3, px + l, py); x.stroke();
  }
  return tex(c);
});

export const grass = () => cached('grass', () => {
  const [c, x] = canvas(64, 64);
  x.fillStyle = '#5d9a3c'; x.fillRect(0, 0, 64, 64);
  noise(x, 64, 64, 900, 0.2);
  return tex(c);
});

// Azerbaijan flag (blue / red / green, white crescent and 8-pointed star)
export function drawFlag(x, w, h, ox = 0, oy = 0) {
  x.fillStyle = '#00b5e2'; x.fillRect(ox, oy, w, h / 3);
  x.fillStyle = '#ef3340'; x.fillRect(ox, oy + h / 3, w, h / 3);
  x.fillStyle = '#509e2f'; x.fillRect(ox, oy + 2 * h / 3, w, h / 3 + 0.5);
  const cx = ox + w / 2 - h * 0.03, cy = oy + h / 2, r = h * 0.12;
  x.fillStyle = '#fff';
  x.beginPath(); x.arc(cx, cy, r, 0, Math.PI * 2); x.fill();
  x.fillStyle = '#ef3340';
  x.beginPath(); x.arc(cx + r * 0.25, cy, r * 0.8, 0, Math.PI * 2); x.fill();
  // 8-point star
  x.fillStyle = '#fff';
  const sx = cx + r * 1.05, sr = r * 0.5;
  x.beginPath();
  for (let i = 0; i < 16; i++) {
    const a = i * Math.PI / 8, rr = i % 2 ? sr * 0.45 : sr;
    x.lineTo(sx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
  }
  x.closePath(); x.fill();
}

export const flag = () => cached('flag', () => {
  const [c, x] = canvas(256, 128);
  drawFlag(x, 256, 128);
  return tex(c, false);
});

// Vest textures for characters: base colour + emblem on the back
export function vestTexture(kind, base) {
  return cached('vest_' + kind + base, () => {
    const [c, x] = canvas(256, 256);
    x.fillStyle = base; x.fillRect(0, 0, 256, 256);
    if (kind === 'flag') {
      drawFlag(x, 170, 110, 43, 70);
    } else if (kind === 'buta') {
      x.fillStyle = 'rgba(255,215,120,0.9)';
      for (let j = 0; j < 4; j++) for (let i = 0; i < 4; i++) {
        const px = i * 64 + 32 + (j % 2) * 20, py = j * 64 + 32;
        x.beginPath(); x.arc(px, py, 13, 0, Math.PI * 2); x.fill();
        x.beginPath(); x.moveTo(px - 13, py); x.quadraticCurveTo(px - 10, py - 30, px + 10, py - 30); x.quadraticCurveTo(px, py - 14, px + 13, py); x.fill();
      }
    } else if (kind === 'carpet') {
      x.strokeStyle = '#f2c14e'; x.lineWidth = 10; x.strokeRect(14, 14, 228, 228);
      x.fillStyle = '#1d3f8f';
      x.beginPath(); x.moveTo(128, 40); x.lineTo(216, 128); x.lineTo(128, 216); x.lineTo(40, 128); x.closePath(); x.fill();
      x.fillStyle = '#f2c14e';
      x.beginPath(); x.moveTo(128, 80); x.lineTo(176, 128); x.lineTo(128, 176); x.lineTo(80, 128); x.closePath(); x.fill();
      x.fillStyle = '#b3202a'; x.fillRect(112, 112, 32, 32);
    } else if (kind === 'stripes') {
      for (let i = 0; i < 8; i++) { x.fillStyle = i % 2 ? '#111' : base; x.fillRect(i * 32, 0, 32, 256); }
      x.fillStyle = '#fff'; x.font = 'bold 90px sans-serif'; x.textAlign = 'center'; x.fillText('10', 128, 160);
    } else if (kind === 'stars') {
      x.fillStyle = '#ffd34d';
      x.font = 'bold 120px sans-serif'; x.textAlign = 'center'; x.fillText('★', 128, 170);
    }
    noise(x, 256, 256, 800, 0.06);
    return tex(c, false);
  });
}

export function licensePlate(text) {
  const [c, x] = canvas(256, 56);
  x.fillStyle = '#f4f4f0'; x.fillRect(0, 0, 256, 56);
  x.strokeStyle = '#111'; x.lineWidth = 4; x.strokeRect(2, 2, 252, 52);
  // flag + AZ code
  drawFlag(x, 30, 18, 10, 8);
  x.fillStyle = '#1b3e94'; x.font = 'bold 18px sans-serif'; x.fillText('AZ', 11, 46);
  x.fillStyle = '#111'; x.font = 'bold 40px sans-serif'; x.textAlign = 'center';
  x.fillText(text, 148, 43);
  return tex(c, false);
}

export const busSide = () => cached('busSide', () => {
  const [c, x] = canvas(512, 128);
  x.clearRect(0, 0, 512, 128);
  x.fillStyle = '#ffffff'; x.font = 'bold 64px sans-serif'; x.textAlign = 'center';
  x.fillText('BakuBus', 256, 86);
  drawFlag(x, 60, 36, 18, 46);
  drawFlag(x, 60, 36, 434, 46);
  return tex(c, false);
});

export function busSign(route) {
  return cached('busSign' + route, () => {
    const [c, x] = canvas(256, 64);
    x.fillStyle = '#101010'; x.fillRect(0, 0, 256, 64);
    x.fillStyle = '#ffb21e'; x.font = 'bold 40px monospace'; x.textAlign = 'center';
    x.fillText(route, 128, 46);
    drawFlag(x, 36, 22, 8, 21); drawFlag(x, 36, 22, 212, 21);
    return tex(c, false);
  });
}

export function textSign(text, bg, fg, w = 256, h = 64, font = 'bold 40px sans-serif') {
  return cached('sign' + text + bg + fg + w + h, () => {
    const [c, x] = canvas(w, h);
    x.fillStyle = bg; x.fillRect(0, 0, w, h);
    x.fillStyle = fg; x.font = font; x.textAlign = 'center'; x.textBaseline = 'middle';
    x.fillText(text, w / 2, h / 2 + 2);
    return tex(c, false);
  });
}

export const stripes = () => cached('stripes', () => {
  const [c, x] = canvas(128, 32);
  for (let i = -2; i < 10; i++) {
    x.fillStyle = i % 2 ? '#ffffff' : '#e22a2a';
    x.beginPath(); x.moveTo(i * 16, 32); x.lineTo(i * 16 + 16, 0); x.lineTo(i * 16 + 32, 0); x.lineTo(i * 16 + 16, 32); x.fill();
  }
  return tex(c, false);
});

export const hazard = () => cached('hazard', () => {
  const [c, x] = canvas(256, 64);
  x.fillStyle = '#ffcc00'; x.fillRect(0, 0, 256, 64);
  x.fillStyle = '#111';
  for (let i = -2; i < 18; i++) { x.beginPath(); x.moveTo(i * 20, 64); x.lineTo(i * 20 + 10, 64); x.lineTo(i * 20 + 30, 0); x.lineTo(i * 20 + 20, 0); x.fill(); }
  x.fillStyle = '#ffcc00'; x.fillRect(60, 12, 136, 40);
  x.fillStyle = '#111'; x.font = 'bold 30px sans-serif'; x.textAlign = 'center'; x.fillText('TƏMİR', 128, 43);
  return tex(c, false);
});

export const coinFace = () => cached('coin', () => {
  const [c, x] = canvas(128, 128);
  const g = x.createRadialGradient(54, 50, 10, 64, 64, 64);
  g.addColorStop(0, '#fff3a0'); g.addColorStop(0.55, '#ffc21a'); g.addColorStop(1, '#e08a00');
  x.fillStyle = g; x.beginPath(); x.arc(64, 64, 64, 0, Math.PI * 2); x.fill();
  x.strokeStyle = '#ffe680'; x.lineWidth = 6; x.beginPath(); x.arc(64, 64, 50, 0, Math.PI * 2); x.stroke();
  x.fillStyle = '#ffde55'; x.strokeStyle = '#d07a00'; x.lineWidth = 4;
  x.beginPath();
  for (let i = 0; i < 10; i++) {
    const a = -Math.PI / 2 + i * Math.PI / 5, r = i % 2 ? 16 : 36;
    x.lineTo(64 + Math.cos(a) * r, 66 + Math.sin(a) * r);
  }
  x.closePath(); x.fill(); x.stroke();
  return tex(c, false);
});

export const radial = (key, inner, outer) => cached('radial' + key, () => {
  const [c, x] = canvas(64, 64);
  const g = x.createRadialGradient(32, 32, 0, 32, 32, 32);
  g.addColorStop(0, inner); g.addColorStop(1, outer);
  x.fillStyle = g; x.fillRect(0, 0, 64, 64);
  return tex(c, false);
});

export const sparkle = () => cached('sparkle', () => {
  const [c, x] = canvas(64, 64);
  const g = x.createRadialGradient(32, 32, 0, 32, 32, 32);
  g.addColorStop(0, 'rgba(255,255,220,1)'); g.addColorStop(0.25, 'rgba(255,220,90,0.8)'); g.addColorStop(1, 'rgba(255,200,0,0)');
  x.fillStyle = g; x.fillRect(0, 0, 64, 64);
  x.fillStyle = 'rgba(255,255,255,0.9)';
  x.fillRect(30, 2, 4, 60); x.fillRect(2, 30, 60, 4);
  return tex(c, false);
});

export const warning = () => cached('warning', () => {
  const [c, x] = canvas(128, 128);
  x.fillStyle = '#ff2a2a'; x.strokeStyle = '#fff'; x.lineWidth = 8; x.lineJoin = 'round';
  x.beginPath(); x.moveTo(64, 10); x.lineTo(120, 112); x.lineTo(8, 112); x.closePath(); x.fill(); x.stroke();
  x.fillStyle = '#fff'; x.font = 'bold 70px sans-serif'; x.textAlign = 'center'; x.fillText('!', 64, 100);
  return tex(c, false);
});

export const powerIcon = (kind) => cached('pw' + kind, () => {
  const [c, x] = canvas(128, 128);
  const g = x.createRadialGradient(64, 64, 10, 64, 64, 64);
  g.addColorStop(0, kind === 'magnet' ? '#ff6b6b' : '#ffe066');
  g.addColorStop(1, kind === 'magnet' ? '#b3122b' : '#e07b00');
  x.fillStyle = g; x.beginPath(); x.arc(64, 64, 60, 0, Math.PI * 2); x.fill();
  x.strokeStyle = '#fff'; x.lineWidth = 6; x.stroke();
  x.fillStyle = '#fff'; x.textAlign = 'center'; x.textBaseline = 'middle';
  if (kind === 'magnet') {
    x.lineWidth = 18; x.strokeStyle = '#fff'; x.lineCap = 'butt';
    x.beginPath(); x.arc(64, 58, 26, Math.PI, 0, true); x.stroke();
    x.fillStyle = '#ddd'; x.fillRect(29, 58, 18, 20); x.fillRect(81, 58, 18, 20);
  } else {
    x.font = 'bold 58px sans-serif'; x.fillText('x2', 64, 68);
  }
  return tex(c, false);
});

// Vertical LED gradient for the Flame Towers
export const flameLED = () => cached('flameLED', () => {
  const [c, x] = canvas(64, 256);
  const g = x.createLinearGradient(0, 256, 0, 0);
  g.addColorStop(0, '#1d4fd8'); g.addColorStop(0.35, '#4f7dff'); g.addColorStop(0.6, '#a84dff');
  g.addColorStop(0.85, '#ff4fa3'); g.addColorStop(1, '#ff9a3c');
  x.fillStyle = g; x.fillRect(0, 0, 64, 256);
  // glass grid
  x.fillStyle = 'rgba(255,255,255,0.18)';
  for (let i = 0; i < 64; i += 8) x.fillRect(i, 0, 1, 256);
  for (let j = 0; j < 256; j += 6) x.fillRect(0, j, 64, 1);
  const t = tex(c);
  t.wrapS = THREE.RepeatWrapping;
  return t;
});

export const flameFire = () => cached('flameFire', () => {
  const [c, x] = canvas(64, 256);
  const g = x.createLinearGradient(0, 256, 0, 0);
  g.addColorStop(0, '#ff2200'); g.addColorStop(0.4, '#ff7a00'); g.addColorStop(0.7, '#ffd000'); g.addColorStop(1, '#ff3d00');
  x.fillStyle = g; x.fillRect(0, 0, 64, 256);
  for (let i = 0; i < 40; i++) {
    x.fillStyle = `rgba(255,255,160,${Math.random() * 0.5})`;
    const px = Math.random() * 64, py = Math.random() * 256;
    x.beginPath(); x.ellipse(px, py, 3 + Math.random() * 5, 12 + Math.random() * 20, 0, 0, Math.PI * 2); x.fill();
  }
  return tex(c);
});
