// City districts. The run cycles through these so the scenery never gets
// monotonous. Add a new district (or a whole new city) by appending an entry.
//
// left/right: 'classic' | 'old' | 'glass' | 'sea' | 'wall'
// road: 'asphalt' | 'cobble'      walk: 'tiles' | 'stone' | 'plaza'
// lamp: prefab key for street lamps, props: extra prefabs placed on sidewalks
// skyline: background landmarks shown (see World.js)
// obstacles: relative spawn weights (see obstacleTypes.js)

export const THEMES = [
  {
    id: 'bulvar', name: 'Dənizkənarı Bulvar',
    left: 'classic', right: 'sea', road: 'asphalt', walk: 'tiles',
    lamp: 'lamp_classic', props: ['tree', 'planter', 'planter', 'bench'], rightProps: ['palm', 'bench', 'planter'],
    skyline: ['flame', 'tv', 'hills'],
    light: { skyTop: '#2f7fe0', skyBottom: '#bfe3ff', fog: '#bfe0f8', fogNear: 60, fogFar: 175, hemi: 1.0, hemiSky: '#ffffff', hemiGround: '#8a7d6a', sun: 1.25, sunColor: '#fff3dc', glow: '#8b9ab3', night: 0 },
    obstacles: { car: 3, taxi: 2, bus: 1.5, cones: 1.4, barrier: 1.4, gantry: 1.2, fence: 0.6 },
  },
  {
    id: 'icheri', name: 'İçərişəhər',
    left: 'old', right: 'wall', road: 'cobble', walk: 'stone',
    lamp: 'lamp_old', props: ['planter', 'tree_round'], rightProps: ['planter'],
    skyline: ['maiden', 'flame', 'hills'],
    light: { skyTop: '#3d8de0', skyBottom: '#f3dfb8', fog: '#ecd9b6', fogNear: 50, fogFar: 160, hemi: 1.0, hemiSky: '#fff4e0', hemiGround: '#8a6e4a', sun: 1.2, sunColor: '#ffe7c0', glow: '#9a9580', night: 0 },
    obstacles: { car: 2, taxi: 2.5, bus: 0.4, cones: 2, barrier: 2, gantry: 1.5, fence: 0.8 },
  },
  {
    id: 'prospekt', name: 'Neftçilər Prospekti',
    left: 'glass', right: 'glass', road: 'asphalt', walk: 'plaza',
    lamp: 'lamp_modern', props: ['tree_round', 'bench'], rightProps: ['tree_round', 'flagpole_small'],
    skyline: ['flame', 'towers', 'tv'],
    light: { skyTop: '#1f6fd6', skyBottom: '#cfe8ff', fog: '#cde4f8', fogNear: 60, fogFar: 180, hemi: 1.05, hemiSky: '#ffffff', hemiGround: '#6f7680', sun: 1.3, sunColor: '#ffffff', glow: '#7f9bbd', night: 0 },
    obstacles: { car: 4, taxi: 2, bus: 1.2, cones: 1, barrier: 1, gantry: 1, fence: 0.5 },
  },
  {
    id: 'bakubus', name: 'BakuBus Küçəsi',
    left: 'classic', right: 'classic', road: 'asphalt', walk: 'tiles',
    lamp: 'lamp_classic', props: ['busstop', 'tree', 'planter'], rightProps: ['busstop', 'tree', 'planter'],
    skyline: ['flame', 'hills', 'tv'],
    light: { skyTop: '#3a87e6', skyBottom: '#d6ecff', fog: '#d2e6f6', fogNear: 55, fogFar: 170, hemi: 1.0, hemiSky: '#ffffff', hemiGround: '#87796a', sun: 1.2, sunColor: '#fff6e8', glow: '#8b9ab3', night: 0 },
    obstacles: { car: 2, taxi: 1.5, bus: 4, cones: 1, barrier: 1, gantry: 1, fence: 0.5 },
  },
  {
    id: 'gece', name: 'Gecə Bakı',
    left: 'classic', right: 'sea', road: 'asphalt', walk: 'tiles',
    lamp: 'lamp_classic', props: ['tree', 'planter'], rightProps: ['palm', 'bench'],
    skyline: ['flame', 'tv', 'hills'],
    light: { skyTop: '#050b24', skyBottom: '#1f2a5a', fog: '#18204a', fogNear: 45, fogFar: 160, hemi: 0.55, hemiSky: '#8fa6ff', hemiGround: '#2a2030', sun: 0.35, sunColor: '#9fb0ff', glow: '#ffd27a', night: 1 },
    obstacles: { car: 3.5, taxi: 2.5, bus: 1.5, cones: 1.2, barrier: 1.2, gantry: 1, fence: 0.6 },
  },
  {
    id: 'gunbatimi', name: 'Bayraq Meydanı · Gün batımı',
    left: 'glass', right: 'sea', road: 'asphalt', walk: 'plaza',
    lamp: 'lamp_modern', props: ['palm', 'flagpole_small', 'bench'], rightProps: ['palm', 'flagpole_small'],
    skyline: ['bigflag', 'flame', 'towers'],
    light: { skyTop: '#4a3a8f', skyBottom: '#ffb070', fog: '#f0a77a', fogNear: 50, fogFar: 165, hemi: 0.9, hemiSky: '#ffd0a8', hemiGround: '#6a4a5a', sun: 1.1, sunColor: '#ffb27a', glow: '#e0b080', night: 0.45 },
    obstacles: { car: 3, taxi: 2, bus: 1.5, cones: 1.4, barrier: 1.4, gantry: 1.3, fence: 0.6 },
  },
];

export function themeIndexAt(distance, length) {
  return Math.floor(Math.max(0, distance) / length) % THEMES.length;
}
