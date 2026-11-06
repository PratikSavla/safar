// Road geometry for the demo rides, a point every 5 m. A pen follows waypoints like a rider: it heads for the
// next junction, drifts along gentle curves on the way, and turns no tighter than its road allows, so corners
// are arcs. Ghat sections add hairpins. Synthetic: no real road is traced.
import { between, bearing, haversineM, offset, wrap, type LatLng, type Rng } from './demo-geo.ts';

export type RoadKind = 'city' | 'highway' | 'ghat';
export type PathPt = LatLng & { road: RoadKind };
/** [at] is a point index; the rider stands still there for [seconds]. */
export type Stop = { at: number; seconds: number };
export type RoadPath = { pts: PathPt[]; stops: Stop[] };

/** A leg to a waypoint, or [hairpins] switchbacks heading on the whole towards [toward]. */
export type Piece = { to: LatLng; road: RoadKind } | { hairpins: number; toward: LatLng };

export const STEP_M = 5;
const HAIRPIN_R = 14;
const DEG = Math.PI / 180;

/**
 * Junction radius (m) and the meander: three waves of [amplitude (rad), wavelength range (m)] added together,
 * so roads curve irregularly instead of snaking. Highways bend over kilometres; ghats wind tightly.
 */
const STYLE = {
  city: { radius: 32, waves: [[0.07, 700, 1100], [0.12, 1800, 2600], [0.12, 4000, 6000]] },
  highway: { radius: 260, waves: [[0.05, 1500, 2500], [0.18, 5000, 8000], [0.2, 12_000, 20_000]] },
  ghat: { radius: 40, waves: [[0.6, 250, 400], [0.45, 800, 1300], [0.35, 2500, 3500]] },
} as const;

type Wave = { amp: number; length: number; phase: number };

function meander(rng: Rng, road: RoadKind): (travelled: number) => number {
  const waves: Wave[] = STYLE[road].waves.map(([amp, lo, hi]) => ({ amp: amp * between(rng, 0.7, 1.2), length: between(rng, lo, hi), phase: between(rng, 0, 2 * Math.PI) }));
  return (s) => waves.reduce((sum, w) => sum + w.amp * Math.sin((2 * Math.PI * s) / w.length + w.phase), 0);
}

/** [rng] shapes the road; [luck] decides red lights, so a commute keeps its shape while its stops change. */
type Pen = { pos: LatLng; heading: number; travelled: number; pts: PathPt[]; stops: Stop[]; rng: Rng; luck: Rng };

function step(pen: Pen, desired: number, radius: number, road: RoadKind): void {
  const max = STEP_M / radius;
  pen.heading = wrap(pen.heading + Math.max(-max, Math.min(max, wrap(desired - pen.heading))));
  pen.pos = offset(pen.pos, pen.heading, STEP_M);
  pen.travelled += STEP_M;
  pen.pts.push({ ...pen.pos, road });
}

/** Distance before a waypoint at which the turn onto the next leg starts, so the arc meets the new road. */
function turnInM(pen: Pen, to: LatLng, next: LatLng | undefined, radius: number): number {
  if (!next) return 8;
  const turn = Math.abs(wrap(bearing(to, next) - bearing(pen.pos, to)));
  return Math.max(8, radius * Math.tan(Math.min(turn, 160 * DEG) / 2) + 4);
}

/** Rides to [to] along a gently meandering road; red lights at city junctions now and then. */
function pursue(pen: Pen, to: LatLng, road: RoadKind, next: LatLng | undefined): void {
  const st = STYLE[road];
  const drift = meander(pen.rng, road);
  for (let guard = 0; guard < 200_000; guard++) {
    const dist = haversineM(pen.pos, to);
    const direct = bearing(pen.pos, to);
    const passed = dist < 2 * st.radius && Math.abs(wrap(direct - pen.heading)) > 100 * DEG;
    if (dist < turnInM(pen, to, next, st.radius) || passed) {
      if (road === 'city' && next && pen.luck() < 0.55) pen.stops.push({ at: pen.pts.length - 1, seconds: Math.round(between(pen.luck, 15, 65)) });
      return;
    }
    const fade = Math.min(1, dist / (4 * st.radius + 400));
    step(pen, direct + fade * drift(pen.travelled), st.radius, road);
    if (road === 'city' && pen.luck() < STEP_M / 2600 && pen.luck() < 0.5) pen.stops.push({ at: pen.pts.length - 1, seconds: Math.round(between(pen.luck, 10, 40)) });
  }
  throw new Error('Road builder did not reach its waypoint');
}

/** Switchbacks: legs of 450–600 m at 74–86° either side of [toward], joined by tight 150–170° hairpins. */
function hairpins(pen: Pen, n: number, toward: LatLng): void {
  const base = bearing(pen.pos, toward);
  let side = pen.rng() < 0.5 ? 1 : -1;
  for (let i = 0; i < n; i++) {
    const heading = base + side * between(pen.rng, 74, 86) * DEG;
    const length = between(pen.rng, 450, 600);
    const bend = between(pen.rng, -0.12, 0.12);
    const start = pen.travelled;
    while (pen.travelled - start < length) {
      const along = (pen.travelled - start) / length;
      step(pen, heading + bend * Math.sin(Math.PI * along), HAIRPIN_R, 'ghat');
    }
    side = -side;
  }
}

/** Geometry for [pieces] from [start]. [rng] drives only the shape, so a route can repeat day after day. */
export function buildPath(rng: Rng, luck: Rng, start: LatLng, pieces: Piece[]): RoadPath {
  const first = pieces[0];
  const aim = 'to' in first ? first.to : first.toward;
  const pen: Pen = { pos: start, heading: bearing(start, aim), travelled: 0, pts: [{ ...start, road: 'city' }], stops: [], rng, luck };
  pieces.forEach((p, i) => {
    const after = pieces[i + 1];
    if ('to' in p) pursue(pen, p.to, p.road, after && 'to' in after ? after.to : after?.toward);
    else hairpins(pen, p.hairpins, p.toward);
  });
  return { pts: pen.pts, stops: pen.stops };
}

/**
 * Out-and-back: rests [restSeconds] at the far end, then rides the same road back on the other lane, stopping
 * once within [endFromStartM] of the start (a café short of home) so the ride does not close on itself.
 */
export function withReturn(out: RoadPath, restSeconds: number, endFromStartM: number): RoadPath {
  const start = out.pts[0];
  const back: PathPt[] = [];
  for (let i = out.pts.length - 2; i > 0; i--) {
    const p = out.pts[i];
    const lane = offset(p, bearing(out.pts[i + 1], p) + Math.PI / 2, 6);
    back.push({ ...lane, road: p.road });
    if (haversineM(lane, start) < endFromStartM) break;
  }
  const n = out.pts.length;
  const mirrored = out.stops.map((s) => ({ at: n + (n - 2 - s.at), seconds: s.seconds })).filter((s) => s.at < n + back.length);
  return { pts: [...out.pts, ...back], stops: [...out.stops, { at: n - 1, seconds: restSeconds }, ...mirrored] };
}
