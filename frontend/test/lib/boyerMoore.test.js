import { describe, expect, it } from 'vitest';
import { boyerMooreContains } from '@/lib/boyerMoore';

describe('boyerMooreContains', () => {
  it('finds a pattern anywhere in the text', () => {
    expect(boyerMooreContains('Orange Cake', 'Cake')).toBe(true);
    expect(boyerMooreContains('Orange Cake', 'Orange')).toBe(true);
    expect(boyerMooreContains('Orange Cake', 'nge C')).toBe(true);
  });

  it('is case-insensitive', () => {
    expect(boyerMooreContains('Chiffon', 'CHIF')).toBe(true);
    expect(boyerMooreContains('DONUT', 'donut')).toBe(true);
  });

  it('works with Thai text', () => {
    expect(boyerMooreContains('ขนมปังเนยสด', 'เนย')).toBe(true);
    expect(boyerMooreContains('ขนมปังเนยสด', 'ชีส')).toBe(false);
  });

  it('returns false when not present or pattern is longer than text', () => {
    expect(boyerMooreContains('Bread', 'Breadstick')).toBe(false);
    expect(boyerMooreContains('Bread', 'xyz')).toBe(false);
  });

  it('treats an empty search as "match everything" and empty text as no match', () => {
    expect(boyerMooreContains('Bread', '')).toBe(true);
    expect(boyerMooreContains('Bread', undefined)).toBe(true);
    expect(boyerMooreContains('', 'a')).toBe(false);
    expect(boyerMooreContains(null, 'a')).toBe(false);
  });

  it('agrees with String.includes on many random inputs (repetitive alphabets stress the shift tables)', () => {
    let seed = 42;
    const rand = () => (seed = (seed * 1103515245 + 12345) % 2 ** 31) / 2 ** 31;
    const alphabet = 'aab';
    const word = (len) => Array.from({ length: len }, () => alphabet[Math.floor(rand() * alphabet.length)]).join('');

    for (let i = 0; i < 3000; i++) {
      const text = word(Math.floor(rand() * 30));
      const pattern = word(1 + Math.floor(rand() * 6));
      expect(boyerMooreContains(text, pattern), `text="${text}" pattern="${pattern}"`).toBe(text.includes(pattern));
    }
  });
});
