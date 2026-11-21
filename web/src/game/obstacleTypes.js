// Obstacle catalogue. kind:
//   solid - must change lane (vehicles, fences)
//   low   - jump over (cones, barriers)
//   high  - slide under (repair gantry)
// speed: own speed toward the player in m/s ([min,max]); 0 = parked/static.
// To add a new car: create a prefab in prefabs.js and list its key here.

export const OBSTACLE_TYPES = {
  car: {
    kind: 'solid', length: 4.4, width: 1.8, speed: [6, 11], suddenChance: 0.22,
    prefabs: ['car_silver', 'car_white', 'car_black', 'car_red', 'car_blue', 'car_green'],
  },
  taxi: {
    kind: 'solid', length: 4.6, width: 1.85, speed: [7, 12], suddenChance: 0.3,
    prefabs: ['taxi_yellow', 'taxi_cab'],
  },
  bus: {
    kind: 'solid', length: 11, width: 2.4, speed: [0, 5], staticChance: 0.45,
    prefabs: ['bus_0', 'bus_1'],
  },
  cones: { kind: 'low', length: 0.6, width: 1.9, height: 0.85, speed: [0, 0], prefabs: ['cones'] },
  barrier: { kind: 'low', length: 0.5, width: 1.9, height: 1.05, speed: [0, 0], prefabs: ['barrier'] },
  gantry: { kind: 'high', length: 0.5, width: 2.0, bottom: 1.2, speed: [0, 0], prefabs: ['gantry'] },
  fence: { kind: 'solid', length: 0.9, width: 1.9, speed: [0, 0], prefabs: ['fence'] },
};
