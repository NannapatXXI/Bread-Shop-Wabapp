'use client';

export default function ConfirmModal({ title, description, confirmLabel = 'ยืนยัน', cancelLabel = 'ยกเลิก', confirmColor = 'red', onConfirm, onCancel }) {
  const confirmStyles = {
    red:  'bg-red-600 hover:bg-red-700 text-white',
    navy: 'bg-[#0B1F33] hover:bg-[#162d47] text-white',
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-black/40 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md overflow-hidden animate-in fade-in zoom-in-95 duration-150">

        {/* Icon + Title */}
        <div className="px-6 pt-6 pb-4 flex flex-col items-center text-center">
          {confirmColor === 'red' && (
            <div className="w-12 h-12 rounded-full bg-red-50 flex items-center justify-center mb-4">
              <svg xmlns="http://www.w3.org/2000/svg" className="w-6 h-6 text-red-500" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
          )}
          <h3 className="text-base font-semibold text-gray-800">{title}</h3>
          {description && (
            <p className="text-sm text-gray-500 mt-1.5 leading-relaxed">{description}</p>
          )}
        </div>

        {/* Divider */}
        <div className="border-t border-gray-100 mx-6" />

        {/* Actions */}
        <div className="px-6 py-4 flex gap-3">
          <button
            onClick={onCancel}
            className="flex-1 py-2.5 rounded-xl border border-gray-200 text-sm font-medium text-gray-600 hover:bg-gray-50 transition"
          >
            {cancelLabel}
          </button>
          <button
            onClick={onConfirm}
            className={`flex-1 py-2.5 rounded-xl text-sm font-semibold transition ${confirmStyles[confirmColor] ?? confirmStyles.navy}`}
          >
            {confirmLabel}
          </button>
        </div>

      </div>
    </div>
  );
}
