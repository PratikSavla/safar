// The demo rides: when they happen, where they go, and an odometer chain that ends at the BikeSim reading.
import { between, movingAvgKmh, type Fix, type Rng } from './demo-geo.ts';
import { rideFor, type Kind } from './demo-routes.ts';

export { SPOT } from './demo-routes.ts';

/** Odometer when the rider last filled to full: BikeSim's Trip A (84.6 km) counts from here. */
export const LAST_FULL_ODO = 12_176;
/** Highest odometer a ride may end at; BikeSim's live odometer reads 12 261. */
export const LAST_TRIP_END_ODO = 12_255;

type Plan = { kind: Kind; daysAgo: number; hour: number; minute: number; title?: string };

export type DemoTrip = {
  id: number; title: string; startMillis: number; endMillis: number; distanceKm: number;
  topSpeedKmh: number; avgSpeedKmh: number; odoStartKm: number; odoEndKm: number;
  fuelBarsStart: number; fuelBarsEnd: number; track: Fix[]; kind: Kind; fillAfter: boolean; startEndM: number;
};

/** Same rule as the app's autoTripTitle. */
export function autoTitle(hour: number): string {
  if (hour >= 5 && hour <= 11) return 'Morning ride';
  if (hour >= 12 && hour <= 16) return 'Afternoon ride';
  if (hour >= 17 && hour <= 20) return 'Evening ride';
  return 'Night ride';
}

/** Local midnight [daysAgo] days before [now], plus [hour]:[minute]. */
export function localAt(now: number, daysAgo: number, hour: number, minute: number): number {
  const d = new Date(now);
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - daysAgo);
  d.setHours(hour, minute, 0, 0);
  return d.getTime();
}

const weekday = (now: number, daysAgo: number): number => new Date(localAt(now, daysAgo, 12, 0)).getDay();

/** Weekend rides on real weekends; commutes on weekdays. The ghat ride is the last Sunday at least 2 days back. */
function schedule(now: number): { plans: Plan[]; postFill: number } {
  let ghat = 2;
  while (weekday(now, ghat) !== 0) ghat++;
  const recent: Plan[] = [];
  const todayKind: Kind = weekday(now, 0) % 6 === 0 ? 'errand' : new Date(now).getHours() < 15 ? 'toWork' : 'toHome';
  recent.push({ kind: todayKind, daysAgo: 0, hour: -1, minute: 0 });
  for (let d = 1; d < ghat && recent.length < 4; d++) {
    if (weekday(now, d) % 6 === 0) continue;
    recent.push({ kind: 'toHome', daysAgo: d, hour: 18, minute: 20 });
    if (recent.length < 4) recent.push({ kind: 'toWork', daysAgo: d, hour: 8, minute: 50 });
  }
  const g = (k: number, kind: Kind, hour: number, minute: number, title?: string): Plan => ({ kind, daysAgo: ghat + k, hour, minute, title });
  const older: Plan[] = [
    g(0, 'ghat', 6, 5, 'Ghat ride'), g(1, 'errand', 11, 20, 'Main Rd errands'), g(2, 'toHome', 18, 35),
    g(4, 'toWork', 8, 45), g(5, 'toHome', 18, 50), g(8, 'nh48', 6, 25, 'Ride on NH 48'), g(9, 'toWork', 9, 5),
    g(11, 'toHome', 18, 15), g(14, 'lake', 16, 10, 'To the lake'), g(16, 'toWork', 8, 35), g(19, 'toHome', 19, 5),
    g(25, 'toWork', 8, 55),
  ];
  return { plans: [...recent, ...older], postFill: recent.length };
}

/** Splits [total] whole km into [n] gaps (unrecorded short hops), each at least 0. */
function splitGaps(rng: Rng, total: number, n: number): number[] {
  const w = Array.from({ length: n }, () => between(rng, 0.5, 1.5));
  const sum = w.reduce((a, b) => a + b, 0);
  const gaps = w.map((x) => Math.floor((x / sum) * total));
  gaps[0] += total - gaps.reduce((a, b) => a + b, 0);
  return gaps;
}

/** Newest first: odometers walked back from [LAST_TRIP_END_ODO], with the ghat ride ending at [LAST_FULL_ODO]. */
function odometers(rng: Rng, kms: number[], postFill: number): Array<[number, number]> {
  const whole = kms.map((k) => Math.max(1, Math.round(k)));
  const postSum = whole.slice(0, postFill).reduce((a, b) => a + b, 0);
  const spare = LAST_TRIP_END_ODO - LAST_FULL_ODO - postSum;
  if (spare < 0) throw new Error(`Recent rides (${postSum} km) do not fit after the last fill`);
  const postGaps = splitGaps(rng, spare, postFill);
  const out: Array<[number, number]> = [];
  let cursor = LAST_TRIP_END_ODO;
  whole.forEach((km, i) => {
    out.push([cursor - km, cursor]);
    const gap = i < postFill ? postGaps[i] : Math.round(between(rng, 14, 46));
    cursor = cursor - km - gap;
  });
  return out;
}

/** Newest first, without fuel bars yet (they come from the fuel log). */
export function buildTrips(rng: Rng, now: number): DemoTrip[] {
  const { plans, postFill } = schedule(now);
  const rides = plans.map((p) => rideFor(rng, p.kind));
  const odos = odometers(rng, rides.map((r) => r.km), postFill);
  return plans.map((p, i) => {
    const { track, km } = rides[i];
    const duration = track[track.length - 1].atMillis;
    const start = p.hour < 0
      ? now - Math.round(between(rng, 42, 46) * 60_000) - duration
      : localAt(now, p.daysAgo, p.hour, p.minute) + Math.round(between(rng, -12, 12)) * 60_000;
    const shifted = track.map((f) => ({ ...f, atMillis: f.atMillis + start }));
    const top = Math.max(...track.map((f) => f.speedKmh));
    return {
      id: plans.length - i, title: p.title ?? autoTitle(new Date(start).getHours()), startMillis: start,
      endMillis: start + duration, distanceKm: Math.round(km * 10) / 10, topSpeedKmh: Math.round(top * 10) / 10,
      avgSpeedKmh: Math.round(movingAvgKmh(track) * 10) / 10, odoStartKm: odos[i][0], odoEndKm: odos[i][1],
      fuelBarsStart: 0, fuelBarsEnd: 0, track: shifted, kind: p.kind, fillAfter: p.kind === 'ghat', startEndM: rides[i].startEndM,
    };
  });
}
