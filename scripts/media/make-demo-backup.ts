// Builds a Safar export (safar-backup zip) full of synthetic riding history, for store screenshots.
// Import it from Settings → "Import from a file". Same --now (and TZ) gives the same file.
//
//   TZ=Asia/Kolkata node scripts/media/make-demo-backup.ts <outDir> [--now <epochMillis|ISO date>] [--seed <n>]
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { bearing, haversineM, makeRng, trackKm, wrap, type LatLng, type Rng } from './demo-geo.ts';
import { buildTrips, localAt, type DemoTrip } from './demo-trips.ts';
import { placeholderPdf, zip, type ZipEntry } from './demo-files.ts';
import {
  LIVE_ODO, SETTINGS, applyFuelBars, buildFuelUps, buildParked, buildPlaces, buildService, mileageSegments, recentKmpl,
  type FuelUp,
} from './demo-data.ts';

const DAY = 86_400_000;
const APP_VERSION = '0.2.0';

type Args = { outDir: string; now: number; seed: number };

function parseArgs(argv: string[]): Args {
  const flag = (name: string): string | undefined => {
    const i = argv.indexOf(name);
    return i >= 0 ? argv[i + 1] : undefined;
  };
  const outDir = argv.find((a, i) => !a.startsWith('--') && !argv[i - 1]?.startsWith('--'));
  if (!outDir) throw new Error('Usage: node scripts/media/make-demo-backup.ts <outDir> [--now <epochMillis|ISO>] [--seed <n>]');
  const rawNow = flag('--now');
  const now = rawNow === undefined ? Date.now() : /^\d+$/.test(rawNow) ? Number(rawNow) : Date.parse(rawNow);
  if (!Number.isFinite(now)) throw new Error(`Not a time: ${rawNow}`);
  return { outDir, now, seed: Number(flag('--seed') ?? 2611) };
}

const epochDay = (millis: number): number => {
  const d = new Date(millis);
  return Math.round(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) / DAY);
};
const isoDay = (day: number): string => new Date(day * DAY).toISOString().slice(0, 10);

function uuid(rng: Rng): string {
  const hex = Array.from({ length: 32 }, () => Math.floor(rng() * 16).toString(16)).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-a${hex.slice(17, 20)}-${hex.slice(20)}`;
}

type Paper = { dto: object; file: ZipEntry };

/** Two sample papers: insurance runs out in 20 days (the warning card), PUC in about four months. */
function buildPapers(rng: Rng, now: number): Paper[] {
  const today = epochDay(now);
  const specs = [
    { id: 1, title: 'Insurance', kind: 'INSURANCE', added: 41, expires: 20, note: 'Renew before the trip in December', heading: 'INSURANCE', rgb: [0.11, 0.25, 0.52], stamp: 'INSURED' },
    { id: 2, title: 'PUC certificate', kind: 'PUC', added: 58, expires: 127, note: null, heading: 'PUC CERTIFICATE', rgb: [0.1, 0.45, 0.28], stamp: 'PASS' },
  ] as const;
  return specs.map((s) => {
    const data = placeholderPdf({
      title: s.heading, rgb: [...s.rgb], stamp: s.stamp,
      rows: [['Rider', 'Alex'], ['Bike', 'SBM01A1B2C3D'], ['Model', 'V-Strom SX'], ['Valid until', isoDay(today + s.expires)]],
    });
    const fileName = `${uuid(rng)}.pdf`;
    const dto = {
      id: s.id, title: s.title, kind: s.kind, fileName, mimeType: 'application/pdf', sizeBytes: data.length,
      addedMillis: localAt(now, s.added, 20, 15), expiryEpochDay: today + s.expires, expiryDate: isoDay(today + s.expires), note: s.note,
    };
    return { dto, file: { name: `documents/${fileName}`, data } };
  });
}

const tripDto = (t: DemoTrip): object => ({
  id: t.id, title: t.title, startMillis: t.startMillis, endMillis: t.endMillis, distanceKm: t.distanceKm,
  topSpeedKmh: t.topSpeedKmh, avgSpeedKmh: t.avgSpeedKmh, odoStartKm: t.odoStartKm, odoEndKm: t.odoEndKm,
  fuelBarsStart: t.fuelBarsStart, fuelBarsEnd: t.fuelBarsEnd,
  track: t.track.map((f) => ({ atMillis: f.atMillis, lat: Math.round(f.lat * 1e6) / 1e6, lng: Math.round(f.lng * 1e6) / 1e6, speedKmh: Math.round(f.speedKmh * 10) / 10 })),
});

/** Corners of the drawn route: heading changes of more than 25° within 150 m, counted once each. */
function turns(t: DemoTrip): number {
  const marks: LatLng[] = [t.track[0]];
  for (const f of t.track) if (haversineM(marks[marks.length - 1], f) >= 50) marks.push(f);
  let count = 0;
  let cooldown = 0;
  for (let i = 3; i < marks.length; i++) {
    const change = Math.abs(wrap(bearing(marks[i - 1], marks[i]) - bearing(marks[i - 3], marks[i - 2])));
    if (cooldown > 0) cooldown--;
    else if (change > (25 * Math.PI) / 180) {
      count++;
      cooldown = 4;
    }
  }
  return count;
}

const day = (ms: number): string => new Date(ms).toString().slice(0, 21);

function logTrips(trips: DemoTrip[]): void {
  console.log('\nRides (newest first)');
  for (const t of trips) {
    const gps = trackKm(t.track);
    const diff = ((gps - t.distanceKm) / t.distanceKm) * 100;
    const odo = t.odoEndKm - t.odoStartKm;
    const min = Math.round((t.endMillis - t.startMillis) / 60_000);
    console.log(`  #${String(t.id).padStart(2)} ${day(t.startMillis)}  ${t.title.padEnd(16)} ${t.distanceKm.toFixed(1).padStart(6)} km (gps ${diff >= 0 ? '+' : ''}${diff.toFixed(2)}%, odo ${odo})  ${String(min).padStart(3)} min  avg ${t.avgSpeedKmh}  top ${t.topSpeedKmh}  odo ${t.odoStartKm}-${t.odoEndKm}  bars ${t.fuelBarsStart}->${t.fuelBarsEnd}  ${t.track.length} pts`);
    console.log(`      start to end ${(t.startEndM / 1000).toFixed(2)} km, ${turns(t)} turns over 25°`);
    if (Math.abs(diff) > 2) throw new Error(`Ride ${t.id}: track length is ${diff.toFixed(1)}% off its distance`);
    if (t.startEndM < 2500) throw new Error(`Ride ${t.id}: ends ${Math.round(t.startEndM)} m from its start; START and END labels would overlap`);
  }
}

function logFuel(fuelUps: FuelUp[], barsNow: number): void {
  console.log('\nFuel-ups (oldest first)');
  for (const f of fuelUps) {
    console.log(`  #${f.id} ${day(f.atMillis)}  ${f.odometerKm} km  ${f.litres.toFixed(2)} L  ₹${f.totalPaid.toFixed(2)} (₹${(f.totalPaid / f.litres).toFixed(2)}/L)  ${f.full ? 'full   ' : 'partial'}  ${f.odoSource}  bars ${f.barsBefore ?? '-'}`);
  }
  const segs = mileageSegments(fuelUps);
  for (const s of segs) console.log(`  full-to-full ${s.fromOdo} -> ${s.toOdo}: ${s.km} km / ${s.litres} L = ${s.kmpl} km/L`);
  const avg = recentKmpl(segs);
  const lastFull = [...fuelUps].reverse().find((f) => f.full);
  const left = lastFull ? Math.round(Math.min(12, 12 - (LIVE_ODO - lastFull.odometerKm) / avg) * 10) / 10 : 0;
  console.log(`  avg (last 3) ${avg} km/L; at ${LIVE_ODO} km about ${left} L left, range ${Math.round(left * avg)} km, gauge ${barsNow}/6 bars`);
}

function main(): void {
  const args = parseArgs(process.argv.slice(2));
  const rng = makeRng(args.seed);
  const trips = buildTrips(rng, args.now);
  const oldestFirst = [...trips].reverse();
  const { fuelUps, rates } = buildFuelUps(rng, args.now, oldestFirst);
  const barsNow = applyFuelBars(trips, fuelUps, rates);
  const papers = buildPapers(rng, args.now);
  const data = {
    trips: oldestFirst.map(tripDto), fuelUps, serviceItems: buildService(args.now, trips), places: buildPlaces(),
    parkedSpot: buildParked(rng, trips[0]), documents: papers.map((p) => p.dto), settings: SETTINGS,
    savedBike: { name: 'SBM01A1B2C3D', address: '00:00:00:00:00:00' },
  };
  const counts = {
    trips: data.trips.length, trackPoints: trips.reduce((n, t) => n + t.track.length, 0), fuelUps: fuelUps.length,
    serviceItems: data.serviceItems.length, places: data.places.length, parkedSpot: true, documents: papers.length,
  };
  const manifest = { format: 'safar-backup', formatVersion: 1, appVersion: APP_VERSION, exportedAtMillis: args.now, counts };
  const entries: ZipEntry[] = [
    { name: 'manifest.json', data: Buffer.from(JSON.stringify(manifest, null, 2)) },
    { name: 'data.json', data: Buffer.from(JSON.stringify(data, null, 2)) },
    ...papers.map((p) => p.file),
  ];
  mkdirSync(args.outDir, { recursive: true });
  const out = join(args.outDir, `safar-backup-${isoDay(epochDay(args.now))}.zip`);
  writeFileSync(out, zip(entries, args.now));
  console.log(`now ${day(args.now)} (${args.now}), seed ${args.seed}`);
  logTrips(trips);
  logFuel(fuelUps, barsNow);
  console.log(`\nCounts ${JSON.stringify(counts)}\nWrote ${out}`);
}

process.on('unhandledRejection', (e) => {
  console.error('make-demo-backup failed:', e);
  process.exit(1);
});
main();
