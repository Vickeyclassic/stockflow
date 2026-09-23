import { useEffect, useRef, useState } from 'react';
import { api } from '../api/client';
import { downloadCsv, fetchExportRows, toCsv } from '../utils/csv';

export default function CsvExport({ endpoint, filters, columns, filename, disabled = false }) {
  const controller = useRef(null);
  const [busy, setBusy] = useState(false), [message, setMessage] = useState(''), [error, setError] = useState('');
  useEffect(() => () => controller.current?.abort(), []);
  async function exportRows() {
    const request = new AbortController(); controller.current = request;
    setBusy(true); setError(''); setMessage('');
    try {
      const rows = await fetchExportRows(options => api.get(endpoint, options), { ...filters }, request.signal);
      downloadCsv(filename, toCsv(columns, rows));
      setMessage(`Exported ${rows.length} records.`);
    } catch (e) {
      if (!request.signal.aborted) setError(e.response?.data?.message || 'Export failed. No file was downloaded. Please retry.');
    } finally { if (controller.current === request) { controller.current = null; setBusy(false); } }
  }
  return <div className="csv-export">
    <button type="button" disabled={disabled || busy} onClick={exportRows} title="Download all pages matching the applied filters">{busy ? 'Exporting…' : 'Export CSV'}</button>
    {busy && <button type="button" onClick={() => { controller.current?.abort(); setMessage('Export cancelled.'); }}>Cancel export</button>}
    <span className="muted">All rows matching applied filters</span>
    {message && <span role="status">{message}</span>}{error && <span role="alert" className="text-danger">{error}</span>}
  </div>;
}
