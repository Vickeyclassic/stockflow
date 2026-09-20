import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { useAuth } from '../auth';
import { ErrorNotice, Field, Modal } from '../components/Forms';

function CreateStaff({ onClose, onSaved }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(null);
    try {
      await api.post('/api/users', { username, password, role: 'STAFF' });
      setPassword(''); onSaved(); onClose();
    } catch (e) { setError(e); } finally { setBusy(false); }
  }
  return <Modal title="Create staff user" onClose={onClose} busy={busy}>
    <form onSubmit={submit}><ErrorNotice error={error} /><fieldset disabled={busy}>
      <Field label="Username" value={username} onChange={setUsername} required minLength={3} maxLength={100} pattern="[A-Za-z0-9._\-]{3,100}" autoComplete="off" />
      <Field label="Password" type="password" value={password} onChange={setPassword} required minLength={12} maxLength={72} autoComplete="new-password" />
      <p className="muted">12 or more characters, at most 72 UTF-8 bytes. Share credentials securely.</p>
    </fieldset><div className="form-actions"><button type="button" onClick={onClose} disabled={busy}>Cancel</button><button className="primary" disabled={busy}>{busy ? 'Creating…' : 'Create STAFF user'}</button></div></form>
  </Modal>;
}

export default function UsersPage() {
  const { user } = useAuth();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [creating, setCreating] = useState(false);
  const [revision, setRevision] = useState(0);
  const [message, setMessage] = useState('');
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(null);
    api.get('/api/users', { signal: controller.signal }).then(({ data }) => setRows(data))
      .catch(e => { if (!controller.signal.aborted) setError(e); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [revision]);
  async function toggle(row) {
    setBusy(true); setError(null); setMessage('');
    try {
      const { data } = await api.patch(`/api/users/${row.id}/active`, { active: !row.active });
      setRows(previous => previous.map(item => item.id === data.id ? data : item));
      setMessage(`${data.username} ${data.active ? 'activated' : 'deactivated'}.`);
    } catch (e) { setError(e); } finally { setBusy(false); }
  }
  return <>
    <div className="page-heading"><div><p className="eyebrow">ADMINISTRATION</p><h1>Users</h1><p>Manage access to StockFlow.</p></div><button className="primary" onClick={() => setCreating(true)}>+ Create STAFF user</button></div>
    <div className="section-toolbar"><span>{rows.length} users</span><button disabled={loading || busy} onClick={() => setRevision(n => n + 1)}>Refresh</button></div>
    <ErrorNotice error={error} />{message && <p className="notice success" role="status">{message}</p>}
    <div className="table-wrap"><table><caption className="sr-only">Users</caption><thead><tr><th>Username</th><th>Role</th><th>Status</th><th>Actions</th></tr></thead><tbody>
      {loading ? <tr><td colSpan={4}>Loading…</td></tr> : rows.length === 0 ? <tr><td colSpan={4}>No users loaded.</td></tr> : rows.map(row => <tr key={row.id}><td>{row.username}</td><td>{row.role}</td><td>{row.active ? 'Active' : 'Inactive'}</td><td><button disabled={busy || row.username === user.username} onClick={() => toggle(row)} aria-label={`${row.active ? 'Deactivate' : 'Activate'} ${row.username}`}>{row.active ? 'Deactivate' : 'Activate'}</button></td></tr>)}
    </tbody></table></div><p className="muted footnote">Deactivation blocks login and existing sessions. You cannot deactivate your own account.</p>
    {creating && <CreateStaff onClose={() => setCreating(false)} onSaved={() => { setMessage('STAFF user created.'); setRevision(n => n + 1); }} />}
  </>;
}
