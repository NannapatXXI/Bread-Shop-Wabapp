// Spinner — ใช้แทนข้อความ "กำลัง..." ในปุ่มที่กำลัง loading
export default function Spinner({ size = 16, color = 'currentColor' }) {
  return (
    <svg
      style={{ width: size, height: size, flexShrink: 0 }}
      viewBox="0 0 24 24" fill="none"
      className="animate-spin"
    >
      <circle cx="12" cy="12" r="10" stroke={color} strokeWidth="4" className="opacity-25" />
      <path fill={color} className="opacity-75"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
    </svg>
  );
}
