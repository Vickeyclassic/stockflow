import { createPortal } from 'react-dom';
import { Modal } from './Forms';

const money = value => Number(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export default function SalesInvoice({ order, onClose }) {
  return createPortal(<Modal title="Invoice preview" className="invoice-dialog" onClose={onClose}>
    <article className="sales-invoice" aria-label="Sales order invoice">
      <div className="invoice-heading"><div><h2>Your company name</h2><p>Company address · Phone · Email</p><small>Company details placeholder</small></div><div><h1>Sales order invoice</h1><p><strong>{order.orderNumber}</strong></p></div></div>
      <div className="invoice-parties"><section><h3>Customer</h3><strong>{order.customer.name}</strong><p className="preserve-lines">{order.customer.address || 'Address not provided'}</p><p>{order.customer.email || 'Email not provided'}<br />{order.customer.phone || 'Phone not provided'}</p></section>
        <section><h3>Order details</h3><p>Date: {order.orderDate}</p><p>Status: <strong>{order.status}</strong></p><p>Order ID: {order.id}</p></section></div>
      <table><caption className="sr-only">Invoice line items</caption><thead><tr><th>Product / SKU</th><th className="number">Quantity</th><th className="number">Unit price</th><th className="number">Line total</th></tr></thead>
        <tbody>{order.items.map((item, index) => <tr key={index}><td>{item.productName}<small>{item.sku}</small></td><td className="number">{item.quantity}</td><td className="number">{money(item.unitPrice)}</td><td className="number">{money(item.lineTotal)}</td></tr>)}</tbody></table>
      <p className="invoice-total">Total amount <strong>{money(order.totalAmount)}</strong></p>
      {order.notes && <section><h3>Notes</h3><p className="preserve-lines">{order.notes}</p></section>}
      <p className="muted footnote">Amounts use your business currency. This is an order summary; StockFlow does not track tax or payment status.</p>
    </article>
    <div className="form-actions invoice-controls"><button onClick={onClose}>Back to order</button><button className="primary" onClick={() => window.print()}>Print invoice</button></div>
  </Modal>, document.body);
}
