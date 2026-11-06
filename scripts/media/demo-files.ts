// A tiny deflate zip writer and a one-page placeholder PDF. Deterministic: no clocks, no randomness.
import { crc32, deflateRawSync } from 'node:zlib';

export type ZipEntry = { name: string; data: Buffer };

function dosTime(millis: number): { time: number; date: number } {
  const d = new Date(millis);
  return {
    time: (d.getHours() << 11) | (d.getMinutes() << 5) | Math.floor(d.getSeconds() / 2),
    date: ((d.getFullYear() - 1980) << 9) | ((d.getMonth() + 1) << 5) | d.getDate(),
  };
}

function header(sig: number, size: number): Buffer {
  const b = Buffer.alloc(size);
  b.writeUInt32LE(sig, 0);
  return b;
}

/** Zip with entries in the given order (the app wants manifest.json before data.json), all deflated. */
export function zip(entries: ZipEntry[], millis: number): Buffer {
  const { time, date } = dosTime(millis);
  const locals: Buffer[] = [];
  const centrals: Buffer[] = [];
  let offset = 0;
  for (const e of entries) {
    const name = Buffer.from(e.name, 'utf8');
    const body = deflateRawSync(e.data, { level: 9 });
    const crc = crc32(e.data);
    const local = header(0x04034b50, 30);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(8, 8);
    local.writeUInt16LE(time, 10);
    local.writeUInt16LE(date, 12);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(body.length, 18);
    local.writeUInt32LE(e.data.length, 22);
    local.writeUInt16LE(name.length, 26);
    const central = header(0x02014b50, 46);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    local.copy(central, 8, 6, 30);
    central.writeUInt32LE(offset, 42);
    locals.push(local, name, body);
    centrals.push(central, name);
    offset += local.length + name.length + body.length;
  }
  const dir = Buffer.concat(centrals);
  const end = header(0x06054b50, 22);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(dir.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, dir, end]);
}

const pdfText = (s: string): string => s.replace(/[\\()]/g, (c) => `\\${c}`);

export type PaperLook = { title: string; rgb: [number, number, number]; rows: Array<[string, string]>; stamp: string };

const W = 595;
const H = 420;
/** The app crops a square from the middle of the page for its thumbnail: x 87.5–507.5 on this page. */
const X0 = 100;

const col = (rgb: readonly number[], op: 'rg' | 'RG'): string => `${rgb.map((n) => n.toFixed(2)).join(' ')} ${op}`;
const text = (font: 'F1' | 'F2', size: number, x: number, y: number, s: string): string => `BT /${font} ${size} Tf ${x} ${y} Td (${pdfText(s)}) Tj ET`;
const bar = (x: number, y: number, w: number, h: number, grey: number): string => `${grey} g ${x} ${y} ${w} ${h} re f`;

/** A circle as four Bezier quarters, stroked. */
function circle(cx: number, cy: number, r: number): string {
  const k = 0.5523 * r;
  return [
    `${cx + r} ${cy} m`, `${cx + r} ${cy + k} ${cx + k} ${cy + r} ${cx} ${cy + r} c`, `${cx - k} ${cy + r} ${cx - r} ${cy + k} ${cx - r} ${cy} c`,
    `${cx - r} ${cy - k} ${cx - k} ${cy - r} ${cx} ${cy - r} c`, `${cx + k} ${cy - r} ${cx + r} ${cy - k} ${cx + r} ${cy} c`, 'S',
  ].join(' ');
}

/** Watermark, coloured header band with the title, grey text lines, a details table, a round stamp. */
function pageOps(look: PaperLook): string {
  const [r, g, b] = look.rgb;
  const tableTop = 262;
  const rowH = 26;
  const rows = look.rows.flatMap(([k, v], i) => [text('F1', 9, X0 + 8, tableTop - rowH * (i + 1) + 9, k), text('F2', 10, X0 + 98, tableTop - rowH * (i + 1) + 9, v)]);
  const lines = look.rows.slice(1).map((_, i) => `${X0} ${tableTop - rowH * (i + 1)} m ${X0 + 250} ${tableTop - rowH * (i + 1)} l S`);
  return [
    `BT /F2 92 Tf ${col([1, 0.86, 0.86], 'rg')} 0.906 0.423 -0.423 0.906 120 70 Tm (SAMPLE) Tj ET`,
    `${col(look.rgb, 'rg')} 0 ${H - 78} ${W} 78 re f`,
    `1 g ${text('F2', 28, X0, H - 50, look.title)}`, `0.9 g ${text('F1', 10, X0, H - 68, 'Two-wheeler  -  issued to the registered owner')}`,
    bar(X0, 312, 300, 9, 0.45), bar(X0, 294, 230, 6, 0.75), bar(X0, 281, 270, 6, 0.75),
    `0.55 G 1 w ${X0} ${tableTop - rowH * look.rows.length} 250 ${rowH * look.rows.length} re S`, ...lines,
    `${X0 + 90} ${tableTop} m ${X0 + 90} ${tableTop - rowH * look.rows.length} l S`, `0.15 g`, ...rows,
    bar(X0, 132, 330, 6, 0.8), bar(X0, 119, 290, 6, 0.8), bar(X0, 106, 310, 6, 0.8), bar(X0, 93, 180, 6, 0.8),
    `${col([r, g, b], 'RG')} 3 w ${circle(432, 196, 52)}`, `1.2 w ${circle(432, 196, 42)}`,
    `${col([r, g, b], 'rg')} ${text('F2', 15, 432 - look.stamp.length * 5.1, 191, look.stamp)}`,
    `0.5 g ${text('F1', 8, X0, 60, 'SAMPLE DOCUMENT - not a real certificate. Made for Safar screenshots.')}`,
    `0.8 G 0.8 w 12 12 ${W - 24} ${H - 24} re S`,
  ].join('\n');
}

/** A valid one-page A5-landscape PDF that looks like a certificate, even as a 60 px thumbnail. */
export function placeholderPdf(look: PaperLook): Buffer {
  const body = pageOps(look);
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${W} ${H}] /Resources << /Font << /F1 4 0 R /F2 6 0 R >> >> /Contents 5 0 R >>`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${Buffer.byteLength(body, 'latin1')} >>\nstream\n${body}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>',
  ];
  let out = '%PDF-1.4\n';
  const offsets: number[] = [];
  objects.forEach((o, i) => {
    offsets.push(Buffer.byteLength(out, 'latin1'));
    out += `${i + 1} 0 obj\n${o}\nendobj\n`;
  });
  const xref = Buffer.byteLength(out, 'latin1');
  out += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  out += offsets.map((n) => `${String(n).padStart(10, '0')} 00000 n \n`).join('');
  out += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(out, 'latin1');
}
