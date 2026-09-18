import { useState } from 'react';
import { useAuth } from '../auth';
import { ErrorNotice, Field } from '../components/Forms';
export default function LoginPage() {
  const { login, message } = useAuth();
  const [username, setUsername] = useState(''); const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false); const [error, setError] = useState(null);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(null);
    try { await login(username.trim(), password); }
    catch (error) { setError(error); setPassword(''); }
    finally { setBusy(false); }
  }
  return <main className="login-screen"><section className="login-card">
    <a className="brand" href="#login"><span className="brand-icon" aria-hidden="true">S</span>StockFlow</a>
    <p className="eyebrow">INVENTORY &amp; ORDER MANAGEMENT</p><h1>Welcome back</h1>
    <p className="muted">Sign in to keep your operations moving.</p>
    {message && <p className="notice" role="status">{message}</p>}<ErrorNotice error={error} />
    <form onSubmit={submit}><fieldset disabled={busy}>
      <Field label="Username" value={username} onChange={setUsername} required maxLength={100} autoComplete="username" autoFocus />
      <Field label="Password" type="password" value={password} onChange={setPassword} required maxLength={72} autoComplete="current-password" />
      <button className="primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
    </fieldset></form><p className="muted footnote">Need access? Contact your StockFlow administrator.</p>
  </section></main>;
}
