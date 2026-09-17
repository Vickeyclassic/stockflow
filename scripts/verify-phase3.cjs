// Use only with an isolated disposable stockflow_db. Audit fixtures are retained
// until the isolated database is removed; application history cannot be deleted.
const assert = require('node:assert/strict');
const base = process.env.API_BASE_URL || 'http://localhost:8081';
const origin = process.env.TEST_ORIGIN || 'http://localhost:5174';
const tag = `QA-${Date.now()}`;
let checks = 0;
async function request(method, path, body, expected = 200) {
  const r = await fetch(base + path, { method, headers: { 'Content-Type': 'application/json', Origin: origin }, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await r.text(); assert.equal(r.status, expected, `${method} ${path}: ${text}`);
  assert.equal(r.headers.get('access-control-allow-origin'), origin); checks++;
  return text ? JSON.parse(text) : null;
}
(async () => {
  assert.equal((await request('GET', '/api/health')).status, 'UP');
  const c = await request('POST', '/api/categories', { name: tag }, 201);
  const s = await request('POST', '/api/suppliers', { name: tag }, 201);
  const body = { name: tag, sku: tag, categoryId: c.id, costPrice: '2.10', sellingPrice: '3.25', quantityInStock: 10, reorderLevel: 5, unit: 'piece' };
  const a = await request('POST', '/api/products', body, 201);
  const b = await request('POST', '/api/products', { ...body, sku: tag + '-B' }, 201);
  const stock = async id => (await request('GET', `/api/products/${id}`)).quantityInStock;
  const history = async id => request('GET', `/api/inventory/transactions?productId=${id}`);
  assert.equal((await history(a.id)).content[0].referenceType, 'OPENING_STOCK');
  const movement = (type, quantity, productId = a.id) => ({ productId, transactionType: type, quantity, reason: 'Live verification', referenceId: tag });
  const incoming = await request('POST', '/api/inventory/movements', movement('STOCK_IN', 3), 201);
  assert.equal(incoming.previousStock, 10); assert.equal(incoming.newStock, 13);
  await request('GET', `/api/inventory/transactions/${incoming.id}`);
  const outgoing = await request('POST', '/api/inventory/movements', movement('STOCK_OUT', 5), 201);
  assert.equal(outgoing.newStock, 8);
  await request('POST', '/api/inventory/movements', movement('ADJUSTMENT_IN', 2), 201);
  await request('POST', '/api/inventory/movements', movement('ADJUSTMENT_OUT', 1), 201);
  await request('PATCH', `/api/products/${a.id}/stock`, { adjustment: 1 });
  assert.equal((await history(a.id)).content[0].referenceType, 'LEGACY_ADJUSTMENT');
  for (const quantity of [0, -1, 1.5]) await request('POST', '/api/inventory/movements', movement('STOCK_IN', quantity), 400);
  await request('POST', '/api/inventory/movements', movement('STOCK_OUT', 11), 400);
  await request('POST', '/api/inventory/movements', movement('WRONG', 1), 400);
  await request('POST', '/api/inventory/movements', movement('STOCK_IN', 1, 999999999), 404);
  assert.equal(await stock(a.id), 10);
  const filtered = await request('GET', `/api/inventory/transactions?productId=${a.id}&transactionType=STOCK_IN&referenceType=MANUAL&referenceId=${tag}&dateFrom=2020-01-01T00:00:00Z&dateTo=2100-01-01T00:00:00Z`);
  assert.equal(filtered.totalElements, 1);
  const p0 = await request('GET', `/api/inventory/transactions?productId=${a.id}&size=1&page=0`);
  const p1 = await request('GET', `/api/inventory/transactions?productId=${a.id}&size=1&page=1`);
  assert.equal(p0.totalElements, 6); assert.notEqual(p0.content[0].id, p1.content[0].id);
  await request('GET', '/api/inventory/transactions?dateFrom=bad', undefined, 400);
  await request('GET', '/api/inventory/transactions?dateFrom=2100-01-01T00:00:00Z&dateTo=2020-01-01T00:00:00Z', undefined, 400);
  const line = (productId, quantity) => ({ productId, quantity, unitPrice: '2.10' });
  const receipt = { number: tag + '-PR', date: '2026-09-17', supplierId: s.id, lines: [line(a.id, 4), line(b.id, 5)] };
  const pr = await request('POST', '/api/purchase-receipts', receipt, 201);
  assert.equal(await stock(a.id), 14); assert.equal(await stock(b.id), 15);
  assert.equal((await request('GET', `/api/purchase-receipts/${pr.id}`)).lines.length, 2);
  await request('GET', '/api/purchase-receipts');
  assert.equal((await request('GET', `/api/inventory/transactions?referenceType=PURCHASE_RECEIPT&referenceId=${pr.id}`)).totalElements, 2);
  await request('POST', '/api/purchase-receipts', receipt, 409);
  await request('POST', '/api/purchase-receipts', { ...receipt, number: tag + '-BAD-S', supplierId: 999999999 }, 404);
  await request('POST', '/api/purchase-receipts', { ...receipt, number: tag + '-BAD-P', lines: [line(a.id, 1), line(999999999, 1)] }, 404);
  await request('POST', '/api/purchase-receipts', { ...receipt, number: tag + '-OVERFLOW', lines: [line(a.id, 1), line(b.id, 2147483647)] }, 400);
  assert.equal(await stock(a.id), 14); assert.equal(await stock(b.id), 15);
  assert.equal((await history(a.id)).totalElements, 7);
  const issue = { number: tag + '-SI', date: '2026-09-17', lines: [line(a.id, 3), line(b.id, 4)] };
  const si = await request('POST', '/api/sales-issues', issue, 201);
  assert.equal(await stock(a.id), 11); assert.equal(await stock(b.id), 11);
  await request('GET', `/api/sales-issues/${si.id}`); await request('GET', '/api/sales-issues');
  await request('POST', '/api/sales-issues', issue, 409);
  await request('POST', '/api/sales-issues', { ...issue, number: tag + '-FAIL', lines: [line(a.id, 1), line(b.id, 12)] }, 400);
  assert.equal(await stock(a.id), 11); assert.equal(await stock(b.id), 11);
  assert.equal((await history(a.id)).totalElements, 8);
  // Actual concurrent MySQL requests: only one withdrawal may consume ten units.
  const results = await Promise.all([1, 2].map(() => fetch(base + '/api/inventory/movements', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(movement('STOCK_OUT', 10)) })));
  assert.deepEqual(results.map(r => r.status).sort(), [201, 400]); assert.equal(await stock(a.id), 1);
  // Opposite line orders must still acquire locks in the same product-ID order.
  await Promise.all([
    request('POST', '/api/purchase-receipts', { ...receipt, number: tag + '-CON-PR', lines: [line(a.id, 2), line(b.id, 2)] }, 201),
    request('POST', '/api/sales-issues', { ...issue, number: tag + '-CON-SI', lines: [line(b.id, 1), line(a.id, 1)] }, 201)
  ]);
  assert.equal(await stock(a.id), 2); assert.equal(await stock(b.id), 12);
  await request('DELETE', `/api/products/${a.id}`, undefined, 409);
  await request('DELETE', `/api/suppliers/${s.id}`, undefined, 409);
  await request('GET', '/api/inventory/dashboard');
  const rows = (await history(a.id)).content.slice().reverse();
  for (let i = 1; i < rows.length; i++) assert.equal(rows[i].previousStock, rows[i - 1].newStock);
  console.log(`PASS: ${checks} live HTTP/CORS checks plus concurrent stock-out and mixed-document checks. Fixtures ${tag} retained in isolated database for audit integrity.`);
})().catch(e => { console.error(e); process.exitCode = 1; });
