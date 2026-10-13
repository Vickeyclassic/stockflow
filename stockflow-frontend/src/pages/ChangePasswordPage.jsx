import { useState } from 'react';
import { api } from '../api/client';
import { useAuth } from '../auth';
import { ErrorNotice, Field } from '../components/Forms';

export default function ChangePasswordPage() {
  const { logout } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [validation, setValidation] = useState('');
  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    setError(null); setValidation('');
    if (newPassword !== confirmation) { setValidation('New passwords do not match.'); return; }
    if (newPassword === currentPassword) { setValidation('Choose a different new password.'); return; }
    if (new TextEncoder().encode(newPassword).length > 72) { setValidation('New password must not exceed 72 UTF-8 bytes. Use fewer characters.'); return; }
    setBusy(true);
    try {
      await api.post('/api/auth/change-password', { currentPassword, newPassword });
      setCurrentPassword(''); setNewPassword(''); setConfirmation('');
      logout('Password changed. Please sign in with your new password.');
    } catch (error) { setError(error); setCurrentPassword(''); }
    finally { setBusy(false); }
  }
  return <section className="login-card" style={{ margin: '2rem auto', maxWidth: '32rem' }}>
    <h1>Change password</h1>
    <p className="muted">Use at least 12 characters. After saving, sign in again on your devices.</p>
    {validation && <p className="notice error" role="alert">{validation}</p>}
    <ErrorNotice error={error} />
    <form onSubmit={submit}><fieldset disabled={busy}>
      <Field label="Current password" type="password" value={currentPassword} onChange={setCurrentPassword} required maxLength={72} autoComplete="current-password" autoFocus />
      <Field label="New password" type="password" value={newPassword} onChange={setNewPassword} required minLength={12} maxLength={72} autoComplete="new-password" />
      <Field label="Confirm new password" type="password" value={confirmation} onChange={setConfirmation} required minLength={12} maxLength={72} autoComplete="new-password" />
      <div className="form-actions"><button type="button" onClick={() => { window.location.hash = 'dashboard'; }}>Cancel</button>
        <button className="primary" type="submit">{busy ? 'Saving…' : 'Change password'}</button></div>
    </fieldset></form>
  </section>;
}
