export default function AuthLayout({ children }) {
  return (
    <div style={{
      minHeight: '100vh',
      background: '#0B1F33',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '1.5rem 1rem',
      overflowX: 'hidden',
    }}>
      <div style={{ width: '100%', maxWidth: '480px' }}>
        {children}
      </div>
    </div>
  );
}
