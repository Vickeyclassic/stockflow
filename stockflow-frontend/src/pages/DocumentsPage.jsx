import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { ErrorNotice, Field, Modal } from '../components/Forms';
import { dateTime, loadProducts, Pager, productOptions } from './InventoryPage';

const newLine = () => ({ productId: '', quantity: 1, unitPrice: '' });
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
const emptyDocument = () => ({ number: '', date: today(), supplierId: '', notes: '', lines: [newLine()] });
export default function DocumentsPage({ purchase }) {
  const path = purchase ? '/api/purchase-receipts' : '/api/sales-issues';
  const title = purchase ? 'Purchase receipts' : 'Sales issues';
  const singular = purchase ? 'receipt' : 'issue';
  const [products, setProducts] = useState([]), [suppliers, setSuppliers] = useState([]), [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0), [loading, setLoading] = useState(true), [error, setError] = useState(null);
  const [values, setValues] = useState(emptyDocument), [busy, setBusy] = useState(false), [saveError, setSaveError] = useState(null), [detail, setDetail] = useState(null), [message, setMessage] = useState('');
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(null);
    Promise.all([loadProducts(controller.signal), api.get('/api/suppliers', { signal: controller.signal }), api.get(path, { params: { page, size: 20 }, signal: controller.signal })])
      .then(([p, s, d]) => { setProducts(p); setSuppliers(s.data); setData(d.data); }).catch(e => { if (!controller.signal.aborted) setError(e); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [page, revision, path]);
  function changeLine(index, key, value) {
    setValues(v => ({ ...v, lines: v.lines.map((line, i) => {
      if (i !== index) return line;
      if (key === 'productId') { const p = products.find(p => String(p.id) === value); return { ...line, productId: value, unitPrice: p ? String(purchase ? p.costPrice : p.sellingPrice) : '' }; }
      return { ...line, [key]: value };
    }) }));
  }
  async function submit(event) {
    event.preventDefault(); setBusy(true); setSaveError(null); setMessage('');
    const body = { number: values.number.trim(), date: values.date, notes: values.notes.trim() || null, lines: values.lines.map(l => ({ productId: Number(l.productId), quantity: Number(l.quantity), unitPrice: String(l.unitPrice) })) };
    if (purchase) body.supplierId = Number(values.supplierId);
    try { const { data } = await api.post(path, body); setValues(emptyDocument()); setDetail(data); setMessage(`${purchase ? 'Receipt' : 'Issue'} ${data.number} recorded. All lines applied.`); setPage(0); setRevision(r => r + 1); }
    catch (e) { setSaveError(e); } finally { setBusy(false); }
  }
  async function view(id) { try { const response = await api.get(`${path}/${id}`); setDetail(response.data); } catch (e) { setError(e); } }
  const field = (key, label, props = {}) => <Field label={label} value={values[key]} onChange={value => setValues(v => ({ ...v, [key]: value }))} {...props} />;
  return <>
    <div className="page-heading"><div><p className="eyebrow">{purchase ? 'GOODS RECEIVED' : 'GOODS ISSUED'}</p><h1>{title}</h1><p>{purchase ? 'Receive supplier goods into stock.' : 'Record goods leaving inventory.'} Every line is applied together or none are applied.</p></div><button disabled={loading} onClick={() => setRevision(r => r + 1)}>Refresh {title.toLowerCase()}</button></div>
    <ErrorNotice error={error} />{loading && <p role="status">Loading {title.toLowerCase()}…</p>}{message && <p role="status" className="notice success">{message}</p>}
    <section className="inventory-panel"><h2>Create {singular}</h2><p>Posted documents are permanent. Use a new, unique document number. Prices are recorded for reference and do not change catalog prices.</p>
      <form onSubmit={submit}><ErrorNotice error={saveError} /><fieldset disabled={busy || loading || !!error}><div className="form-grid">
        {field('number', purchase ? 'Receipt number' : 'Issue number', { required: true, maxLength: 100 })}{field('date', 'Document date', { required: true, type: 'date' })}
        {purchase && field('supplierId', 'Receipt supplier', { required: true, options: [{ value: '', label: 'Select supplier' }, ...suppliers.map(s => ({ value: s.id, label: s.name }))] })}
        {field('notes', 'Document notes', { type: 'textarea', maxLength: 2000 })}
      </div><h3>Product lines</h3>{values.lines.map((line, index) => <div className="document-line" key={index}>
        <Field label={`Line ${index + 1} product`} value={line.productId} required options={productOptions(products)} onChange={v => changeLine(index, 'productId', v)} />
        <Field label={`Line ${index + 1} quantity`} value={line.quantity} required type="number" min={1} max={2147483647} step={1} onChange={v => changeLine(index, 'quantity', v)} />
        <Field label={`Line ${index + 1} ${purchase ? 'unit cost' : 'unit selling price'}`} value={line.unitPrice} required type="number" min={0} max="9999999999.99" step=".01" onChange={v => changeLine(index, 'unitPrice', v)} />
        <button type="button" disabled={values.lines.length === 1} onClick={() => setValues(v => ({ ...v, lines: v.lines.filter((_, i) => i !== index) }))}>Remove line {index + 1}</button>
      </div>)}<div className="form-actions"><button type="button" disabled={values.lines.length >= 100} onClick={() => setValues(v => ({ ...v, lines: [...v.lines, newLine()] }))}>Add line</button><button className="primary">{busy ? 'Posting…' : `Post ${singular}`}</button></div></fieldset></form>
    </section>
    <section className="inventory-panel"><h2>{purchase ? 'Receipt' : 'Issue'} history</h2><div className="table-wrap"><table><thead><tr><th>Number</th><th>Date</th>{purchase && <th>Supplier</th>}<th>Lines</th><th>Recorded</th><th>Details</th></tr></thead><tbody>{data.content.length === 0 ? <tr><td colSpan={6}>No {title.toLowerCase()} found.</td></tr> : data.content.map(d => <tr key={d.id}><td>{d.number}</td><td>{d.date}</td>{purchase && <td>{d.supplierName}</td>}<td>{d.lines.length}</td><td>{dateTime(d.createdAt)}</td><td><button onClick={() => view(d.id)}>View {d.number}</button></td></tr>)}</tbody></table></div><Pager page={page} setPage={setPage} data={data} busy={loading} /></section>
    {detail && <Modal title={`${purchase ? 'Receipt' : 'Issue'} ${detail.number}`} onClose={() => setDetail(null)}><p>{detail.date} · Recorded {dateTime(detail.createdAt)}</p>{detail.supplierName && <p>Supplier: {detail.supplierName}</p>}<p>{detail.notes}</p><div className="table-wrap"><table><thead><tr><th>Product</th><th>Quantity</th><th>{purchase ? 'Unit cost' : 'Unit selling price'}</th></tr></thead><tbody>{detail.lines.map(l => <tr key={l.id}><td>{l.productName}<small>{l.productSku}</small></td><td>{l.quantity}</td><td>{Number(l.unitPrice).toFixed(2)}</td></tr>)}</tbody></table></div><p className="muted">Inventory history reference: {purchase ? 'PURCHASE_RECEIPT' : 'SALES_ISSUE'} #{detail.id}</p></Modal>}
  </>;
}
