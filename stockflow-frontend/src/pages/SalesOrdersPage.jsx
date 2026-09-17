import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { ErrorNotice, Field, Modal } from '../components/Forms';
import { dateTime, loadProducts, Pager, productOptions } from './InventoryPage';

const statuses = ['DRAFT', 'CONFIRMED', 'FULFILLED', 'CANCELLED'];
const money = value => Number(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const newItem = () => ({ productId: '', quantity: 1, unitPrice: '' });
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
const blank = () => ({ orderNumber: '', customerId: '', orderDate: today(), notes: '', items: [newItem()] });
const emptyFilters = { orderNumber: '', customerId: '', status: '', dateFrom: '', dateTo: '' };

function OrderForm({ order, customers, products, onClose, onSaved }) {
  const [values, setValues] = useState(() => order ? { ...order, customerId: order.customer.id } : blank());
  const [busy, setBusy] = useState(false), [error, setError] = useState(null);
  const field = (key, label, props = {}) => <Field label={label} value={values[key]} onChange={v => setValues(s => ({ ...s, [key]: v }))} {...props} />;
  function changeItem(index, key, value) {
    setValues(v => ({ ...v, items: v.items.map((item, i) => {
      if (i !== index) return item;
      if (key === 'productId') return { ...item, productId: value, unitPrice: products.find(p => String(p.id) === value)?.sellingPrice ?? '' };
      return { ...item, [key]: value };
    }) }));
  }
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(null);
    const body = { orderNumber: values.orderNumber.trim(), customerId: Number(values.customerId), orderDate: values.orderDate, notes: values.notes?.trim() || null,
      items: values.items.map(i => ({ productId: Number(i.productId), quantity: Number(i.quantity), unitPrice: String(i.unitPrice) })) };
    try { const { data } = order ? await api.put(`/api/sales-orders/${order.id}`, body) : await api.post('/api/sales-orders', body); onSaved(data); }
    catch (e) { setError(e); } finally { setBusy(false); }
  }
  return <Modal title={order ? 'Edit draft order' : 'Create sales order'} onClose={onClose} busy={busy}>
    <form onSubmit={submit}><ErrorNotice error={error} /><fieldset disabled={busy}><div className="form-grid">
      {field('orderNumber', 'Order number', { required: true, maxLength: 100 })}
      {field('orderDate', 'Order date', { required: true, type: 'date' })}
      {field('customerId', 'Customer', { required: true, options: [{ value: '', label: 'Select customer' }, ...customers.filter(c => c.active || c.id === Number(values.customerId)).map(c => ({ value: c.id, label: c.name + (c.active ? '' : ' (inactive)') }))] })}
      {field('notes', 'Notes', { type: 'textarea', maxLength: 2000 })}
    </div><h3>Order items</h3>{values.items.map((item, index) => <div className="document-line" key={index}>
      <Field label={`Item ${index + 1} product`} required value={item.productId} options={productOptions(products.filter(p => p.active || p.id === Number(item.productId)))} onChange={v => changeItem(index, 'productId', v)} />
      <Field label="Quantity" required type="number" min={1} max={2147483647} step={1} value={item.quantity} onChange={v => changeItem(index, 'quantity', v)} />
      <Field label="Unit price" required type="number" min={0} max="9999999999.99" step=".01" value={item.unitPrice} onChange={v => changeItem(index, 'unitPrice', v)} />
      <button type="button" disabled={values.items.length === 1} onClick={() => setValues(v => ({ ...v, items: v.items.filter((_, i) => i !== index) }))}>Remove</button>
    </div>)}
    <p><strong>Estimated total: {money(values.items.reduce((sum, i) => sum + Number(i.quantity) * Number(i.unitPrice), 0))}</strong></p>
    <p className="muted">The saved total is calculated by StockFlow. Stock is deducted only when a confirmed order is fulfilled.</p>
    <div className="form-actions"><button type="button" disabled={values.items.length >= 100} onClick={() => setValues(v => ({ ...v, items: [...v.items, newItem()] }))}>Add item</button><button className="primary">{busy ? 'Saving…' : 'Save draft'}</button></div>
    </fieldset></form>
  </Modal>;
}

export default function SalesOrdersPage() {
  const [data, setData] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [customers, setCustomers] = useState([]), [products, setProducts] = useState([]);
  const [filters, setFilters] = useState(emptyFilters), [applied, setApplied] = useState(emptyFilters);
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true), [error, setError] = useState(null), [message, setMessage] = useState('');
  const [editing, setEditing] = useState(null), [detail, setDetail] = useState(null);
  const [busy, setBusy] = useState(false), [statusError, setStatusError] = useState(null);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(null);
    const params = { page, size: 20, ...Object.fromEntries(Object.entries(applied).filter(([, v]) => v !== '')) };
    Promise.all([api.get('/api/sales-orders', { params, signal: controller.signal }), api.get('/api/customers', { signal: controller.signal }), loadProducts(controller.signal)])
      .then(([o, c, p]) => { setData(o.data); setCustomers(c.data); setProducts(p); })
      .catch(e => { if (!controller.signal.aborted) setError(e); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [page, revision, applied]);
  async function view(id) {
    setError(null);
    try { const { data } = await api.get(`/api/sales-orders/${id}`); setStatusError(null); setDetail(data); } catch (e) { setError(e); }
  }
  async function changeStatus(status) {
    setBusy(true); setStatusError(null);
    try { const { data } = await api.patch(`/api/sales-orders/${detail.id}/status`, { status }); setDetail(data); setRevision(r => r + 1); setMessage(`Order ${data.orderNumber}: ${status}.`); }
    catch (e) { setStatusError(e); } finally { setBusy(false); }
  }
  const filter = (key, label, props = {}) => <Field label={label} value={filters[key]} onChange={v => setFilters(f => ({ ...f, [key]: v }))} {...props} />;
  return <>
    <div className="page-heading"><div><p className="eyebrow">CUSTOMER ORDERS</p><h1>Sales orders</h1><p>Create drafts, confirm orders, and fulfill from available stock.</p></div><button className="primary" disabled={loading || !!error} onClick={() => setEditing({})}>+ Create order</button></div>
    <form className="filters" onSubmit={e => { e.preventDefault(); setApplied({ ...filters }); setPage(0); }}>
      {filter('orderNumber', 'Order number')}
      {filter('customerId', 'Customer', { options: [{ value: '', label: 'All customers' }, ...customers.map(c => ({ value: c.id, label: c.name }))] })}
      {filter('status', 'Status', { options: [{ value: '', label: 'All statuses' }, ...statuses.map(s => ({ value: s, label: s }))] })}
      {filter('dateFrom', 'From date', { type: 'date' })}{filter('dateTo', 'To date', { type: 'date' })}
      <div className="filter-actions"><button className="primary">Apply filters</button><button type="button" onClick={() => { setFilters(emptyFilters); setApplied(emptyFilters); setPage(0); }}>Reset</button><button type="button" disabled={loading} onClick={() => setRevision(r => r + 1)}>Refresh</button></div>
    </form>
    <ErrorNotice error={error} />{message && <p className="notice success" role="status">{message}</p>}
    <div className="table-wrap"><table><caption className="sr-only">Sales orders</caption><thead><tr><th>Order number</th><th>Customer</th><th>Date</th><th>Status</th><th className="number">Total amount</th><th>Actions</th></tr></thead><tbody>
      {loading ? <tr><td colSpan={6}>Loading…</td></tr> : error ? <tr><td colSpan={6}>Orders could not be loaded.</td></tr> : !data.content.length ? <tr><td colSpan={6}>No orders found.</td></tr> : data.content.map(o => <tr key={o.id}><td><strong>{o.orderNumber}</strong></td><td>{o.customer.name}</td><td>{o.orderDate}</td><td><span className="badge">{o.status}</span></td><td className="number"><strong>{money(o.totalAmount)}</strong></td><td><button onClick={() => view(o.id)}>View</button></td></tr>)}
    </tbody></table></div><Pager page={page} setPage={setPage} data={data} busy={loading} />
    {editing && <OrderForm order={editing.id ? editing : null} customers={customers} products={products} onClose={() => setEditing(null)} onSaved={o => { setEditing(null); setDetail(o); setStatusError(null); setRevision(r => r + 1); setMessage('Draft saved.'); }} />}
    {detail && <Modal title={`Order ${detail.orderNumber}`} busy={busy} onClose={() => setDetail(null)}>
      <p>{detail.customer.name} · {detail.orderDate} · <strong>{detail.status}</strong></p><p className="muted">Created {dateTime(detail.createdAt)}</p><p>{detail.notes}</p>
      <div className="table-wrap"><table><thead><tr><th>Product</th><th>Quantity</th><th>Unit price</th><th>Line total</th></tr></thead><tbody>{detail.items.map((i, index) => <tr key={index}><td>{i.productName}<small>{i.sku}</small></td><td>{i.quantity}</td><td>{money(i.unitPrice)}</td><td>{money(i.lineTotal)}</td></tr>)}</tbody></table></div>
      <h3>Total amount: {money(detail.totalAmount)}</h3><ErrorNotice error={statusError} />
      {detail.status === 'CONFIRMED' && <p>Fulfillment deducts all items from stock and is final.</p>}
      {detail.status === 'FULFILLED' && <p className="muted">Inventory history reference: SALES_ORDER #{detail.id}</p>}
      <div className="form-actions">
        {detail.status === 'DRAFT' && <><button disabled={busy} onClick={() => { setEditing(detail); setDetail(null); }}>Edit draft</button><button className="primary" disabled={busy} onClick={() => changeStatus('CONFIRMED')}>Confirm order</button></>}
        {detail.status === 'CONFIRMED' && <button className="primary" disabled={busy} onClick={() => changeStatus('FULFILLED')}>{busy ? 'Fulfilling…' : 'Fulfill order'}</button>}
        {['DRAFT', 'CONFIRMED'].includes(detail.status) && <button disabled={busy} onClick={() => changeStatus('CANCELLED')}>Cancel order</button>}
      </div>
    </Modal>}
  </>;
}
