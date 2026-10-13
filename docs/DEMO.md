# Run the portfolio demo

Requirements: Docker Desktop running, Node.js 22+, and internet access for the first build.

From the repository root:

```sh
node scripts/demo.mjs
```

Open **http://localhost:5185**. Use the generated username/password in `.demo/credentials.json`. Signing keys, database passwords and the demo credentials are ignored by Git under `.demo/`.

The launcher starts a separate `stockflow-portfolio-demo` Docker project and database, builds the actual app and seeds synthetic catalog records. It records a purchase receipt, then fulfills a sales order through the normal API workflows so the screenshots include traceable stock movements and a real calculated invoice. It seeds only an empty demo product catalog; later starts preserve the demo data.

The MySQL service has no host port. Frontend and API are bound to loopback on 5185 and 18085. First start also installs the frontend's locked Node dependencies to generate a BCrypt hash for the dedicated demo administrator. Bootstrap is enabled only in the separate demo Compose configuration.

Stop without deleting records:

```sh
node scripts/demo.mjs stop
```

## Suggested walkthrough

1. Dashboard: inventory value, low stock and order totals.
2. Products: stock after purchase receipt and sales fulfillment.
3. Purchase Orders: `PO-DEMO-001`, received once through the API.
4. Sales Orders: `SO-DEMO-001`, fulfilled, with invoice preview.
5. Inventory: reference-linked movements and previous/new balances.

Screenshots under `screenshots/` contain synthetic data from this local demo. This is not a hosted public demo. For normal deployments and migration guidance, see [OPERATIONS.md](OPERATIONS.md).
