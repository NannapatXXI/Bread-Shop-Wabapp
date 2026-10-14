import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { exportCSV } from '@/lib/exportCSV';

// jsdom's Blob has no .text(); read raw bytes and keep the BOM so we can assert it
const readBlob = (blob) => new Promise((resolve) => {
  const reader = new FileReader();
  reader.onload = () => resolve(new TextDecoder('utf-8', { ignoreBOM: true }).decode(reader.result));
  reader.readAsArrayBuffer(blob);
});

describe('exportCSV', () => {
  let blobs;
  let clicked;

  beforeEach(() => {
    blobs = [];
    clicked = [];
    // jsdom ไม่มี createObjectURL/revokeObjectURL — ใส่ของปลอมที่จดว่าได้ Blob อะไรมา
    URL.createObjectURL = vi.fn((blob) => {
      blobs.push(blob);
      return 'blob:fake';
    });
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
      clicked.push({ href: this.href, download: this.download });
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    delete URL.createObjectURL;
    delete URL.revokeObjectURL;
  });

  it('writes BOM + headers + rows in the given key order, and escapes commas, quotes and newlines', async () => {
    exportCSV(
      [
        { orderNo: 'ORD-1', name: 'สมชาย, ใจดี', note: 'ขอ "ไม่ใส่ถุง"', total: 40 },
        { orderNo: 'ORD-2', name: 'Alice', note: 'line1\nline2', total: null },
      ],
      ['เลขออเดอร์', 'ชื่อ', 'หมายเหตุ', 'ยอดรวม'],
      ['orderNo', 'name', 'note', 'total'],
      'orders',
    );

    expect(blobs).toHaveLength(1);
    const text = await readBlob(blobs[0]);
    expect(text.charCodeAt(0)).toBe(0xfeff);
    expect(text.slice(1).split('\n')).toEqual([
      'เลขออเดอร์,ชื่อ,หมายเหตุ,ยอดรวม',
      'ORD-1,"สมชาย, ใจดี","ขอ ""ไม่ใส่ถุง""",40',
      'ORD-2,Alice,"line1',
      'line2",',
    ]);
    expect(blobs[0].type).toBe('text/csv;charset=utf-8;');
  });

  it('names the file with the date and cleans up the object URL', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-13T08:00:00Z'));

    exportCSV([{ a: 1 }], ['A'], ['a'], 'customers');

    expect(clicked).toEqual([{ href: 'blob:fake', download: 'customers_2026-10-13.csv' }]);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake');
  });
});
