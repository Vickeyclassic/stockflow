import { useAuth } from '../auth';
import { useEffect, useState } from 'react';
import { api } from '../api/client';
import { DeleteDialog, ErrorNotice, Field, Modal } from '../components/Forms';
import CsvExport from '../components/CsvExport';
import { productColumns } from '../utils/csv';

const emptyFilters = { name: '', sku: '', categoryId: '', supplierId: '', active: '', lowStock: '' };
const money = value => new Intl.NumberFormat(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);

function ProductForm({ item, categories, suppliers, onSaved, onClose }) {
  const [values, setValues] = useState(() => ({ sku: '', name: '', description: '', categoryId: '', supplierId: '', costPrice: '', sellingPrice: '', quantityInStock: 0, reorderLevel: 0, unit: 'piece', active: true, ...item }));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const field = (name, label, props = {}) => <Field label={label} value={values[name]} onChange={value => setValues(v => ({ ...v, [name]: value }))} {...props} />;
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(null);
    // Currency strings preserve entered decimals; only whole-unit quantities use Number.
    const body = { sku: values.sku.trim(), name: values.name.trim(), description: values.description?.trim() || null,
      categoryId: Number(values.categoryId), supplierId: values.supplierId ? Number(values.supplierId) : null,
      costPrice: String(values.costPrice), sellingPrice: String(values.sellingPrice), reorderLevel: Number(values.reorderLevel), unit: values.unit.trim(), active: values.active };
    if (!item?.id) body.quantityInStock = Number(values.quantityInStock);
    try {
      if (item?.id) await api.put(`/api/products/${item.id}`, body); else await api.post('/api/products', body);
      onSaved(); onClose();
    } catch (e) { setError(e); } finally { setBusy(false); }
  }
  return <Modal title={item?.id ? 'Edit product' : 'Add product'} onClose={onClose} busy={busy}>
    <form onSubmit={submit}><ErrorNotice error={error} /><fieldset disabled={busy}><div className="form-grid">
      {field('name', 'Product name', { required: true, maxLength: 150 })}
      {field('sku', 'SKU', { required: true, maxLength: 64 })}
      {field('categoryId', 'Category', { required: true, options: [{ value: '', label: 'Select category' }, ...categories.map(c => ({ value: c.id, label: c.name }))] })}
      {field('supplierId', 'Supplier', { options: [{ value: '', label: 'No supplier assigned' }, ...suppliers.map(s => ({ value: s.id, label: s.name }))] })}
      {field('costPrice', 'Cost price', { required: true, type: 'number', min: 0, max: '9999999999.99', step: '.01' })}
      {field('sellingPrice', 'Selling price', { required: true, type: 'number', min: 0, max: '9999999999.99', step: '.01' })}
      {!item?.id && field('quantityInStock', 'Initial stock', { required: true, type: 'number', min: 0, max: 2147483647, step: 1 })}
      {field('reorderLevel', 'Reorder level', { required: true, type: 'number', min: 0, max: 2147483647, step: 1 })}
      {field('unit', 'Unit', { required: true, maxLength: 30 })}
      {field('description', 'Description', { type: 'textarea', maxLength: 2000 })}
      <label className="checkbox"><input type="checkbox" checked={values.active} onChange={e => setValues(v => ({ ...v, active: e.target.checked }))} />Active product</label>
    </div></fieldset>
      {item?.id && <p className="muted footnote">Current stock: {item.quantityInStock}. Use Adjust stock to change the quantity.</p>}
      <div className="form-actions"><button type="button" disabled={busy} onClick={onClose}>Cancel</button><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Save product'}</button></div>
    </form>
  </Modal>;
}

function StockForm({ item, onSaved, onClose }) {
  const [adjustment, setAdjustment] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState(null);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(null);
    try { await api.patch(`/api/products/${item.id}/stock`, { adjustment: Number(adjustment) }); onSaved(); onClose(); }
    catch (e) { setError(e); } finally { setBusy(false); }
  }
  return <Modal title="Adjust stock" onClose={onClose} busy={busy}><p><strong>{item.name}</strong> · Current stock: {item.quantityInStock} {item.unit}</p>
    <p className="muted">Enter a positive quantity to add stock or a negative quantity to remove it. This adjustment is recorded in inventory history. Use Inventory to add a specific reason.</p>
    <form onSubmit={submit}><ErrorNotice error={error} /><Field label="Adjustment quantity" required type="number" min={-2147483648} max={2147483647} step={1} value={adjustment} onChange={setAdjustment} disabled={busy} />
      <div className="form-actions"><button type="button" disabled={busy} onClick={onClose}>Cancel</button><button className="primary" disabled={busy || adjustment === '' || Number(adjustment) === 0}>{busy ? 'Applying…' : 'Apply adjustment'}</button></div>
    </form>
  </Modal>;
}

export default function ProductsPage() {
  const { user } = useAuth();
  const [page, setPage] = useState({ content: [], page: 0, totalPages: 0, totalElements: 0 });
  const [pageNumber, setPageNumber] = useState(0);
  const [categories, setCategories] = useState([]); const [suppliers, setSuppliers] = useState([]);
  const [filters, setFilters] = useState(emptyFilters); const [applied, setApplied] = useState(emptyFilters);
  const [loading, setLoading] = useState(true); const [error, setError] = useState(null);
  const [editing, setEditing] = useState(null); const [deleting, setDeleting] = useState(null); const [adjusting, setAdjusting] = useState(null);
  const [revision, setRevision] = useState(0); const [message, setMessage] = useState('');
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(null);
    const params = { ...Object.fromEntries(Object.entries(applied).filter(([, v]) => v !== '')), page: pageNumber, size: 20 };
    Promise.all([api.get('/api/products', { params, signal: controller.signal }), api.get('/api/categories', { signal: controller.signal }), api.get('/api/suppliers', { signal: controller.signal })])
      .then(([p, c, s]) => { setPage(p.data); setCategories(c.data); setSuppliers(s.data); if (pageNumber > 0 && p.data.content.length === 0) setPageNumber(n => n - 1); })
      .catch(e => { if (!controller.signal.aborted) setError(e); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [pageNumber, applied, revision]);
  const refresh = (message = '') => { setMessage(message); setRevision(r => r + 1); };
  const filterField = (name, label, options) => <Field label={label} value={filters[name]} options={options} onChange={value => setFilters(f => ({ ...f, [name]: value }))} />;
  return <>
    <div className="page-heading"><div><p className="eyebrow">CORE INVENTORY</p><h1>Products</h1><p>Your catalog, stock levels, and replenishment signals.</p></div><button className="primary" disabled={loading || !!error || categories.length === 0} onClick={() => setEditing({})}>+ Add product</button></div>
    {!loading && !error && categories.length === 0 && <p className="notice">Create a category on the Categories page before adding a product.</p>}
    <form className="filters" onSubmit={e => { e.preventDefault(); setPageNumber(0); setApplied({ ...filters }); }}>
      {filterField('name', 'Search name')}{filterField('sku', 'Search SKU')}
      {filterField('categoryId', 'Category filter', [{ value: '', label: 'All categories' }, ...categories.map(c => ({ value: c.id, label: c.name }))])}
      {filterField('supplierId', 'Supplier filter', [{ value: '', label: 'All suppliers' }, ...suppliers.map(s => ({ value: s.id, label: s.name }))])}
      {filterField('active', 'Status filter', [{ value: '', label: 'All statuses' }, { value: 'true', label: 'Active' }, { value: 'false', label: 'Inactive' }])}
      {filterField('lowStock', 'Stock filter', [{ value: '', label: 'All stock levels' }, { value: 'true', label: 'Low stock' }, { value: 'false', label: 'Above reorder level' }])}
      <div className="filter-actions"><button className="primary" disabled={loading}>Apply filters</button><button type="button" onClick={() => { setFilters(emptyFilters); setApplied(emptyFilters); setPageNumber(0); }}>Reset</button></div>
    </form>
    <div className="section-toolbar"><span>{page.totalElements} products</span><button onClick={() => refresh()} disabled={loading}>Refresh</button></div>
    <CsvExport endpoint="/api/products" filters={applied} columns={productColumns} filename="stockflow-products.csv" disabled={loading || !!error} />
    {message && <p className="notice success" role="status">{message}</p>}<ErrorNotice error={error} />
    <div className="table-wrap"><table><caption className="sr-only">Product inventory</caption><thead><tr><th>Product / SKU</th><th>Category</th><th>Supplier</th><th className="number">Selling price</th><th className="number">Stock</th><th className="number">Reorder</th><th>Stock level</th><th>Status</th><th>Actions</th></tr></thead><tbody>
      {loading ? <tr><td colSpan={9}>Loading products…</td></tr> : error ? <tr><td colSpan={9}>Products could not be loaded. Try Refresh.</td></tr> : page.content.length === 0 ? <tr><td colSpan={9}>No products found. Add a product or adjust your filters.</td></tr> : page.content.map(p => <tr key={p.id}>
        <td><strong>{p.name}</strong><small>{p.sku}</small></td><td>{p.categoryName}</td><td>{p.supplierName || 'Unassigned'}</td><td className="number">{money(p.sellingPrice)}</td><td className="number">{p.quantityInStock}<small>{p.unit}</small></td><td className="number">{p.reorderLevel}</td><td><span className={`badge ${p.lowStock ? 'low' : 'good'}`}>{p.lowStock ? 'Low stock' : 'In stock'}</span></td><td><span className={`badge ${p.active ? '' : 'inactive'}`}>{p.active ? 'Active' : 'Inactive'}</span></td>
        <td><div className="row-actions"><button onClick={() => setEditing(p)} aria-label={`Edit ${p.name}`}>Edit</button><button onClick={() => setAdjusting(p)} aria-label={`Adjust stock for ${p.name}`}>Adjust stock</button>{user.role === 'ADMIN' && <button className="text-danger" onClick={() => setDeleting(p)} aria-label={`Delete ${p.name}`}>Delete</button>}</div></td>
      </tr>)}
    </tbody></table></div>
    <div className="pagination"><span>Page {page.totalPages ? pageNumber + 1 : 0} of {page.totalPages}</span><button disabled={loading || pageNumber === 0} onClick={() => setPageNumber(n => n - 1)}>Previous</button><button disabled={loading || pageNumber + 1 >= page.totalPages} onClick={() => setPageNumber(n => n + 1)}>Next</button></div>
    <p className="muted footnote">Products with movement history cannot be deleted; mark them inactive instead. Low stock means quantity is at or below the reorder level. Prices use your business currency; multi-currency support is outside this phase.</p>
    {editing && <ProductForm item={editing} categories={categories} suppliers={suppliers} onClose={() => setEditing(null)} onSaved={() => refresh('Product saved.')} />}
    {adjusting && <StockForm item={adjusting} onClose={() => setAdjusting(null)} onSaved={() => refresh('Stock adjusted.')} />}
    {deleting && <DeleteDialog name={deleting.name} onClose={() => setDeleting(null)} onDelete={async () => { await api.delete(`/api/products/${deleting.id}`); refresh('Product deleted.'); }} />}
  </>;
}
