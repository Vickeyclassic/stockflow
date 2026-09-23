import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { ErrorNotice, Field } from '../components/Forms';
import CsvExport from '../components/CsvExport';
import { historyFilters, movementColumns } from '../utils/csv';

export const movementTypes = ['STOCK_IN', 'STOCK_OUT', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT'];
export const displayType = value => value.replaceAll('_', ' ');
export const dateTime = value => new Date(value).toLocaleString();
export async function loadProducts(signal) {
  const products = []; let page = 0, totalPages;
  do { const { data } = await api.get('/api/products', { params: { page, size: 100 }, signal }); products.push(...data.content); totalPages = data.totalPages; page++; } while (page < totalPages);
  return products;
}
export const productOptions = products => [{ value: '', label: 'Select product' }, ...products.map(p => ({ value: p.id, label: `${p.sku} · ${p.name} (stock ${p.quantityInStock})` }))];
export function MovementTable({ rows }) {
  return <div className="table-wrap"><table><caption className="sr-only">Stock movements</caption><thead><tr><th>Date / time</th><th>Product</th><th>Type</th><th>Quantity</th><th>Previous</th><th>New stock</th><th>Reference</th><th>Reason / notes</th></tr></thead><tbody>
    {rows.length === 0 ? <tr><td colSpan={8}>No movements found.</td></tr> : rows.map(t => <tr key={t.id}><td>{dateTime(t.createdAt)}<small>#{t.id}</small></td><td>{t.productName}<small>{t.productSku}</small></td><td>{displayType(t.transactionType)}</td><td>{t.quantity}</td><td>{t.previousStock}</td><td>{t.newStock}</td><td>{displayType(t.referenceType)}<small>{t.referenceId || '—'}</small></td><td>{t.reason}<small>{t.notes}</small></td></tr>)}
  </tbody></table></div>;
}
export function Pager({ page, setPage, data, busy }) {
  return <div className="pagination"><span>Page {data.totalPages ? page + 1 : 0} of {data.totalPages} · {data.totalElements} records</span><button disabled={busy || page === 0} onClick={() => setPage(p => p - 1)}>Previous</button><button disabled={busy || page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>Next</button></div>;
}
const blank = { productId: '', transactionType: 'STOCK_IN', quantity: '', reason: '', notes: '', referenceId: '' };
const emptyFilters = { productId: '', transactionType: '', dateFrom: '', dateTo: '', referenceType: '', referenceId: '' };
const referenceTypes = ['MANUAL', 'OPENING_STOCK', 'LEGACY_ADJUSTMENT', 'PURCHASE_RECEIPT', 'SALES_ISSUE', 'SALES_ORDER', 'PURCHASE_ORDER'];
export default function InventoryPage() {
  const [products, setProducts] = useState([]), [dashboard, setDashboard] = useState(null), [revision, setRevision] = useState(0);
  const [error, setError] = useState(null), [loading, setLoading] = useState(true);
  const [values, setValues] = useState(blank), [busy, setBusy] = useState(false), [saveError, setSaveError] = useState(null), [result, setResult] = useState(null);
  const [filters, setFilters] = useState(emptyFilters), [applied, setApplied] = useState(emptyFilters), [page, setPage] = useState(0);
  const [history, setHistory] = useState({ content: [], totalPages: 0, totalElements: 0 });
  const [pageSize, setPageSize] = useState(20);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(null);
    const params = { page, size: pageSize, ...historyFilters(applied) };
    Promise.all([loadProducts(controller.signal), api.get('/api/inventory/dashboard', { signal: controller.signal }), api.get('/api/inventory/transactions', { params, signal: controller.signal })])
      .then(([p, d, h]) => { setProducts(p); setDashboard(d.data); setHistory(h.data); })
      .catch(e => { if (!controller.signal.aborted) setError(e); }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [revision, page, pageSize, applied]);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setSaveError(null); setResult(null);
    try {
      const { data } = await api.post('/api/inventory/movements', { ...values, productId: Number(values.productId), quantity: Number(values.quantity), reason: values.reason.trim(), notes: values.notes.trim() || null, referenceId: values.referenceId.trim() || null });
      setResult(data); setValues(blank); setRevision(r => r + 1);
    } catch (e) { setSaveError(e); } finally { setBusy(false); }
  }
  const field = (key, label, props = {}) => <Field label={label} value={values[key]} onChange={value => setValues(v => ({ ...v, [key]: value }))} {...props} />;
  return <>
    <div className="page-heading"><div><p className="eyebrow">AUDITABLE INVENTORY</p><h1>Inventory</h1><p>Receive, issue, and reconcile stock with a permanent movement trail.</p></div><button disabled={loading} onClick={() => setRevision(r => r + 1)}>Refresh inventory</button></div>
    <ErrorNotice error={error} />{loading && <p role="status">Loading inventory…</p>}
    {dashboard && <><div className="inventory-metrics"><div><strong>{dashboard.productCount}</strong><span>Products</span></div><div><strong>{dashboard.lowStockProductCount}</strong><span>Low-stock products</span></div><div><strong>{dashboard.recentStockIn}</strong><span>Units in · last 30 days</span></div><div><strong>{dashboard.recentStockOut}</strong><span>Units out · last 30 days</span></div></div><p className="muted footnote">Totals include adjustments and opening stock. Quantities across product units are summed without conversion. Low stock includes inactive products.</p></>}
    <section className="inventory-panel"><h2>Record stock movement</h2><p>Use a positive quantity. The movement type determines the direction. For supplier receipts or sales issues, use their document pages.</p>
      <form onSubmit={submit}><ErrorNotice error={saveError} /><fieldset disabled={busy || loading || !!error}><div className="form-grid">
        {field('productId', 'Movement product', { required: true, options: productOptions(products) })}
        {field('transactionType', 'Movement type', { options: movementTypes.map(value => ({ value, label: displayType(value) })) })}
        {field('quantity', 'Movement quantity', { required: true, type: 'number', min: 1, max: 2147483647, step: 1 })}
        {field('reason', 'Reason', { required: true, maxLength: 250 })}{field('referenceId', 'External reference', { maxLength: 100 })}{field('notes', 'Movement notes', { type: 'textarea', maxLength: 2000 })}
      </div><div className="form-actions"><button className="primary">{busy ? 'Recording…' : 'Record movement'}</button></div></fieldset></form>
      {result && <p className="notice success" role="status">Movement #{result.id} recorded for {result.productName}: {result.previousStock} → {result.newStock}.</p>}
    </section>
    {dashboard && <section className="inventory-panel"><h2>Recent movements</h2><MovementTable rows={dashboard.recentMovements} /></section>}
    <section className="inventory-panel" aria-labelledby="history-title"><h2 id="history-title">Transaction history</h2><p className="muted footnote">Filter by product, movement, UTC dates, or an exact reference. For orders, enter the numeric order ID shown in the order details.</p><form className="filters" onSubmit={e => { e.preventDefault(); setPage(0); setApplied({ ...filters }); }}>
      <Field label="History product" value={filters.productId} options={[{ value: '', label: 'All products' }, ...productOptions(products).slice(1)]} onChange={v => setFilters(f => ({ ...f, productId: v }))} />
      <Field label="History type" value={filters.transactionType} options={[{ value: '', label: 'All types' }, ...movementTypes.map(value => ({ value, label: displayType(value) }))]} onChange={v => setFilters(f => ({ ...f, transactionType: v }))} />
      <Field label="From date (UTC)" type="date" value={filters.dateFrom} onChange={v => setFilters(f => ({ ...f, dateFrom: v }))} /><Field label="To date (UTC)" type="date" min={filters.dateFrom || undefined} value={filters.dateTo} onChange={v => setFilters(f => ({ ...f, dateTo: v }))} />
      <Field label="Reference type" value={filters.referenceType} options={[{ value: '', label: 'All references' }, ...referenceTypes.map(value => ({ value, label: displayType(value) }))]} onChange={v => setFilters(f => ({ ...f, referenceType: v }))} />
      <Field label="Reference / order ID" maxLength={100} value={filters.referenceId} onChange={v => setFilters(f => ({ ...f, referenceId: v }))} />
      <div className="filter-actions"><button className="primary" disabled={loading}>Filter history</button><button type="button" onClick={() => { setFilters(emptyFilters); setApplied(emptyFilters); setPage(0); }}>Reset history</button></div>
    </form><div className="history-toolbar"><CsvExport endpoint="/api/inventory/transactions" filters={historyFilters(applied)} columns={movementColumns} filename="stockflow-inventory-transactions.csv" disabled={loading || !!error} />
      <Field label="Rows per page" value={pageSize} options={[10, 20, 50, 100].map(value => ({ value, label: String(value) }))} onChange={v => { setPageSize(Number(v)); setPage(0); }} /></div>
      {loading ? <p role="status">Loading history…</p> : !error && <MovementTable rows={history.content} />}<Pager page={page} setPage={setPage} data={history} busy={loading} /></section>
  </>;
}
