'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import Icon from './Icon';

const SHOW_ON = ['/login', '/register'];

export default function AuthHomeLink() {
  const pathname = usePathname();
  if (!SHOW_ON.includes(pathname)) return null;
  return (
    <Link className="btn btn-ghost sm" href="/" style={{ margin: '0 0 20px -12px' }}>
      <Icon name="left" size="sm" />กลับหน้าแรก
    </Link>
  );
}
