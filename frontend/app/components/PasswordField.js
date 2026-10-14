'use client';
import { useState } from 'react';
import Icon from './Icon';

export default function PasswordField({ id, label, value, onChange, placeholder = '••••••••', disabled }) {
  const [show, setShow] = useState(false);
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="control">
        <Icon name="lock" size="sm" />
        <input id={id} type={show ? 'text' : 'password'} placeholder={placeholder}
          value={value} onChange={onChange} disabled={disabled} />
        <button type="button" className="icon-btn sm" onClick={() => setShow(s => !s)}
          aria-label={show ? 'ซ่อนรหัสผ่าน' : 'แสดงรหัสผ่าน'}>
          <Icon name={show ? 'eyeOff' : 'eye'} size="sm" />
        </button>
      </div>
    </div>
  );
}

export function PasswordRules({ password }) {
  const longEnough = password.length >= 8;
  const hasDigitOrSymbol = /\d/.test(password) || /[^a-zA-Z0-9]/.test(password);
  return (
    <div className="rules">
      <span className={longEnough ? 'ok' : undefined}>{longEnough ? '✓' : '•'} อย่างน้อย 8 ตัวอักษร</span>
      <span className={hasDigitOrSymbol ? 'ok' : undefined}>{hasDigitOrSymbol ? '✓' : '•'} มีตัวเลขหรือสัญลักษณ์อย่างน้อย 1 ตัว</span>
    </div>
  );
}
