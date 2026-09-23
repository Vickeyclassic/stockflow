import test from 'node:test';
import assert from 'node:assert/strict';
import { fetchExportRows, historyFilters, toCsv } from '../src/utils/csv.js';

const columns = [{ label: 'Name', value: row => row.name }, { label: 'Amount', value: row => row.amount }];
test('CSV preserves Unicode, quotes, commas, multiline fields, nulls and numeric values', () => {
  assert.equal(toCsv(columns, [{ name: 'Café, "A"\nsecond line', amount: 12.5 }, { name: null, amount: 0 }]),
    '\uFEFF"Name","Amount"\r\n"Café, ""A""\nsecond line","12.5"\r\n"","0"\r\n');
});
test('CSV neutralizes spreadsheet formulas, including whitespace prefixes', () => {
  for (const value of ['=1+1', '+SUM(A1)', '-1+2', '@SUM(A1)', '  =1', '\t=1', '\ntext'])
    assert.ok(toCsv(columns, [{ name: value, amount: -2 }]).includes(`"'${value}"`));
  assert.ok(toCsv(columns, [{ name: 'safe', amount: -2 }]).includes('"-2"'));
});
test('empty exports still include a header', () => assert.equal(toCsv(columns, []), '\uFEFF"Name","Amount"\r\n'));
test('exports traverse all pages with a frozen set of applied filters', async () => {
  const calls = [];
  const rows = await fetchExportRows(async options => {
    calls.push(options.params);
    return { data: { content: [{ id: options.params.page }], totalPages: 3 } };
  }, { sku: 'DEMO', active: 'false', name: '', page: 42 });
  assert.deepEqual(rows.map(row => row.id), [0, 1, 2]);
  assert.deepEqual(calls, [0, 1, 2].map(page => ({ sku: 'DEMO', active: 'false', page, size: 100 })));
});
test('a failed page rejects the entire export instead of returning a partial CSV', async () => {
  await assert.rejects(fetchExportRows(async ({ params }) => {
    if (params.page === 1) throw new Error('offline');
    return { data: { content: [{ id: 1 }], totalPages: 2 } };
  }, {}), /offline/);
});
test('export cancellation prevents further page reads', async () => {
  const controller = new AbortController(); let calls = 0;
  await assert.rejects(fetchExportRows(async () => {
    calls++; controller.abort(); return { data: { content: [], totalPages: 2 } };
  }, {}, controller.signal), { name: 'AbortError' });
  assert.equal(calls, 1);
});
test('history filters use inclusive UTC dates and trimmed exact references', () => {
  assert.deepEqual(historyFilters({ productId: '7', dateFrom: '2026-01-01', dateTo: '2026-01-02',
    transactionType: '', referenceType: 'SALES_ORDER', referenceId: ' 42 ' }), {
    productId: '7', dateFrom: '2026-01-01T00:00:00Z', dateTo: '2026-01-02T23:59:59.999999999Z',
    referenceType: 'SALES_ORDER', referenceId: '42'
  });
});
