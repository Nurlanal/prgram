// Object pools: nothing is allocated during gameplay once pools are warm,
// which keeps memory flat and avoids GC hitches.
import { createPrefab } from './prefabs.js';

export class PoolSet {
  constructor(parent, factory = createPrefab) {
    this.parent = parent;
    this.factory = factory;
    this.free = new Map();
  }

  get(key) {
    const arr = this.free.get(key);
    let o = arr && arr.pop();
    if (!o) {
      o = this.factory(key);
      o.userData.poolKey = key;
      this.parent.add(o);
    }
    o.visible = true;
    return o;
  }

  release(o) {
    o.visible = false;
    const k = o.userData.poolKey;
    let arr = this.free.get(k);
    if (!arr) { arr = []; this.free.set(k, arr); }
    arr.push(o);
  }
}
