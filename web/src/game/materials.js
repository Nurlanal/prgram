// Shared materials. Prefabs use vertex colours so hundreds of objects can share
// a handful of materials (cheap state changes, good mobile performance).
import * as THREE from 'three';

export const MAT = {
  // Lit, vertex-coloured surfaces
  solid: new THREE.MeshLambertMaterial({ vertexColors: true }),
  // Building windows / lanterns: colour is animated day (glassy) -> night (warm glow)
  glow: new THREE.MeshBasicMaterial({ vertexColors: true, color: 0x8899b0 }),
  // Always bright (headlights, LEDs)
  light: new THREE.MeshBasicMaterial({ vertexColors: true }),
  // Skyline (far background) - not affected by fog
  farSolid: new THREE.MeshLambertMaterial({ vertexColors: true, fog: false }),
  farGlow: new THREE.MeshBasicMaterial({ vertexColors: true, fog: false, color: 0x8899b0 }),
};

const texMats = new Map();
export function texMat(texture, opts = {}) {
  const key = texture.uuid + JSON.stringify(opts);
  if (!texMats.has(key)) {
    const M = opts.basic ? THREE.MeshBasicMaterial : THREE.MeshLambertMaterial;
    const o = Object.assign({ map: texture }, opts);
    delete o.basic;
    texMats.set(key, new M(o));
  }
  return texMats.get(key);
}

// Additive "light pool" material for headlights on the road at night
export const headlightPool = new THREE.MeshBasicMaterial({
  color: 0xfff1c0, transparent: true, opacity: 0, depthWrite: false, blending: THREE.AdditiveBlending,
});
