export const nonemptyFilters = filters => Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '' && value != null));

// Dates in the history API are Instants; UI date boundaries are inclusive UTC days.
export function historyFilters(filters) {
  const params = nonemptyFilters(filters);
  if (params.dateFrom) params.dateFrom += 'T00:00:00Z';
  if (params.dateTo) params.dateTo += 'T23:59:59.999999999Z';
  if (params.referenceId) params.referenceId = params.referenceId.trim();
  return nonemptyFilters(params);
}

function cell(value) {
  let text = value == null ? '' : String(value);
  // Treat spreadsheet formulas as text, including whitespace-prefixed payloads.
  if (typeof value === 'string' && (/^[\s\u0000-\u001f]*[=+\-@]/.test(text) || /^[\t\r\n]/.test(text))) text = "'" + text;
  return `"${text.replaceAll('"', '""')}"`;
}
export function toCsv(columns, rows) {
  return '\uFEFF' + [columns.map(column => cell(column.label)).join(','),
    ...rows.map(row => columns.map(column => cell(column.value(row))).join(','))].join('\r\n') + '\r\n';
}

export async function fetchExportRows(getPage, filters, signal) {
  const rows = [];
  let page = 0, totalPages;
  do {
    signal?.throwIfAborted();
    const { data } = await getPage({ params: { ...nonemptyFilters(filters), page, size: 100 }, signal });
    if (!Array.isArray(data.content) || !Number.isInteger(data.totalPages) || data.totalPages < 0)
      throw new Error('The export response was invalid. Please refresh and retry.');
    rows.push(...data.content);
    totalPages = data.totalPages;
    page++;
  } while (page < totalPages);
  signal?.throwIfAborted();
  return rows;
}

export function downloadCsv(filename, csv) {
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url; link.download = filename; document.body.appendChild(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

const column = (label, key) => ({ label, value: typeof key === 'function' ? key : row => row[key] });
export const productColumns = [column('SKU', 'sku'), column('Product', 'name'), column('Category', 'categoryName'),
  column('Supplier', 'supplierName'), column('Cost price', 'costPrice'), column('Selling price', 'sellingPrice'),
  column('Stock', 'quantityInStock'), column('Unit', 'unit'), column('Reorder level', 'reorderLevel'), column('Active', 'active')];
export const movementColumns = [column('Movement ID', 'id'), column('Date (UTC)', 'createdAt'), column('SKU', 'productSku'),
  column('Product', 'productName'), column('Movement type', 'transactionType'), column('Quantity', 'quantity'),
  column('Previous stock', 'previousStock'), column('New stock', 'newStock'), column('Reference type', 'referenceType'),
  column('Reference ID', 'referenceId'), column('Reason', 'reason'), column('Notes', 'notes')];
export const orderColumns = purchase => [column('Order ID', 'id'), column('Order number', 'orderNumber'),
  column('Order date', 'orderDate'), column(purchase ? 'Supplier' : 'Customer', row => (purchase ? row.supplier : row.customer)?.name),
  column('Status', 'status'), column('Total amount', 'totalAmount'), column('Notes', 'notes')];
