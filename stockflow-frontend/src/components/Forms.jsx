import { useEffect, useRef, useState } from 'react';

export function ErrorNotice({ error }) {
  if (!error) return null;
  const data = error.response?.data;
  return <div className="notice error" role="alert"><strong>{data?.message || 'Could not reach StockFlow. Check the backend connection and try again.'}</strong>
    {Object.keys(data?.fieldErrors || {}).length > 0 && <ul>{Object.entries(data.fieldErrors).map(([name, message]) => <li key={name}>{name}: {message}</li>)}</ul>}
  </div>;
}

export function Modal({ title, children, onClose, busy = false }) {
  const ref = useRef(null);
  useEffect(() => { ref.current.showModal(); }, []);
  return <dialog ref={ref} aria-labelledby="dialog-title" onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}>
    <div className="dialog-heading"><h2 id="dialog-title">{title}</h2><button type="button" className="icon-button" aria-label="Close dialog" onClick={onClose} disabled={busy}>×</button></div>
    {children}
  </dialog>;
}

export function Field({ label, value, onChange, type = 'text', options, ...props }) {
  const input = options ? <select {...props} value={value ?? ''} onChange={e => onChange(e.target.value)}>{options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select>
    : type === 'textarea' ? <textarea {...props} value={value ?? ''} onChange={e => onChange(e.target.value)} rows={3} />
    : <input {...props} type={type} value={value ?? ''} onChange={e => onChange(e.target.value)} />;
  return <label className={type === 'textarea' ? 'field wide' : 'field'}><span>{label}{props.required && <span aria-hidden="true"> *</span>}</span>{input}</label>;
}

export function DeleteDialog({ name, onDelete, onClose }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  async function remove() {
    setBusy(true); setError(null);
    try { await onDelete(); onClose(); } catch (e) { setError(e); } finally { setBusy(false); }
  }
  return <Modal title="Delete record" onClose={onClose} busy={busy}>
    <p>Delete <strong>{name}</strong>? This cannot be undone.</p><ErrorNotice error={error} />
    <div className="form-actions"><button disabled={busy} onClick={onClose}>Cancel</button><button className="danger" disabled={busy} onClick={remove}>{busy ? 'Deleting…' : 'Confirm delete'}</button></div>
  </Modal>;
}
