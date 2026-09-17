import { useEffect, useState } from 'react';
import { api } from './api/client';
import CatalogPage from './pages/CatalogPage';
import ProductsPage from './pages/ProductsPage';

import InventoryPage from './pages/InventoryPage';
import SalesOrdersPage from './pages/SalesOrdersPage';
import DocumentsPage from './pages/DocumentsPage';
const pages = ['products', 'categories', 'suppliers', 'inventory', 'purchase-receipts', 'sales-issues', 'customers', 'sales-orders'];
const pageLabel = name => name.split('-').map(part => part[0].toUpperCase() + part.slice(1)).join(' ');
const currentPage = () => pages.includes(window.location.hash.slice(1)) ? window.location.hash.slice(1) : 'products';

export default function App() {
  const [page, setPage] = useState(currentPage);
  const [health, setHealth] = useState('Checking');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => { const update = () => setPage(currentPage()); window.addEventListener('hashchange', update); return () => window.removeEventListener('hashchange', update); }, []);
  useEffect(() => {
    const controller = new AbortController(); setHealth('Checking');
    api.get('/api/health', { signal: controller.signal }).then(({ data }) => setHealth(data.status === 'UP' && data.service === 'stockflow-backend' ? 'Connected' : 'Unavailable'))
      .catch(() => { if (!controller.signal.aborted) setHealth('Unavailable'); });
    return () => controller.abort();
  }, [attempt]);
  return <div className="shell">
    <header><a className="brand" href="#products"><span className="brand-icon" aria-hidden="true">S</span>StockFlow</a><span className="tagline">Inventory &amp; Order Management System</span><button className="connection" onClick={() => setAttempt(n => n + 1)} aria-label="Check backend connection"><span className={`dot ${health === 'Connected' ? 'online' : ''}`} /><span role="status">{health}</span></button></header>
    <nav aria-label="Main navigation">{pages.map(name => <a key={name} href={`#${name}`} aria-current={page === name ? 'page' : undefined}>{pageLabel(name)}</a>)}<span>PHASE 4A</span></nav>
    <main>{page === 'sales-orders' ? <SalesOrdersPage /> : page === 'products' ? <ProductsPage /> : page === 'inventory' ? <InventoryPage /> : ['purchase-receipts', 'sales-issues'].includes(page) ? <DocumentsPage key={page} purchase={page === 'purchase-receipts'} /> : <CatalogPage key={page} kind={page} />}</main>
    <footer><span>STOCKFLOW</span><span>Clarity in every operation.</span></footer>
  </div>;
}
