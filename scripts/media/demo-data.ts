// Fuel log, fuel bars, service logbook, places, parked spot and settings for the demo backup.
import { between, offset, type Rng } from './demo-geo.ts';
import { LAST_FULL_ODO, SPOT, localAt, type DemoTrip } from './demo-trips.ts';

export const TANK_L = 12;
const BARS = 6;
export const LIVE_ODO = 12_261;
const DAY = 86_400_000;

export type FuelUp = {
  id: number; atMillis: number; odometerKm: number; litres: number; totalPaid: number;
  full: boolean; odoSource: 'BIKE' | 'EDITED' | 'MANUAL'; barsBefore: number | null;
};

const r2 = (n: number): number => Math.round(n * 100) / 100;
const bars = (litres: number): number => Math.max(0, Math.min(BARS, Math.floor((litres + 0.6) / (TANK_L / BARS))));

/** Time the bike passed [odo]: between trip ends and starts, or extrapolated before the first trip. */
export function timeAtOdo(trips: DemoTrip[], odo: number): number {
  const knots = trips.flatMap((t) => [[t.odoStartKm, t.startMillis], [t.odoEndKm, t.endMillis]]).sort((a, b) => a[0] - b[0]);
  const [o0, t0] = knots[0];
  const [o1, t1] = knots[knots.length - 1];
  const rate = (o1 - o0) / (t1 - t0);
  if (odo <= o0) return t0 - (o0 - odo) / rate;
  for (let i = 1; i < knots.length; i++) {
    const [oa, ta] = knots[i - 1];
    const [ob, tb] = knots[i];
    if (odo <= ob) return ob === oa ? tb : ta + ((odo - oa) / (ob - oa)) * (tb - ta);
  }
  return t1;
}

/** [time]'s local day at [hour]:[minute]. */
function onDayAt(now: number, time: number, hour: number, minute: number): number {
  const daysAgo = Math.round((localAt(now, 0, 12, 0) - localAt(time, 0, 12, 0)) / DAY);
  return localAt(now, daysAgo, hour, minute);
}

type Slot = { odo: number; at: number; full: boolean; onBike: boolean };

const minutes = (rng: Rng, lo: number, hi: number): number => Math.round(between(rng, lo, hi)) * 60_000;

/**
 * A fill near [odo]: at the end of a ride when one ends within 30 km (bike connected), before a ride the
 * odometer falls inside, else between rides at about 7 pm (typed in by hand).
 */
function slotNear(rng: Rng, now: number, trips: DemoTrip[], odo: number, full: boolean): Slot {
  const end = trips.find((t) => !t.fillAfter && Math.abs(t.odoEndKm - odo) <= 30);
  if (end) return { odo: end.odoEndKm, at: end.endMillis + minutes(rng, 6, 15), full, onBike: true };
  const inside = trips.find((t) => t.odoStartKm <= odo && odo <= t.odoEndKm);
  if (inside) return { odo: inside.odoStartKm, at: inside.startMillis - minutes(rng, 10, 20), full, onBike: false };
  const raw = timeAtOdo(trips, odo);
  const before = trips.filter((t) => t.endMillis < raw).at(-1);
  const after = trips.find((t) => t.startMillis > raw);
  const evening = onDayAt(now, raw, 19, Math.round(between(rng, 0, 50)));
  const fits = (!before || evening > before.endMillis + 20 * 60_000) && (!after || evening < after.startMillis - 20 * 60_000);
  return { odo, at: fits ? evening : raw, full, onBike: false };
}

/** Fill odometers walked back from the last full fill (after the ghat ride), oldest first. */
function fillSlots(rng: Rng, now: number, trips: DemoTrip[]): Slot[] {
  const fullPattern = [true, true, false, true, true, false, true, true, true];
  const ghat = trips.find((t) => t.fillAfter);
  if (!ghat) throw new Error('No ride marks the last full fill');
  const slots: Slot[] = [{ odo: LAST_FULL_ODO, at: ghat.endMillis + 14 * 60_000, full: true, onBike: true }];
  let odo = LAST_FULL_ODO;
  for (let i = 1; i < fullPattern.length; i++) {
    const slot = slotNear(rng, now, trips, odo - Math.round(between(rng, 150, 265)), fullPattern[i]);
    odo = slot.odo;
    slots.push(slot);
  }
  return slots.reverse();
}

/** Price per litre by age: two small revisions over the two months. */
const priceAt = (now: number, at: number): number => (now - at > 45 * DAY ? 104.61 : now - at > 20 * DAY ? 105.29 : 106.12);

type Amount = { litres: number; paid: number; full: boolean };

/**
 * What the pump said: a full fill tops the tank up; a partial one is a round amount of money, enough to reach
 * the next fill with a litre or so to spare ([need] litres). No round amount fits: it becomes a full fill.
 */
function fillAmount(rng: Rng, first: boolean, full: boolean, level: number, need: number, price: number): Amount {
  if (first) {
    const litres = r2(between(rng, 6.5, 8.5));
    return { litres, paid: r2(litres * price), full: true };
  }
  const paid = [500, 600, 700, 800].find((m) => level + m / price >= need + 1.2 && m / price <= TANK_L - level - 0.3);
  if (full || paid === undefined) {
    const litres = r2(TANK_L - level + between(rng, -0.12, 0.12));
    return { litres, paid: r2(litres * price), full: true };
  }
  return { litres: r2(paid / price), paid, full: false };
}

/** Oldest first. Each full-to-full stretch burns fuel at its own 27.5–30.5 km/L; [rates] are per stretch. */
export function buildFuelUps(rng: Rng, now: number, trips: DemoTrip[]): { fuelUps: FuelUp[]; rates: number[] } {
  const slots = fillSlots(rng, now, trips);
  const rates: number[] = [];
  const out: FuelUp[] = [];
  let level = TANK_L;
  let rate = between(rng, 27.5, 30.5);
  slots.forEach((s, i) => {
    if (i > 0) level -= (s.odo - slots[i - 1].odo) / rate;
    if (level < 0.8) throw new Error(`Tank would run dry before the fill at ${s.odo} km (${level.toFixed(1)} L)`);
    const need = i + 1 < slots.length ? (slots[i + 1].odo - s.odo) / rate : 0;
    const { litres, paid, full } = fillAmount(rng, i === 0, s.full, level, need, priceAt(now, s.at));
    const before = i === 0 ? TANK_L - litres : level;
    const source = s.onBike ? 'BIKE' : i === 1 ? 'EDITED' : 'MANUAL';
    out.push({ id: i + 1, atMillis: s.at, odometerKm: s.odo, litres, totalPaid: paid, full, odoSource: source, barsBefore: s.onBike ? bars(before) : null });
    level = full ? TANK_L : level + litres;
    if (full && i > 0) rate = between(rng, 27.5, 30.5);
    rates.push(rate);
  });
  return { fuelUps: out, rates };
}

export type Segment = { fromOdo: number; toOdo: number; km: number; litres: number; kmpl: number };

/** Same as the app's mileageSegments: km between full fills over the litres added up to the closing one. */
export function mileageSegments(fuelUps: FuelUp[]): Segment[] {
  const out: Segment[] = [];
  let start: FuelUp | null = null;
  let litres = 0;
  for (const f of [...fuelUps].sort((a, b) => a.odometerKm - b.odometerKm)) {
    if (start) litres += f.litres;
    if (!f.full) continue;
    if (start && f.odometerKm > start.odometerKm) {
      const km = f.odometerKm - start.odometerKm;
      out.push({ fromOdo: start.odometerKm, toOdo: f.odometerKm, km, litres: r2(litres), kmpl: Math.round((km / litres) * 10) / 10 });
    }
    start = f;
    litres = 0;
  }
  return out;
}

/** The app's "average" mileage: the last three full-to-full stretches together. */
export function recentKmpl(segs: Segment[]): number {
  const last = segs.slice(-3);
  return Math.round((last.reduce((a, s) => a + s.km, 0) / last.reduce((a, s) => a + s.litres, 0)) * 10) / 10;
}

/** Litres in the tank at [odo]; [afterFill] counts a fill made at exactly that odometer. */
function levelAt(fuelUps: FuelUp[], rates: number[], odo: number, afterFill: boolean): number {
  let level = TANK_L;
  let prev = fuelUps[0].odometerKm;
  let rate = rates[0];
  fuelUps.forEach((f, i) => {
    if (f.odometerKm > odo || (f.odometerKm === odo && !afterFill)) return;
    level = f.full ? TANK_L : level - (f.odometerKm - prev) / rate + f.litres;
    prev = f.odometerKm;
    rate = rates[i];
  });
  return Math.max(0.3, level - (odo - prev) / rate);
}

/** Fills in each ride's fuel bars from the fuel log (bars only drop while riding). Returns the bars now. */
export function applyFuelBars(trips: DemoTrip[], fuelUps: FuelUp[], rates: number[]): number {
  for (const t of trips) {
    t.fuelBarsStart = bars(levelAt(fuelUps, rates, t.odoStartKm, true));
    t.fuelBarsEnd = bars(levelAt(fuelUps, rates, t.odoEndKm, false));
  }
  return bars(levelAt(fuelUps, rates, LIVE_ODO, true));
}

/** The app's default items. Brakes and oil also run on time; chain is overdue, brakes due soon by time. */
export function buildService(now: number, trips: DemoTrip[]): object[] {
  const daysAgo = (d: number): number => localAt(now, d, 10, 30);
  const atOdo = (o: number): number => onDayAt(now, timeAtOdo(trips, o), 10, 30);
  return [
    { id: 1, label: 'Engine oil', everyKm: 3000, lastDoneOdoKm: 9874, lastDoneMillis: daysAgo(104), icon: 'OIL', everyTimeCount: 6, everyTimeUnit: 'MONTHS' },
    { id: 2, label: 'Chain clean & lube', everyKm: 500, lastDoneOdoKm: 11_655, lastDoneMillis: atOdo(11_655), icon: 'CHAIN' },
    { id: 3, label: 'Air filter', everyKm: 6000, lastDoneOdoKm: 7960, lastDoneMillis: daysAgo(190), icon: 'AIR' },
    { id: 4, label: 'Brake pads check', everyKm: 5000, lastDoneOdoKm: 8402, lastDoneMillis: daysAgo(168), icon: 'BRAKE', everyTimeCount: 6, everyTimeUnit: 'MONTHS' },
    { id: 5, label: 'Tyre pressure', everyKm: 300, lastDoneOdoKm: 12_034, lastDoneMillis: atOdo(12_034), icon: 'TYRE' },
  ];
}

const q = (p: { lat: number; lng: number }): string => `${p.lat.toFixed(6)},${p.lng.toFixed(6)}`;

export function buildPlaces(): object[] {
  const list = [
    { label: 'Home', detail: 'Main Rd', kind: 'HOME', source: 'MAPS', p: SPOT.home },
    { label: 'Office', detail: 'Market St', kind: 'WORK', source: 'MAPS', p: SPOT.office },
    { label: 'Fuel stop, Main Rd', detail: 'Main Rd', kind: 'FUEL', source: 'TYPED', p: SPOT.fuel },
    { label: 'Lake view point', detail: 'Lake Rd', kind: 'STAR', source: 'MAPS', p: SPOT.lake },
    { label: 'Workshop', detail: 'NH 48', kind: 'STAR', source: 'HERE', p: SPOT.workshop },
  ];
  return list.map((x, i) => ({ id: i + 1, label: x.label, detail: x.detail, query: q(x.p), kind: x.kind, source: x.source, sortOrder: i, lat: x.p.lat, lng: x.p.lng }));
}

/** A few metres from where the newest ride ended, a couple of minutes after it. */
export function buildParked(rng: Rng, newest: DemoTrip): object {
  const end = newest.track[newest.track.length - 1];
  const p = offset(end, between(rng, 0, 2 * Math.PI), between(rng, 8, 20));
  const label = newest.kind === 'toWork' ? 'Market St' : null;
  return { lat: Math.round(p.lat * 1e6) / 1e6, lng: Math.round(p.lng * 1e6) / 1e6, accuracyM: 7, atMillis: newest.endMillis + 3 * 60_000, label };
}

export const SETTINGS = {
  riderName: 'Alex', speedAlertOn: true, speedAlertKmh: 80, autoRideMode: true, serviceReminders: true,
  lowFuelAlert: true, documentReminders: true, tankLitres: 12.0, currencySymbol: '₹',
} as const;
