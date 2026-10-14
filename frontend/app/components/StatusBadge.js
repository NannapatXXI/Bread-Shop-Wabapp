const BADGE_CLASS = {
  PENDING:    'b-amber',
  CONFIRMED:  'b-blue',
  PROCESSING: 'b-violet',
  SHIPPED:    'b-violet',
  DELIVERED:  'b-green',
  CANCELLED:  'b-red',
  REFUNDED:   '',
};

export default function StatusBadge({ status, label }) {
  return <span className={`badge ${BADGE_CLASS[status] ?? ''}`}>{label ?? status}</span>;
}
