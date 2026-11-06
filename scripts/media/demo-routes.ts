// Where each kind of demo ride goes. Commutes keep one fixed road per direction (same shape every day; only
// speeds and red lights change). Weekend rides never end where they started: the app labels START and END.
import { buildTrack, haversineM, makeRng, trackKm, type Fix, type LatLng, type Rng } from './demo-geo.ts';
import { buildPath, withReturn, type Piece } from './demo-roads.ts';

/** Synthetic spots around lat 18.50, lng 73.80. None of them is a real address. */
export const SPOT = {
  home: { lat: 18.4968, lng: 73.8012 },
  office: { lat: 18.5486, lng: 73.8571 },
  market: { lat: 18.5052, lng: 73.8148 },
  fuel: { lat: 18.5113, lng: 73.8231 },
  workshop: { lat: 18.5235, lng: 73.8402 },
  lake: { lat: 18.3850, lng: 73.5300 },
} as const satisfies Record<string, LatLng>;

export type Kind = 'toWork' | 'toHome' | 'errand' | 'lake' | 'nh48' | 'ghat';

const pt = (lat: number, lng: number): LatLng => ({ lat, lng });
const city = (to: LatLng): Piece => ({ to, road: 'city' });
const hwy = (to: LatLng): Piece => ({ to, road: 'highway' });
const ghat = (to: LatLng): Piece => ({ to, road: 'ghat' });

const WEST_EXIT = pt(18.4905, 73.7790);
const GHAT_FOOT = pt(18.2333, 73.7160);

type Route = { from: LatLng; pieces: Piece[]; top: number; shapeSeed?: number; back?: { restS: [number, number]; endFromStartM: number } };

/** Home to office along Main Rd; back by the road past the market. Four junction turns each way. */
const ROUTES: Record<Kind, Route> = {
  toWork: {
    from: SPOT.home, top: 68, shapeSeed: 9001,
    pieces: [city(pt(18.5050, 73.8035)), city(pt(18.5130, 73.8260)), city(pt(18.5370, 73.8420)), city(pt(18.5420, 73.8600)), city(SPOT.office)],
  },
  toHome: {
    from: SPOT.office, top: 66, shapeSeed: 9002,
    pieces: [city(pt(18.5330, 73.8590)), city(pt(18.5190, 73.8330)), city(pt(18.5020, 73.8150)), city(pt(18.5060, 73.8030)), city(SPOT.home)],
  },
  errand: { from: SPOT.home, top: 56, shapeSeed: 9003, pieces: [city(pt(18.4985, 73.8150)), city(SPOT.market), city(SPOT.fuel), city(pt(18.5230, 73.8250)), city(SPOT.workshop)] },
  lake: {
    from: SPOT.home, top: 98, pieces: [city(WEST_EXIT), hwy(pt(18.4420, 73.6550)), hwy(SPOT.lake)],
    back: { restS: [600, 1500], endFromStartM: 3000 },
  },
  nh48: {
    from: SPOT.home, top: 108,
    pieces: [city(WEST_EXIT), hwy(pt(18.6400, 73.7300)), hwy(pt(18.8454, 73.6403)), hwy(pt(18.7873, 73.9413)), hwy(pt(18.5600, 73.8450)), city(pt(18.5262, 73.8205))],
  },
  ghat: {
    from: SPOT.home, top: 104,
    pieces: [
      city(WEST_EXIT), hwy(pt(18.3900, 73.7350)), hwy(pt(18.2800, 73.6650)), ghat(pt(18.2600, 73.6850)),
      { hairpins: 11, toward: GHAT_FOOT }, ghat(pt(18.2450, 73.7000)), ghat(GHAT_FOOT), hwy(pt(18.1945, 73.8810)), hwy(pt(18.4550, 73.8500)), city(SPOT.workshop),
    ],
  },
};

/** One ride of [kind]: its fixes (from 0 ms), length and how far it ends from where it started. */
export function rideFor(rng: Rng, kind: Kind): { track: Fix[]; km: number; startEndM: number } {
  const r = ROUTES[kind];
  const shapeRng = r.shapeSeed === undefined ? rng : makeRng(r.shapeSeed);
  let path = buildPath(shapeRng, rng, r.from, r.pieces);
  if (r.back) path = withReturn(path, Math.round(r.back.restS[0] + (r.back.restS[1] - r.back.restS[0]) * rng()), r.back.endFromStartM);
  const track = buildTrack(rng, path, r.top, 600);
  const startEndM = haversineM(track[0], track[track.length - 1]);
  return { track, km: trackKm(track), startEndM };
}
