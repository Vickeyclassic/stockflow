import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { DeleteDialog, ErrorNotice, Field, Modal } from '../components/Forms';

const supplierFields = [
  { name: 'name', label: 'Supplier name', required: true, maxLength: 150 },
  { name: 'contactPerson', label: 'Contact person', maxLength: 100 },
  { name: 'email', label: 'Email', type: 'email', maxLength: 254 },
  { name: 'phone', label: 'Phone', maxLength: 30 },
  { name: 'address', label: 'Address', type: 'textarea', maxLength: 500 },
];
const customerFields = supplierFields.filter(f => f.name !== 'contactPerson').map(f => f.name === 'name' ? { ...f, label: 'Customer name' } : f);
const categoryFields = [
  { name: 'name', label: 'Category name', required: true, maxLength: 100 },
  { name: 'description', label: 'Description', type: 'textarea', maxLength: 1000 },
];

function CatalogForm({ kind, item, onSaved, onClose }) {
  const fields = kind === 'categories' ? categoryFields : kind === 'customers' ? customerFields : supplierFields;
  const singular = kind === 'categories' ? 'category' : kind === 'customers' ? 'customer' : 'supplier';
  const [values, setValues] = useState(() => Object.fromEntries(fields.map(f => [f.name, item?.[f.name] || ''])));
  const [active, setActive] = useState(item?.active ?? true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(null);
    const body = Object.fromEntries(Object.entries(values).map(([key, value]) => [key, value.trim() || null]));
    if (kind === 'customers') body.active = active;
    try {
      if (item?.id) await api.put(`/api/${kind}/${item.id}`, body); else await api.post(`/api/${kind}`, body);
      onSaved(); onClose();
    } catch (e) { setError(e); } finally { setBusy(false); }
  }
  return <Modal title={`${item?.id ? 'Edit' : 'Add'} ${singular}`} onClose={onClose} busy={busy}>
    <form onSubmit={submit}><ErrorNotice error={error} /><fieldset disabled={busy}><div className="form-grid">
      {fields.map(({ name, ...field }) => <Field key={name} {...field} value={values[name]} onChange={value => setValues(v => ({ ...v, [name]: value }))} />)}
    </div>{kind === 'customers' && <label className="checkbox"><input type="checkbox" checked={active} onChange={e => setActive(e.target.checked)} />Active customer</label>}</fieldset><div className="form-actions"><button type="button" onClick={onClose} disabled={busy}>Cancel</button><button className="primary" disabled={busy}>{busy ? 'Saving…' : `Save ${singular}`}</button></div></form>
  </Modal>;
}

export default function CatalogPage({ kind }) {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [revision, setRevision] = useState(0);
  const [message, setMessage] = useState('');
  const title = kind === 'categories' ? 'Categories' : kind === 'customers' ? 'Customers' : 'Suppliers';
  const singular = kind === 'categories' ? 'category' : kind === 'customers' ? 'customer' : 'supplier';
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(null);
    api.get(`/api/${kind}`, { signal: controller.signal }).then(({ data }) => setRows(data))
      .catch(e => { if (!controller.signal.aborted) setError(e); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [kind, revision]);
  const reload = () => setRevision(r => r + 1);
  return <>
    <div className="page-heading"><div><p className="eyebrow">INVENTORY DIRECTORY</p><h1>{title}</h1><p>{kind === 'categories' ? 'Organize products into clear, reusable categories.' : 'Keep contact details together.'}</p></div><button className="primary" onClick={() => setEditing({})}>+ Add {singular}</button></div>
    <div className="section-toolbar"><span>{rows.length} records</span><button onClick={reload} disabled={loading}>Refresh</button></div>
    {message && <p className="notice success" role="status">{message}</p>}<ErrorNotice error={error} />
    <div className="table-wrap"><table><caption className="sr-only">{title}</caption><thead><tr><th>Name</th>{kind === 'categories' ? <th>Description</th> : <><th>{kind === 'customers' ? 'Status' : 'Contact'}</th><th>Email</th><th>Phone</th><th>Address</th></>}<th>Actions</th></tr></thead><tbody>
      {loading ? <tr><td colSpan={6}>Loading…</td></tr> : error ? <tr><td colSpan={6}>Records could not be loaded. Try Refresh.</td></tr> : rows.length === 0 ? <tr><td colSpan={6}>No {kind} yet. Add your first {singular} to get started.</td></tr> : rows.map(row => <tr key={row.id}><td><strong>{row.name}</strong></td>{kind === 'categories' ? <td>{row.description || '—'}</td> : <><td>{kind === 'customers' ? (row.active ? 'Active' : 'Inactive') : row.contactPerson || '—'}</td><td>{row.email || '—'}</td><td>{row.phone || '—'}</td><td>{row.address || '—'}</td></>}<td><div className="row-actions"><button onClick={() => setEditing(row)} aria-label={`Edit ${row.name}`}>Edit</button><button className="text-danger" onClick={() => setDeleting(row)} aria-label={`Delete ${row.name}`}>Delete</button></div></td></tr>)}
    </tbody></table></div>
    <p className="muted footnote">Referenced records cannot be deleted. Customers with orders can be marked inactive.</p>
    {editing && <CatalogForm kind={kind} item={editing} onClose={() => setEditing(null)} onSaved={() => { setMessage('Record saved.'); reload(); }} />}
    {deleting && <DeleteDialog name={deleting.name} onClose={() => setDeleting(null)} onDelete={async () => { await api.delete(`/api/${kind}/${deleting.id}`); setMessage('Record deleted.'); reload(); }} />}
  </>;
}
