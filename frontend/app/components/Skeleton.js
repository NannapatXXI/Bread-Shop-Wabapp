// Reusable skeleton loaders for loading states

export function SkeletonRow({ cols = 5 }) {
  return (
    <tr className="animate-pulse border-b border-gray-50">
      {Array.from({ length: cols }).map((_, i) => (
        <td key={i} className="px-4 py-3">
          <div className="h-4 bg-gray-200 rounded w-3/4" />
        </td>
      ))}
    </tr>
  );
}

export function SkeletonRows({ rows = 6, cols = 5 }) {
  return Array.from({ length: rows }).map((_, i) => (
    <SkeletonRow key={i} cols={cols} />
  ));
}

export function SkeletonCard() {
  return (
    <div className="animate-pulse bg-white rounded-xl overflow-hidden border border-gray-100">
      <div className="h-28 bg-gray-200" />
      <div className="p-3 space-y-2">
        <div className="h-3 bg-gray-200 rounded w-3/4" />
        <div className="flex justify-between items-center pt-1">
          <div className="h-3 bg-gray-200 rounded w-1/3" />
          <div className="h-6 w-12 bg-gray-200 rounded" />
        </div>
      </div>
    </div>
  );
}

export function SkeletonOrderItem() {
  return (
    <div className="animate-pulse bg-white rounded-xl border border-gray-100 p-4">
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-lg bg-gray-200 flex-shrink-0" />
        <div className="flex-1 space-y-2">
          <div className="h-3 bg-gray-200 rounded w-1/3" />
          <div className="h-3 bg-gray-200 rounded w-1/2" />
        </div>
        <div className="h-5 w-20 bg-gray-200 rounded-full" />
      </div>
    </div>
  );
}
