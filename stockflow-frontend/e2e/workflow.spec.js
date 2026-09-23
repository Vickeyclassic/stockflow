import { test, expect } from '@playwright/test';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';

test('ADMIN purchase-to-sale workflow, exports, invoice, inventory and logout', async ({ page }, testInfo) => {
  const suffix = randomUUID().slice(0, 8);
  const category = `E2E category ${suffix}`, supplier = `E2E supplier ${suffix}`;
  const product = `E2E product ${suffix}`, sku = `E2E-${suffix.toUpperCase()}`, customer = `E2E customer ${suffix}`;
  const purchase = `PO-${suffix.toUpperCase()}`, sale = `SO-${suffix.toUpperCase()}`;
  const dialog = () => page.getByRole('dialog');
  const navigate = async name => page.getByRole('navigation').getByRole('link', { name, exact: true }).click();
  async function exportedContains(value, filename) {
    const downloaded = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Export CSV', exact: true }).click();
    const download = await downloaded;
    expect(download.suggestedFilename()).toBe(filename);
    const text = await readFile(await download.path(), 'utf8');
    expect(text).toContain(value);
    return text;
  }
  async function stockIs(quantity) {
    await navigate('Products');
    await page.getByLabel(/^Search SKU(?: \*)?$/).fill(sku);
    await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
    const row = page.getByRole('table', { name: 'Product inventory' }).getByRole('row').filter({ hasText: sku });
    await expect(row).toHaveCount(1);
    await expect(row.getByRole('cell').nth(4)).toHaveText(`${quantity}piece`);
  }

  await test.step('Login as ADMIN and create catalog records', async () => {
    await page.goto('/');
    await page.getByLabel(/^Username(?: \*)?$/).fill(process.env.E2E_USERNAME);
    await page.getByLabel(/^Password(?: \*)?$/).fill(process.env.E2E_PASSWORD);
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await expect(page.getByRole('navigation')).toBeVisible();
    await expect(page.locator('.session-user')).toContainText('ADMIN');

    await navigate('Categories');
    await page.getByRole('button', { name: '+ Add category', exact: true }).click();
    await dialog().getByLabel('Category name').fill(category);
    await dialog().getByRole('button', { name: 'Save category', exact: true }).click();
    await expect(dialog()).toHaveCount(0);

    await navigate('Suppliers');
    await page.getByRole('button', { name: '+ Add supplier', exact: true }).click();
    await dialog().getByLabel('Supplier name').fill(supplier);
    await dialog().getByRole('button', { name: 'Save supplier', exact: true }).click();
    await expect(dialog()).toHaveCount(0);

    await navigate('Products');
    await page.getByRole('button', { name: '+ Add product', exact: true }).click();
    await dialog().getByLabel('Product name').fill(product);
    await dialog().getByLabel(/^SKU(?: \*)?$/).fill(sku);
    await dialog().getByRole('combobox', { name: 'Category', exact: true }).selectOption({ label: category });
    await dialog().getByRole('combobox', { name: 'Supplier', exact: true }).selectOption({ label: supplier });
    await dialog().getByLabel('Cost price').fill('5.00');
    await dialog().getByLabel('Selling price').fill('9.00');
    await dialog().getByRole('button', { name: 'Save product', exact: true }).click();
    await expect(dialog()).toHaveCount(0);
    await stockIs(0);
  });

  await test.step('Create and receive a purchase; stock increases', async () => {
    await navigate('Purchase Orders');
    await page.getByRole('button', { name: '+ Create order', exact: true }).click();
    await dialog().getByLabel('Order number').fill(purchase);
    await dialog().getByRole('combobox', { name: 'Supplier', exact: true }).selectOption({ label: supplier });
    await dialog().getByRole('combobox', { name: 'Item 1 product', exact: true }).selectOption({ label: `${sku} · ${product} (stock 0)` });
    await dialog().getByLabel(/^Quantity(?: \*)?$/).fill('10');
    await dialog().getByRole('button', { name: 'Save draft', exact: true }).click();
    await dialog().getByRole('button', { name: 'Place order', exact: true }).click();
    await dialog().getByRole('button', { name: 'Receive order', exact: true }).click();
    await expect(dialog().getByText('RECEIVED', { exact: true })).toBeVisible();
    await dialog().getByRole('button', { name: 'Close dialog' }).click();
    await page.getByLabel(/^Order number(?: \*)?$/).fill(purchase);
    await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
    const csv = await exportedContains(purchase, 'stockflow-purchase-orders.csv');
    expect(csv).toContain('RECEIVED');
    await stockIs(10);
    await exportedContains(sku, 'stockflow-products.csv');
  });

  let salesId;
  await test.step('Create a customer and fulfill a sales order', async () => {
    await navigate('Customers');
    await page.getByRole('button', { name: '+ Add customer', exact: true }).click();
    await dialog().getByLabel('Customer name').fill(customer);
    await dialog().getByLabel(/^Email(?: \*)?$/).fill('demo@example.test');
    await dialog().getByLabel(/^Address(?: \*)?$/).fill('Demo Street\nDemo City');
    await dialog().getByRole('button', { name: 'Save customer', exact: true }).click();
    await expect(dialog()).toHaveCount(0);
    await navigate('Sales Orders');
    await page.getByRole('button', { name: '+ Create order', exact: true }).click();
    await dialog().getByLabel('Order number').fill(sale);
    await dialog().getByRole('combobox', { name: 'Customer', exact: true }).selectOption({ label: customer });
    await dialog().getByRole('combobox', { name: 'Item 1 product', exact: true }).selectOption({ label: `${sku} · ${product} (stock 10)` });
    await dialog().getByLabel(/^Quantity(?: \*)?$/).fill('3');
    await dialog().getByRole('button', { name: 'Save draft', exact: true }).click();
    await dialog().getByRole('button', { name: 'Confirm order', exact: true }).click();
    await dialog().getByRole('button', { name: 'Fulfill order', exact: true }).click();
    await expect(dialog().getByText('FULFILLED', { exact: true })).toBeVisible();
    salesId = (await dialog().getByText('Inventory history reference:').textContent()).match(/#(\d+)/)[1];
  });

  await test.step('Preview and print the invoice; export the filtered sales order', async () => {
    await dialog().getByRole('button', { name: 'Invoice / print', exact: true }).click();
    const invoice = page.getByRole('article', { name: 'Sales order invoice' });
    await expect(invoice).toContainText(sale);
    await expect(invoice).toContainText(customer);
    await expect(invoice).toContainText('demo@example.test');
    await expect(invoice).toContainText('FULFILLED');
    await expect(invoice.locator('.invoice-total')).toHaveText('Total amount 27.00');
    await expect(invoice.getByRole('row').filter({ hasText: sku })).toContainText('9.00');
    await page.evaluate(() => { window.print = () => { window.__printed = true; }; });
    await dialog().getByRole('button', { name: 'Print invoice', exact: true }).click();
    expect(await page.evaluate(() => window.__printed)).toBe(true);
    await page.emulateMedia({ media: 'print' });
    await expect(invoice).toBeVisible();
    await expect(page.getByRole('navigation')).toBeHidden();
    await expect(dialog().getByRole('button', { name: 'Print invoice', exact: true })).toBeHidden();
    await page.pdf({ path: testInfo.outputPath('invoice-a4.pdf'), format: 'A4', printBackground: true });
    await page.pdf({ path: testInfo.outputPath('invoice-letter.pdf'), format: 'Letter', printBackground: true });
    // Layout-only stress fixture; saved order data remains unchanged.
    await invoice.locator('tbody').evaluate(body => {
      for (let i = 2; i <= 60; i++) {
        const row = body.firstElementChild.cloneNode(true);
        row.dataset.printFixture = 'true';
        row.cells[0].textContent = `Print layout row ${i}: long product name / SKU for wrapping verification`;
        body.appendChild(row);
      }
    });
    await page.pdf({ path: testInfo.outputPath('invoice-multipage-a4.pdf'), format: 'A4', printBackground: true });
    await invoice.locator('[data-print-fixture]').evaluateAll(rows => rows.forEach(row => row.remove()));
    await page.emulateMedia({ media: 'screen' });
    await dialog().getByRole('button', { name: 'Back to order', exact: true }).click();
    await dialog().getByRole('button', { name: 'Close dialog' }).click();
    await page.getByLabel(/^Order number(?: \*)?$/).fill(sale);
    await page.getByRole('button', { name: 'Apply filters', exact: true }).click();
    await exportedContains(sale, 'stockflow-sales-orders.csv');
    await stockIs(7);
  });

  await test.step('Filter movement history, export it, and log out', async () => {
    await navigate('Inventory');
    const history = page.getByRole('region', { name: 'Transaction history' });
    await history.getByRole('combobox', { name: 'History product', exact: true }).selectOption({ label: `${sku} · ${product} (stock 7)` });
    await history.getByRole('combobox', { name: 'History type', exact: true }).selectOption('STOCK_OUT');
    await history.getByRole('combobox', { name: 'Reference type', exact: true }).selectOption('SALES_ORDER');
    await history.getByLabel('Reference / order ID').fill(salesId);
    const date = new Date().toISOString().slice(0, 10);
    await history.getByLabel('From date (UTC)').fill(date);
    await history.getByLabel('To date (UTC)').fill(date);
    await history.getByRole('button', { name: 'Filter history', exact: true }).click();
    const row = history.getByRole('table').getByRole('row').filter({ hasText: sku });
    await expect(row).toHaveCount(1);
    await expect(row.getByRole('cell').nth(3)).toHaveText('3');
    await expect(row.getByRole('cell').nth(4)).toHaveText('10');
    await expect(row.getByRole('cell').nth(5)).toHaveText('7');
    const csv = await exportedContains('SALES_ORDER', 'stockflow-inventory-transactions.csv');
    expect(csv).toContain(sku);
    await history.getByRole('combobox', { name: 'Rows per page', exact: true }).selectOption('10');
    await expect(history.getByText('Page 1 of 1 · 1 records')).toBeVisible();
    await page.getByRole('button', { name: 'Log out', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
    await page.goto('/#products');
    await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible();
    await expect(page.getByRole('navigation')).toHaveCount(0);
    expect(await page.evaluate(() => sessionStorage.getItem('stockflow.session'))).toBeNull();
  });
});
