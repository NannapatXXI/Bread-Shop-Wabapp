import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

afterEach(() => {
  cleanup();
  localStorage.clear();
});

// next/link และ next/image ต้องมี runtime ของ Next — ในเทสใช้ <a>/<img> ธรรมดาแทน
vi.mock('next/link', () => ({
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  default: ({ href, children, prefetch: _p, ...rest }) => <a href={typeof href === 'string' ? href : href?.pathname} {...rest}>{children}</a>,
}));

vi.mock('next/image', () => ({
  // eslint-disable-next-line @next/next/no-img-element, jsx-a11y/alt-text, @typescript-eslint/no-unused-vars
  default: ({ priority: _p, fill: _f, ...rest }) => <img {...rest} />,
}));
