// Seeded randomness, distances, and riding a road path (demo-roads.ts) into timed GPS fixes for the demo backup.
import { STEP_M, type RoadPath } from './demo-roads.ts';

export type LatLng = { lat: number; lng: number };
export type Fix = LatLng & { atMillis: number; speedKmh: number };

/** mulberry32: small, fast and the same on every machine. */
export function makeRng(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export type Rng = () => number;
export const between = (rng: Rng, lo: number, hi: number): number => lo + (hi - lo) * rng();

const EARTH_M = 6_371_008.8;
const rad = (d: number): number => (d * Math.PI) / 180;

/** Same formula as the app's TripMath.haversineM. */
export function haversineM(a: LatLng, b: LatLng): number {
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_M * Math.asin(Math.min(1, Math.sqrt(h)));
}

export function trackKm(points: LatLng[]): number {
  let m = 0;
  for (let i = 1; i < points.length; i++) m += haversineM(points[i - 1], points[i]);
  return m / 1000;
}

/** Moving average like the app's avgMovingSpeedKmh: only legs of at least 3 km/h count. */
export function movingAvgKmh(fixes: Fix[]): number {
  let m = 0;
  let ms = 0;
  for (let i = 1; i < fixes.length; i++) {
    const d = haversineM(fixes[i - 1], fixes[i]);
    const dt = fixes[i].atMillis - fixes[i - 1].atMillis;
    if (dt > 0 && d / 1000 / (dt / 3_600_000) >= 3) {
      m += d;
      ms += dt;
    }
  }
  return ms === 0 ? 0 : m / 1000 / (ms / 3_600_000);
}

export function bearing(a: LatLng, b: LatLng): number {
  const y = Math.sin(rad(b.lng - a.lng)) * Math.cos(rad(b.lat));
  const x = Math.cos(rad(a.lat)) * Math.sin(rad(b.lat)) - Math.sin(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.cos(rad(b.lng - a.lng));
  return Math.atan2(y, x);
}

export function offset(p: LatLng, headingRad: number, metres: number): LatLng {
  const dLat = (metres * Math.cos(headingRad)) / 111_320;
  const dLng = (metres * Math.sin(headingRad)) / (111_320 * Math.cos(rad(p.lat)));
  return { lat: p.lat + dLat, lng: p.lng + dLng };
}

export const wrap = (a: number): number => Math.atan2(Math.sin(a), Math.cos(a));

/** Cruise and occasional burst speed ranges per road kind (km/h). Corners are slowed by [LATERAL_G]. */
const CRUISE = { city: [18, 34], highway: [42, 66], ghat: [24, 38] } as const;
const BURST = { city: [52, 66], highway: [86, 110], ghat: [40, 48] } as const;
const LATERAL_G = 1.8;
const BRAKE = 2.2;
const ACCEL = 1.3;

/** Cruise picked afresh every 0.6–2 km (traffic); a quick burst now and then, at least once past halfway. */
function cruiseLimits(rng: Rng, path: RoadPath, topCap: number): number[] {
  const out: number[] = [];
  let left = 0;
  let cruise = 0;
  let burst = false;
  for (const p of path.pts) {
    if (left <= 0) {
      const fast: boolean = rng() < 0.2 || (!burst && out.length > path.pts.length / 2);
      burst ||= fast;
      const [lo, hi] = fast ? BURST[p.road] : CRUISE[p.road];
      cruise = Math.min(topCap, between(rng, lo, hi)) / 3.6;
      left = between(rng, 600, 2000);
    }
    left -= STEP_M;
    out.push(cruise);
  }
  return out;
}

/** Fastest safe speed (m/s) at each path point: cruise, corners, stops, then braking and pulling away. */
function speedProfile(rng: Rng, path: RoadPath, topCap: number): number[] {
  const pts = path.pts;
  const v = cruiseLimits(rng, path, topCap);
  const heading = pts.map((_, i) => bearing(pts[Math.max(0, i - 1)], pts[Math.min(pts.length - 1, i + 1)]));
  for (let i = 0; i < pts.length; i++) {
    const a = heading[Math.max(0, i - 2)];
    const b = heading[Math.min(pts.length - 1, i + 2)];
    const k = Math.abs(wrap(b - a)) / (4 * STEP_M);
    if (k > 0) v[i] = Math.min(v[i], Math.sqrt(LATERAL_G / k));
  }
  for (const s of path.stops) v[s.at] = 0;
  v[0] = 0;
  v[v.length - 1] = 0;
  for (let i = v.length - 2; i >= 0; i--) v[i] = Math.min(v[i], Math.sqrt(v[i + 1] ** 2 + 2 * BRAKE * STEP_M));
  for (let i = 1; i < v.length; i++) v[i] = Math.min(v[i], Math.sqrt(v[i - 1] ** 2 + 2 * ACCEL * STEP_M));
  return v;
}

function pointAt(path: RoadPath, s: number): LatLng {
  const i = Math.min(path.pts.length - 2, Math.floor(s / STEP_M));
  const f = Math.min(1, s / STEP_M - i);
  const a = path.pts[i];
  const b = path.pts[i + 1];
  return { lat: a.lat + (b.lat - a.lat) * f, lng: a.lng + (b.lng - a.lng) * f };
}

/** Rides [path] one second at a time: a fix a second, standing still at its stops. */
function ride(rng: Rng, path: RoadPath, topCap: number): Fix[] {
  const v = speedProfile(rng, path, topCap);
  const waits = new Map(path.stops.map((s) => [s.at, s.seconds]));
  const end = (path.pts.length - 1) * STEP_M;
  const out: Fix[] = [{ ...path.pts[0], atMillis: 0, speedKmh: 0 }];
  let s = 0;
  let t = 0;
  while (s < end) {
    const i = Math.floor(s / STEP_M);
    const speed = Math.max(1.2, v[i] * between(rng, 0.97, 1.03));
    const nextS = Math.min(end, s + speed);
    for (let j = i + 1; j <= Math.floor(nextS / STEP_M); j++) {
      for (let w = waits.get(j) ?? 0; w > 0; w--) out.push({ ...pointAt(path, j * STEP_M), atMillis: (t += 1000), speedKmh: 0 });
    }
    s = nextS;
    out.push({ ...pointAt(path, s), atMillis: (t += 1000), speedKmh: Math.round(speed * 36) / 10 });
  }
  return out;
}

/** Metres from [p] to the segment [a]-[b], on a local flat projection. */
function segmentM(p: LatLng, a: LatLng, b: LatLng): number {
  const k = 111_320 * Math.cos(rad(a.lat));
  const [px, py, bx, by] = [(p.lng - a.lng) * k, (p.lat - a.lat) * 111_320, (b.lng - a.lng) * k, (b.lat - a.lat) * 111_320];
  const len = bx * bx + by * by;
  const t = len === 0 ? 0 : Math.max(0, Math.min(1, (px * bx + py * by) / len));
  return Math.hypot(px - t * bx, py - t * by);
}

/**
 * The fixes the app would store after a ride (TrackSimplifier.kt): Ramer-Douglas-Peucker within [tolM], plus a
 * fix wherever the speed moved 10 km/h from the last kept one (at least 3 s later).
 */
function simplify(fixes: Fix[], tolM: number): Fix[] {
  const keep = new Array<boolean>(fixes.length).fill(false);
  keep[0] = keep[fixes.length - 1] = true;
  const stack: Array<[number, number]> = [[0, fixes.length - 1]];
  while (stack.length > 0) {
    const [a, b] = stack.pop() ?? [0, 0];
    let far = -1;
    let farM = tolM;
    for (let i = a + 1; i < b; i++) {
      const d = segmentM(fixes[i], fixes[a], fixes[b]);
      if (d > farM) [far, farM] = [i, d];
    }
    if (far < 0) continue;
    keep[far] = true;
    stack.push([a, far], [far, b]);
  }
  let last = fixes[0];
  fixes.forEach((f, i) => {
    if (keep[i]) last = f;
    else if (Math.abs(f.speedKmh - last.speedKmh) >= 10 && f.atMillis - last.atMillis >= 3000) [keep[i], last] = [true, f];
  });
  return fixes.filter((_, i) => keep[i]);
}

/** A ride along [path] (never above [topCap] km/h), stored as the app stores it: at most [maxPoints] fixes. */
export function buildTrack(rng: Rng, path: RoadPath, topCap: number, maxPoints: number): Fix[] {
  const fixes = ride(rng, path, topCap);
  for (let tol = 5; ; tol *= 1.25) {
    const kept = simplify(fixes, tol);
    if (kept.length <= maxPoints) return kept;
  }
}
