'use client';

import Icon from './Icon';

export default function ConfirmModal({ title, description, confirmLabel = 'ยืนยัน', cancelLabel = 'ยกเลิก', confirmColor = 'red', onConfirm, onCancel }) {
  return (
    <div className="modal-scrim">
      <div className="modal" role="alertdialog" aria-modal="true" aria-label={title}>
        <div className="modal-body" style={{ textAlign: 'center', paddingTop: 28 }}>
          {confirmColor === 'red' && (
            <div className="empty" style={{ border: 0, padding: 0, background: 'none' }}>
              <div className="ic" style={{ background: 'var(--red-soft)', color: 'var(--red)' }}><Icon name="alert" /></div>
            </div>
          )}
          <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600 }}>{title}</h2>
          {description && <p className="muted" style={{ margin: '6px 0 0', fontSize: 14 }}>{description}</p>}
        </div>
        <div className="modal-foot">
          <button className="btn btn-outline" style={{ flex: 1 }} onClick={onCancel}>{cancelLabel}</button>
          <button className={`btn ${confirmColor === 'red' ? 'btn-danger' : 'btn-dark'}`} style={{ flex: 1 }} onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  );
}
