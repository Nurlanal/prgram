// Global gameplay constants. Units: 1 unit = 1 metre, time in seconds.

export const LANE_X = [-2.1, 0, 2.1];
export const LANE_WIDTH = 2.1;
export const ROAD_HALF = 3.6;          // half width of asphalt
export const SIDEWALK_W = 4.6;         // sidewalk width on each side
export const SEGMENT_LEN = 20;         // ground segment length
export const SPAWN_DIST = 150;         // how far ahead things appear
export const DESPAWN_Z = 14;           // world z behind the camera where things are recycled

export const SPEED = {
  start: 15,       // m/s at run start (~54 km/h)
  max: 40,         // m/s cap (~144 km/h)
  tau: 130,        // seconds constant of exponential ramp
  menu: 5,         // attract-mode scrolling speed
};

export const PLAYER = {
  width: 0.7,
  depth: 0.5,
  height: 1.7,
  slideHeight: 0.75,
  laneTime: 0.13,
  jumpTime: 0.64,
  jumpHeight: 1.8,
  slideTime: 0.68,
};

export const THEME_LENGTH = 520;       // metres per city district
export const COIN_SPACING = 2.4;       // metres between coins in a trail
export const COIN_POOL = 160;

export const SCORE = {
  perMetre: 1,
  perCoin: 10,
};

export const POWERUP = {
  magnetBase: 8,   // seconds, + 2 per garage level
  x2Base: 10,
  shieldTime: 25,  // skateboard protection duration
  perLevel: 2,
};
