import AuthArt from '@/app/components/AuthArt';
import Brand from '@/app/components/Brand';
import AuthHomeLink from '@/app/components/AuthHomeLink';

export default function AuthLayout({ children }) {
  return (
    <div className="auth">
      <AuthArt />
      <div className="auth-mobile-brand">
        <svg viewBox="0 0 220 220" aria-hidden="true">
          <circle cx="130" cy="90" r="80" fill="#1b2b47" />
          <circle cx="60" cy="160" r="36" fill="#3466f0" />
        </svg>
        <Brand href="/" />
        <h2>ขนมอบใหม่ทุกวัน<br />จากครัวของแม่หมี</h2>
      </div>
      <div className="auth-form">
        <div className="auth-box"><AuthHomeLink />{children}</div>
      </div>
    </div>
  );
}
