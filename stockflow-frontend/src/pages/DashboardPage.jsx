import { useEffect, useState } from 'react';
import { api } from '../api/client';

const money = value => Number(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const integer = value => Number(value).toLocaleString();
const label = value => value.replaceAll('_', ' ').toLowerCase();

function Table({ headings, rows, empty = 'No activity in this period.' }) {
  return <div className="table-wrap"><table><thead><tr>{headings.map(h => <th key={h}>{h}</th>)}</tr></thead>
    <tbody>{rows.length ? rows : <tr><td colSpan={headings.length} className="muted">{empty}</td></tr>}</tbody></table></div>;
}
function Chart({ title, data, monetary = false }) {
  const max = Math.max(1, ...data.map(d => Number(d.value)));
  return <section className="dashboard-panel"><h2>{title}</h2><div className="chart-bars" aria-label={title}>
    {data.length ? data.map(d => <div className="chart-row" key={d.label}><span>{d.label}</span><div className="chart-track"><div style={{ width: `${Number(d.value) / max * 100}%` }} /></div><strong>{monetary ? money(d.value) : integer(d.value)}</strong></div>) : <p className="muted">No activity in this period.</p>}
  </div></section>;
}
function Orders({ title, orders, purchase }) {
  return <section className="dashboard-panel"><div className="section-toolbar"><h2>{title}</h2><a href={purchase ? '#purchase-orders' : '#sales-orders'}>View all</a></div>
    <Table headings={['Order / Date', purchase ? 'Supplier' : 'Customer', 'Status', 'Value']} rows={orders.map(o => <tr key={o.id}><td>{o.orderNumber}<small>{o.orderDate}</small></td><td>{o.partyName}</td><td><span className="badge">{label(o.status)}</span></td><td className="number">{money(o.totalAmount)}</td></tr>)} />
  </section>;
}
export default function DashboardPage() {
  const [dates, setDates] = useState({ dateFrom: '', dateTo: '' });
  const [filter, setFilter] = useState({});
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setResult(null);
    Promise.all([api.get('/api/dashboard', { signal: controller.signal }), api.get('/api/reports', { params: filter, signal: controller.signal })])
      .then(([metrics, report]) => { if (!controller.signal.aborted) setResult({ metrics: metrics.data, report: report.data }); })
      .catch(e => { if (!controller.signal.aborted) setError(e.response?.data?.message || 'Unable to load dashboard data. Please try again.'); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [filter, attempt]);
  const invalid = dates.dateFrom && dates.dateTo && dates.dateFrom > dates.dateTo;
  const m = result?.metrics, r = result?.report;
  return <>
    <div className="page-heading"><div><p className="eyebrow">BUSINESS OVERVIEW</p><h1>Dashboard &amp; Reports</h1><p>Inventory at a glance. Activity across your business.</p></div><button onClick={() => setAttempt(a => a + 1)} disabled={loading}>Refresh</button></div>
    <form className="filters report-filters" onSubmit={e => { e.preventDefault(); if (!invalid) setFilter(Object.fromEntries(Object.entries(dates).filter(([, v]) => v))); }}>
      <label className="field">From date<input type="date" min="0001-01-01" max="9999-12-31" value={dates.dateFrom} onChange={e => setDates({ ...dates, dateFrom: e.target.value })} /></label>
      <label className="field">To date<input type="date" min="0001-01-01" max="9999-12-31" value={dates.dateTo} onChange={e => setDates({ ...dates, dateTo: e.target.value })} /></label>
      <div className="report-actions"><button className="primary" disabled={loading || !!invalid}>Apply dates</button><button type="button" onClick={() => { setDates({ dateFrom: '', dateTo: '' }); setFilter({}); }}>All time</button></div>
      {invalid && <p className="text-danger" role="alert">From date must be on or before to date.</p>}
    </form>
    {loading && <p className="notice" role="status">Loading dashboard…</p>}
    {error && <div className="notice error" role="alert">{error} <button onClick={() => setAttempt(a => a + 1)}>Retry</button></div>}
    {result && <>
      <p className="muted footnote">Report period: {r.dateFrom || 'Beginning'} — {r.dateTo || 'Today and later'}. Order dates are inclusive; stock movements use UTC dates.</p>
      <div className="dashboard-metrics">{[
        ['Total products', integer(m.totalProducts), 'Current catalog'], ['Total customers', integer(m.totalCustomers), 'Current directory'],
        ['Total suppliers', integer(m.totalSuppliers), 'Current directory'], ['Low-stock products', integer(m.lowStockCount), 'At or below reorder level'],
        ['Inventory value', money(m.totalInventoryValue), 'Current quantity × cost price'], ['Purchase value', money(r.totalPurchaseValue), 'Received orders · selected period'],
        ['Sales value', money(r.totalSalesValue), 'Fulfilled orders · selected period'],
      ].map(([title, value, note]) => <article className="metric-card" key={title}><p>{title}</p><strong>{value}</strong><small>{note}</small></article>)}</div>
      <p className="muted footnote">Counts and inventory include all products, including inactive products. Purchase and sales values exclude drafts, pending and cancelled orders, and standalone receipts/issues. Top sellers rank fulfilled order quantities. Recent orders include all statuses.</p>
      <div className="dashboard-charts"><Chart title="Sales by day" data={r.sales.map(d => ({ label: d.date, value: d.value }))} monetary /><Chart title="Purchases by day" data={r.purchases.map(d => ({ label: d.date, value: d.value }))} monetary /><Chart title="Stock movement · units" data={r.stockMovements.map(d => ({ label: label(d.type), value: d.quantity }))} /></div>
      <div className="dashboard-grid"><Orders title="Recent purchases" orders={r.recentPurchaseOrders} purchase /><Orders title="Recent sales" orders={r.recentSalesOrders} /></div>
      <section className="dashboard-panel"><div className="section-toolbar"><h2>Recent stock movements</h2><a href="#inventory">View inventory</a></div><Table headings={['Product', 'Movement', 'Quantity', 'Stock after', 'Recorded (UTC)']} rows={r.recentStockMovements.map(t => <tr key={t.id}><td>{t.productName}<small>{t.productSku}</small></td><td><span className="badge">{label(t.transactionType)}</span></td><td className="number">{integer(t.quantity)}</td><td className="number">{integer(t.newStock)}</td><td>{new Date(t.createdAt).toISOString().replace('T', ' ').slice(0, 19)}</td></tr>)} /></section>
      <section className="dashboard-panel"><h2>Top-selling products</h2><Table headings={['Rank', 'Product', 'Units sold', 'Sales value']} rows={r.topSellingProducts.map((p, index) => <tr key={p.productId}><td>{index + 1}</td><td>{p.name}<small>{p.sku}</small></td><td className="number">{integer(p.quantity)}</td><td className="number">{money(p.salesValue)}</td></tr>)} empty="No fulfilled sales in this period." /></section>
    </>}
  </>;
}
