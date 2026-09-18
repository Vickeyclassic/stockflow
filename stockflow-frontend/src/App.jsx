import { AuthProvider, useAuth } from './auth';
import LoginPage from './pages/LoginPage';
import { useEffect, useState } from 'react';
import { api } from './api/client';
import CatalogPage from './pages/CatalogPage';
import ProductsPage from './pages/ProductsPage';

import InventoryPage from './pages/InventoryPage';
import SalesOrdersPage from './pages/SalesOrdersPage';
import DocumentsPage from './pages/DocumentsPage';
import PurchaseOrdersPage from './pages/PurchaseOrdersPage';
import DashboardPage from './pages/DashboardPage';
const pages = ['dashboard', 'products', 'categories', 'suppliers', 'inventory', 'purchase-receipts', 'sales-issues', 'customers', 'sales-orders', 'purchase-orders'];
const pageLabel = name => name.split('-').map(part => part[0].toUpperCase() + part.slice(1)).join(' ');
const currentPage = () => pages.includes(window.location.hash.slice(1)) ? window.location.hash.slice(1) : 'dashboard';

export default function App() { return <AuthProvider><AuthenticatedApp /></AuthProvider>; }
function AuthenticatedApp() {
  const { user, checking, logout } = useAuth();
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
  if (checking) return <main className="login-screen" role="status">Checking session…</main>;
  if (!user) return <LoginPage />;
  return <div className="shell">
    <header><a className="brand" href="#dashboard"><span className="brand-icon" aria-hidden="true">S</span>StockFlow</a><span className="tagline">Inventory &amp; Order Management System</span><button className="connection" onClick={() => setAttempt(n => n + 1)} aria-label="Check backend connection"><span className={`dot ${health === 'Connected' ? 'online' : ''}`} /><span role="status">{health}</span></button><div className="session-user"><strong>{user.username}</strong><span className="badge">{user.role}</span></div><button onClick={() => logout()}>Log out</button></header>
    <nav aria-label="Main navigation">{pages.map(name => <a key={name} href={`#${name}`} aria-current={page === name ? 'page' : undefined}>{pageLabel(name)}</a>)}<span>V1</span></nav>
    <main>{page === 'dashboard' ? <DashboardPage /> : page === 'purchase-orders' ? <PurchaseOrdersPage /> : page === 'sales-orders' ? <SalesOrdersPage /> : page === 'products' ? <ProductsPage /> : page === 'inventory' ? <InventoryPage /> : ['purchase-receipts', 'sales-issues'].includes(page) ? <DocumentsPage key={page} purchase={page === 'purchase-receipts'} /> : <CatalogPage key={page} kind={page} />}</main>
    <footer><span>STOCKFLOW</span><span>Clarity in every operation.</span></footer>
  </div>;
}
