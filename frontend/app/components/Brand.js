import Link from 'next/link';
import Image from 'next/image';

export default function Brand({ href }) {
  const content = (
    <>
      <Image className="logo" src="/logo-round.png" alt="" width={36} height={36} />
      <span><b>Peak Pung</b><small>by Mom Hmee</small></span>
    </>
  );
  return href
    ? <Link className="brand" href={href}>{content}</Link>
    : <div className="brand">{content}</div>;
}
