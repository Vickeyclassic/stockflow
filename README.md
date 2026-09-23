# StockFlow V1.3

A Java full stack inventory and order management application for a small business. StockFlow replaces disconnected stock sheets with one catalog, traceable stock movements, purchase and sales workflows, and a dashboard that shows what needs attention.

## Features

- **Authentication:** username/password login, BCrypt hashes, signed expiring JWTs, ADMIN and STAFF roles, protected APIs and frontend pages, current user display, and logout.
- **Products:** unique SKU, category/supplier relationships, cost and selling prices, units, reorder levels, active status, combined search/filtering, and server pagination. Stock changes use dedicated operations rather than normal product edits.
- **Categories and suppliers:** validated create/read/update/delete forms, contact details, and reference-aware deletion protection.
- **Customers:** contact details and active status; referenced customers cannot be deleted.
- **Inventory:** additions, withdrawals and adjustments with immutable transaction history, previous/new balances, references, filters, and pagination. Purchase receipts and sales issues support multiple lines.
- **Purchase orders:** draft creation/editing, DRAFT → ORDERED → RECEIVED, or cancellation before receipt. Receiving adds inventory once.
- **Sales orders:** draft creation/editing, DRAFT → CONFIRMED → FULFILLED, or cancellation before fulfillment. Fulfillment deducts inventory once. Confirmation does not reserve stock.
- **Dashboard/reports:** catalog counts, low-stock count, inventory cost value, received purchase and fulfilled sales totals, recent orders/movements, top-selling products, and daily totals with date filters.
- **UI:** dashboard landing page, consistent navigation and forms, responsive tables/layout, loading/empty/error states, status badges, confirmation dialogs, and role-aware delete actions.

Prices use one business currency; currency conversion, tax calculation, accounting, and payment processing are outside V1. Low stock means quantity at or below reorder level, including inactive products. Report order dates are inclusive; movement dates use UTC day boundaries. Inventory value uses current cost × current stock and is not a historical valuation.

## Tech stack and architecture

Java 21 · Spring Boot 4.1.1 · Spring MVC · Spring Security · OAuth2 Resource Server JWT support · BCrypt · Spring Data JPA/Hibernate · Jakarta Validation · MySQL 8 · Flyway 12.4 · springdoc OpenAPI 3.1.1 · Maven wrapper 3.9.16 · React 19 · Vite 8 · Axios. Integration tests use JUnit, MockMvc, Spring Security Test, and test-scoped H2.

The React client calls the REST API through one Axios client. Controllers validate DTOs, services apply business rules and transaction boundaries, and repositories persist JPA entities. Spring Security authenticates bearer tokens and checks roles before controllers execute. Existing hash-based navigation remains intentionally simple; operational pages mount only after session validation.

```text
stockflow/
├── stockflow-backend/
│   ├── src/main/java/com/stockflow/
│   │   ├── controller/       # REST endpoints, login and user creation
│   │   ├── dto/              # Validated requests and response contracts
│   │   ├── entity/           # Catalog, stock, orders and users
│   │   ├── repository/       # Persistence and locking queries
│   │   ├── service/          # Business rules and atomic workflows
│   │   ├── security/         # JWT, role rules, opt-in bootstrap
│   │   ├── config/           # CORS
│   │   └── exception/        # Consistent domain errors
│   ├── src/main/resources/
│   ├── src/test/             # Business and security integration tests
│   ├── pom.xml
│   └── mvnw / mvnw.cmd
├── stockflow-frontend/
│   ├── src/api/              # Bearer header and 401 handling
│   ├── src/components/       # Reusable forms/dialogs
│   ├── src/pages/            # Login and operational screens
│   ├── src/auth.jsx          # Session lifecycle
│   ├── src/App.jsx
│   └── package.json
├── scripts/HashPassword.java # Hidden-input BCrypt helper
└── README.md
```

Build outputs, dependencies, local environment files, credentials, `.tools/`, and `work/` are ignored by Git. Historical phase verification scripts remain for reference; they predate authentication and are not the V1 verification entry point.

## Authentication and permissions

| Operation | ADMIN | STAFF |
| --- | --- | --- |
| View dashboard, reports, catalog, orders, history | Yes | Yes |
| Create/update products, categories, suppliers, customers | Yes | Yes |
| Create/edit drafts and advance/cancel order workflows | Yes | Yes |
| Adjust stock, record movements, receipts and issues | Yes | Yes |
| Delete master data | Yes, subject to reference rules | No |
| Create/list users and activate/deactivate accounts | Yes | No |

All API routes require ADMIN or STAFF except `GET /api/health`, `GET /api/health/ready`, `GET /api/version`, and `POST /api/auth/login`. All DELETE API requests and `/api/users/**` require ADMIN. There is no public registration. The ADMIN-only Users page lists users, creates STAFF accounts, and activates/deactivates accounts. The existing user creation API remains backward compatible, accepting `username`, `password`, and `role` (`ADMIN` or `STAFF`). Usernames contain 3–100 letters/numbers/dots/underscores/hyphens and are stored lowercase. New passwords require at least 12 characters and at most 72 UTF-8 bytes. Password hashes are never returned.

JWTs are signed with HS256, checked for signature, issuer and expiry, and expire after one hour by default. Send `Authorization: Bearer <token>`. Missing/invalid/expired tokens return 401; forbidden actions return 403. The UI stores the session in tab-scoped `sessionStorage`, validates it using `/api/auth/me`, expires it with a timer, and clears it on a 401. Logout removes the local session and unmounts operational screens. Stateless tokens are not revoked server-side on logout; a copied token remains usable until expiry. There are no refresh tokens. Keep token lifetimes short and use HTTPS outside local development. Every real bearer token is also checked against the current account: inactive/deleted users and role mismatches are rejected immediately. Reactivation makes an unexpired token usable again; logout alone does not revoke tokens.

The backend is the authorization boundary; hiding delete buttons is only a usability measure. CSRF is disabled because APIs use explicit bearer headers, not cookie authentication. CORS accepts only configured origins and permits the Authorization header.

## Concurrency and rollback

Transactional services and pessimistic row locks serialize stock updates. Multiple products are locked in a consistent order during order execution to reduce deadlocks. Stock cannot become negative or overflow a Java integer. Database uniqueness constraints and optimistic versions provide additional conflict protection.

Order execution writes stock, transaction history and order status within one transaction. Failure on any line rolls back the entire operation. Locked status transitions prevent repeat receiving/fulfillment. Normal product edits cannot overwrite quantity, referenced master records cannot be deleted, and stock history has no edit/delete endpoints. Existing integration tests cover concurrency, duplicate execution and atomic rollback. H2 tests are not a substitute for production MySQL load testing.

## Database and prerequisites

Install JDK 21, MySQL 8, Node.js 22.12+ (or supported Node 20.19+), and npm. Create the database with a MySQL administrator:

```sql
CREATE DATABASE stockflow_db;
```

Use a dedicated account with access to this database. Flyway owns schema creation and upgrades; Hibernate uses `validate` in both development and production. The migration account needs DDL permissions. The application does not create the database. Existing V1/V1.1 installations must follow the explicit adoption process below before the first V1.2 startup. Set `JAVA_HOME` for your installation.

## Safe first administrator setup

No default password or signing secret is committed. The `dev` profile creates an ADMIN only when `BOOTSTRAP_ADMIN_PASSWORD_HASH` is supplied and no ADMIN exists; it never resets an existing account. Use a lowercase bootstrap username and a BCrypt hash with cost 10–16. Production bootstrap requires the additional explicit `bootstrap` profile; `prod` alone never creates an administrator.

First compile to download dependencies (compilation itself needs no database or runtime secrets):

```powershell
Set-Location D:\JavaFullStack\Projects\stockflow
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11' # adjust locally
$env:MAVEN_USER_HOME = "$PWD\.tools\maven"
.\stockflow-backend\mvnw.cmd -f .\stockflow-backend\pom.xml "-Dmaven.repo.local=$PWD\.tools\repository" -DskipTests compile
```

Generate a hash in an interactive terminal; the helper hides password input and prints only its hash:

```powershell
$cryptoJar = (Get-ChildItem .\.tools\repository\org\springframework\security\spring-security-crypto -Recurse -Filter '*.jar' | Sort-Object FullName | Select-Object -Last 1).FullName
java --class-path $cryptoJar .\scripts\HashPassword.java
$env:BOOTSTRAP_ADMIN_USERNAME = 'admin'
$env:BOOTSTRAP_ADMIN_PASSWORD_HASH = Read-Host 'Paste the generated BCrypt hash'
```

Generate a signing key without printing it (persist it securely for subsequent launches; changing it invalidates existing JWTs):

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
```

After the first successful startup, remove the bootstrap variables from future launches. Sign in with the username and original password you chose. ADMIN can provision STAFF from the Users page or through `POST /api/users` using its bearer token. Never commit passwords, hashes, tokens, or signing secrets.

## Backend startup (PowerShell)

From the repository root, with the signing key and optional first-admin hash set:

```powershell
$env:DB_HOST = 'localhost'
$env:DB_PORT = '3306'
$env:DB_USERNAME = 'your_mysql_user'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'MySQL password' -AsSecureString)).Password
$env:SPRING_PROFILES_ACTIVE = 'dev'
Set-Location .\stockflow-backend
.\mvnw.cmd '-Dmaven.repo.local=D:\JavaFullStack\Projects\stockflow\.tools\repository' spring-boot:run
```

API: `http://localhost:8080`. Public liveness: `GET /api/health`. Health is not a continuous database-readiness query. If the wrapper downloader has TLS trouble, the optional `node scripts/bootstrap-maven.cjs` downloads Maven and verifies its SHA-512 checksum. Maven requires network access for uncached dependencies.

## Frontend startup (separate terminal)

```powershell
Set-Location D:\JavaFullStack\Projects\stockflow\stockflow-frontend
npm.cmd ci
npm.cmd run dev
```

Open `http://localhost:5173` and sign in. The dashboard is the default authenticated page. Start with a category and supplier, add a product and customer, then create purchase/sales orders. For another port, set `CORS_ALLOWED_ORIGINS` on the backend and `VITE_API_BASE_URL` on the frontend as needed, then start Vite with `npm.cmd run dev -- --port 5174`. Origins must match scheme, hostname and port exactly.

## Environment variables

| Variable | Default / purpose |
| --- | --- |
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` |
| `DB_USERNAME` / `DB_PASSWORD` | Required MySQL credentials |
| `JWT_SECRET` | Required Base64 encoding of at least 32 random bytes; no default |
| `JWT_TTL_SECONDS` | `3600`; supported range 60–86400 |
| `BOOTSTRAP_ADMIN_USERNAME` | `admin`; dev or explicit bootstrap profile |
| `BOOTSTRAP_ADMIN_PASSWORD_HASH` | Empty; opt-in first ADMIN BCrypt hash |
| `SPRING_PROFILES_ACTIVE` | Default profile `dev`; use `dev` explicitly for bootstrap |
| `SERVER_PORT` | `8080` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173`; comma-separated exact origins |
| `VITE_API_BASE_URL` | `http://localhost:8080` |
| `JAVA_HOME` | JDK 21 directory |
| `MAVEN_USER_HOME` | Optional Maven wrapper cache directory |
| `FLYWAY_BASELINE_ON_MIGRATE` | `false`; one-time opt-in for reviewed V1/V1.1 schema adoption |
| `API_DOCS_ENABLED` | `true` in dev; `false` in prod and Compose |

Backend `.env.example` is reference only: Spring Boot does not automatically load `.env` files. Export variables in the launching terminal or configure the IDE. Vite reads `.env.local`; its variables are public, so never place secrets there. The development profile logs SQL without bound parameter values.

## API overview

| Methods | Endpoint | Purpose |
| --- | --- | --- |
| POST / GET | `/api/auth/login`, `/api/auth/me` | Login / current identity |
| GET, POST | `/api/users` | ADMIN lists / creates users |
| PATCH | `/api/users/{id}/active` | ADMIN activates/deactivates with `{"active":false}` |
| GET | `/api/health`, `/api/health/ready` | Public liveness / database readiness (200 or 503) |
| GET | `/api/version` | Public release version and build time; no configuration details |
| GET, POST | `/api/products`, `/api/categories`, `/api/suppliers`, `/api/customers` | List / create |
| GET, PUT, DELETE | Each master path + `/{id}` | Read / update / ADMIN delete |
| PATCH | `/api/products/{id}/stock` | Signed quantity adjustment |
| POST | `/api/inventory/movements` | Manual stock operation |
| GET | `/api/inventory/transactions`, `/{id}` | History / transaction detail |
| GET | `/api/inventory/dashboard` | Inventory summary |
| GET, POST | `/api/purchase-receipts`, `/api/sales-issues` | List / record document |
| GET | Document paths + `/{id}` | Document detail |
| GET, POST | `/api/purchase-orders`, `/api/sales-orders` | Filtered list / create draft |
| GET, PUT | Order paths + `/{id}` | Detail / edit draft |
| PATCH | Order paths + `/{id}/status` | Validated lifecycle transition |
| GET | `/api/dashboard`, `/api/reports` | Overview / date-filtered reports |

Writes use JSON. Creates normally return 201, reads/updates 200, deletes 204. Validation errors return 400, missing records 404, uniqueness/reference/concurrent conflicts 409. Domain errors include `timestamp`, `status`, `code`, `message`, `path`, `fieldErrors`; security errors provide `status`, `code`, `message`.

Product filters: `name`, `sku`, `categoryId`, `supplierId`, `active`, `lowStock`, `page`, `size`. Paged responses contain `content`, `page`, `size`, `totalElements`, `totalPages`. Page numbers are zero-based; default size is 20, maximum 100. Normal product PUT must omit `quantityInStock`; use `PATCH /api/products/{id}/stock` with `{"adjustment":-2}`. Unknown fields and fractional integer values are rejected.

Order creation uses `orderNumber`, `customerId` or `supplierId`, `orderDate` (`YYYY-MM-DD`), optional `notes`, and `items` with `productId`, positive whole `quantity`, and decimal `unitPrice`. Status changes use `{"status":"CONFIRMED"}` or another permitted transition. Report filters are `dateFrom` and `dateTo`.

## Build and test commands

```powershell
Set-Location D:\JavaFullStack\Projects\stockflow\stockflow-backend
.\mvnw.cmd '-Dmaven.repo.local=D:\JavaFullStack\Projects\stockflow\.tools\repository' -DskipTests compile
.\mvnw.cmd '-Dmaven.repo.local=D:\JavaFullStack\Projects\stockflow\.tools\repository' test
Set-Location ..\stockflow-frontend
npm.cmd run build
```

Tests use an isolated in-memory H2 database and a test-only signing key; MySQL credentials are unnecessary. Existing business tests run with an authenticated ADMIN test identity, while focused authentication and user-management tests exercise real login/JWTs, missing/invalid/expired token rejection, ADMIN access and STAFF restrictions. The frontend output is `stockflow-frontend/dist/`. To create an executable backend jar separately, run `mvnw.cmd -DskipTests package`; serve the frontend output with a static host and configure the API URL at build time.

## Future improvements

AI inventory insights, demand forecasting, barcode scanning, multi-warehouse support, and notifications are possible future work and are not implemented. Future hardening could add login throttling, account recovery and per-token revocation.


## Docker deployment

The deployment topology is browser → Nginx (static React build and `/api` proxy) → Spring Boot → MySQL. Only Nginx publishes a host port; MySQL and the backend stay on the Compose network. Frontend Docker builds use a same-origin API URL. Backend runs as a non-root user. All three containers have health checks, and dependent services wait for readiness.

On the deployment host, install Docker Engine/Desktop with Compose v2. Local development and tests do not require Docker. From the repository root:

```powershell
Copy-Item .env.example .env
# Edit .env locally with unique database passwords and a random Base64 JWT key.
# Generate a BCrypt hash using the hidden-input helper described above.
# Store BOOTSTRAP_ADMIN_PASSWORD_HASH in single quotes in .env to preserve dollar signs.
docker compose build
docker compose up -d mysql
```

For a **new database only**, initialize schema and bootstrap the first administrator in a foreground one-off backend container:

```powershell
docker compose run --rm -e SPRING_PROFILES_ACTIVE=prod,bootstrap backend
```

Wait for the backend's successful startup, then press Ctrl+C. Flyway creates the schema and bootstrap creates the ADMIN only if no ADMIN exists; it does not reset an existing password. Remove the bootstrap hash from `.env`, then run normally with schema validation:

```powershell
docker compose up -d
docker compose ps
```

Open `http://localhost:8080` (or `HTTP_PORT`) and log in using the original password chosen for the bootstrap hash. Set `CORS_ALLOWED_ORIGINS` to the browser's exact origin if changing ports/domains. Place an HTTPS reverse proxy in front of Nginx for public deployment. `.env` is ignored and must remain local; production operators can supply variables through their deployment secret store. Never put secrets in Vite variables.

The production profile validates the Flyway-managed schema, suppresses development SQL logging, and never seeds demo data. Existing databases use the Flyway adoption process below; do not run manual activation-column SQL or set Hibernate to update/create.

Compose creates its own database in the named `mysql-data` volume and does not touch a separately installed MySQL instance. Restore an existing database backup into that volume if migrating deployments; do not assume Compose uses your local database. `docker compose down` retains the volume. Do not use `down -v` for a database you need to keep. Back up the volume/database independently. Changing `.env` MySQL passwords after volume initialization does not rotate stored database credentials; rotate them in MySQL as well.

## Optional development demo data

Use a separate empty development database and explicitly set:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:APP_DEMO_ENABLED = 'true'
# Start the backend with the normal development command.
```

The seed creates two categories, one supplier, two products with opening-stock history, one customer, one draft purchase order and two draft sales orders. It uses existing transactional services. If any catalog/customer/order data exists, the seed does nothing, so restarts never replace or delete data. Remove `APP_DEMO_ENABLED` after use. Both `dev` and `APP_DEMO_ENABLED=true` are required; the presence of `prod` disables the component even if `dev` and the flag are also supplied. Docker never enables it.

## User management

Only ADMIN can see or open the Users page or call any `/api/users` endpoint. STAFF retains all existing V1 operational permissions. The UI creates STAFF accounts; the existing API still accepts ADMIN or STAFF for compatibility. List/status responses contain only `id`, `username`, `role`, and `active`; the existing creation response remains `username` and `role`. Password hashes are excluded from entity serialization as a second safeguard.

Deactivation preserves the user record and blocks both login and existing bearer tokens on their next request. Administrators cannot deactivate themselves. Activation changes lock administrator rows and reject removal of the last active ADMIN, including concurrent requests. Duplicate usernames return 409; invalid requests return 400. There is no account deletion or role-edit API.

## Continuous integration

`.github/workflows/ci.yml` runs on pushes and pull requests with read-only repository permissions. Separate jobs run the complete backend suite on Java 21 with isolated H2, and `npm ci` plus the frontend production build on Node 22. The unit/integration-test jobs need no MySQL service. A third Docker job builds both production images and runs real MySQL smoke checks with randomly generated, masked CI-only credentials; no deployment secrets are used. Tests cover real token authorization, user activation, validation, hash exclusion, and development-only seed guards in addition to existing business workflows.

## Screenshots

Place reviewed screenshots under `docs/screenshots/` when available:

- Login — placeholder
- Dashboard — placeholder
- Products and inventory — placeholder
- Purchase and sales orders — placeholder
- ADMIN Users page — placeholder

## Flyway migrations and existing database adoption

| Version | Migration | Purpose |
| --- | --- | --- |
| 1 | `V1__initial_schema.sql` | Complete original schema: 12 application tables, foreign keys, uniqueness/check constraints and movement indexes |
| 2 | `V2__user_activation.java` | Adds `app_users.active NOT NULL DEFAULT TRUE` only when missing; preserves existing V1.1 activation values |

Flyway runs before Hibernate validation. Empty databases automatically apply V1 then V2. No migration drops, truncates, deletes, reseeds, or rewrites business data. Flyway cleaning is disabled. The normal baseline setting is **false**, so an untracked nonempty database fails startup instead of being silently adopted. Do not change applied migrations or baseline a partial/unrelated schema. Add a new version for future changes. V2 is a Java migration because MySQL needs a metadata check to safely handle both existing schema variants; Flyway's normal Java migrations do not carry a SQL checksum, so review that source as immutable release history.

For an existing **V1 or V1.1** database without `flyway_schema_history`:

1. Take a restorable backup, stop application writers, and first rehearse against a restored copy. Confirm the database is the intended StockFlow database.
2. Review the existing schema against V1: `app_users`, `categories`, `suppliers`, `customers`, `products`, `inventory_transactions`, `purchase_orders`, `purchase_order_items`, `sales_orders`, `sales_order_items`, `stock_documents`, and `stock_document_lines`. V1.1 also has `app_users.active`; keep all its existing values. Resolve preexisting drift before adopting. Baseline records an assertion that V1 already exists; it does not repair missing tables or constraints.
3. Temporarily set `FLYWAY_BASELINE_ON_MIGRATE=true` for the first startup. Flyway records baseline version **1**, skips V1 creation, then applies V2. V1 users become active; V1.1 inactive users stay inactive.
4. Confirm readiness and schema history, then remove the temporary flag and restart normally. Subsequent starts validate the history and run only future migrations.

Compose adoption, after backup and schema review:

```powershell
docker compose stop backend frontend
docker compose build
# Uses existing MySQL volume; never add --volumes/-v to shutdown.
docker compose run --rm -e FLYWAY_BASELINE_ON_MIGRATE=true backend
# Wait for successful startup/readiness, then Ctrl+C.
docker compose up -d --wait
```

For direct Java/Maven startup, set `$env:FLYWAY_BASELINE_ON_MIGRATE='true'` for that one run, then `Remove-Item Env:FLYWAY_BASELINE_ON_MIGRATE`. Leave the flag false for new databases and all subsequent launches. If a Flyway history already exists, inspect it and migration checksums rather than re-baselining.

Verify from a database administration session:

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history ORDER BY installed_rank;
SELECT id, username, role, active FROM app_users ORDER BY id;
```

Expect versions 1 and 2, both successful; version 1 is `BASELINE` for adopted databases and `SQL` for new ones. Hibernate's `validate` also checks mapped columns/types at startup, but is not a full constraint-drift audit. MySQL DDL is not fully transactional across migration statements: if initialization fails, inspect the cause and history and restore/rehearse as necessary. Do not blindly enable baselining or run Flyway repair to hide a failure. No automatic rollback is provided. Existing `DB_DDL_AUTO` setup instructions are obsolete; V1.2 uses Flyway rather than Hibernate schema updates.

The default datasource is used for migrations. For deployments separating DDL and runtime access, provide `SPRING_FLYWAY_USER` and `SPRING_FLYWAY_PASSWORD` securely to the backend, and grant runtime credentials only the required application DML/read access. Compose uses one dedicated, database-scoped application account by default. Do not use the MySQL root account for the backend.

## Swagger / OpenAPI

With the backend running in development, open [Swagger UI](http://localhost:8080/swagger-ui/index.html) or [OpenAPI JSON](http://localhost:8080/v3/api-docs). These paths also work through the Docker Nginx proxy **when explicitly enabled**. The UI and specification default to disabled in production (`404`); set `API_DOCS_ENABLED=true` and recreate the backend only if documentation exposure is intended. Enabled documentation pages are public, but the operational APIs retain exactly the existing authentication and role checks.

The generated specification covers authentication, users, products, categories, suppliers, customers, sales/purchase orders, stock movements/documents, dashboard/reports, health, and release info. Request/response schemas reflect current DTOs. Each operation lists ADMIN/STAFF permissions, `x-roles`, bearer requirements, and relevant success/security responses. Login is public; user management and master-data deletion require ADMIN; other operational calls accept ADMIN or STAFF. Password request fields are write-only, and hashes are absent.

Use `POST /api/auth/login` with your own credentials, copy the returned token, select **Authorize**, and paste the token without a `Bearer ` prefix. Swagger sends the bearer header. Authorization is not persisted across page reloads. Try-it-out writes affect the selected database, so use a development database for exploratory calls. Do not paste production tokens into shared/public documentation hosts.

## CI Docker verification and release checks

The Docker job in `.github/workflows/ci.yml` runs `docker compose build backend frontend`, then `scripts/ci-docker-smoke.sh`. It starts MySQL, the production backend, and Nginx; checks readiness, version `1.3.0`, unauthenticated API rejection, and disabled production docs; and verifies both migrations on a new database. It also creates isolated V1/V1.1 fixture databases, performs explicit baseline adoption, compares preserved business-table checksums and user fields, checks inactive-user preservation, and tests a normal restart. Containers/volumes are disposed of only in this isolated CI job. The script refuses normal local invocation.

Local targeted verification requires only Java/Maven and Node:

```powershell
Set-Location stockflow-backend
.\mvnw.cmd '-Dtest=MigrationTests,ReleaseApiTests,ProductionReleaseTests,AuthApiTests' test
# Final backend suite once targeted checks pass:
.\mvnw.cmd test
Set-Location ..\stockflow-frontend
npm.cmd run build
```

Migration tests use isolated H2 in MySQL mode and preserve rows across every table. A production-profile test runs Flyway plus Hibernate validation. Existing business tests retain their isolated Hibernate-created H2 fixtures with Flyway disabled. Real MySQL/Docker behavior is verified by the additional CI job, not claimed by the H2 checks. A successful local build does not mean the GitHub-hosted Docker job has run; require its green result before release.

For deployment verification:

1. Require green backend, frontend, Docker, and E2E CI jobs for the exact release revision. Back up and apply the migration/adoption steps above.
2. Start/recreate the deployment and check `docker compose ps` reports healthy services. Keep `SPRING_PROFILES_ACTIVE=prod`, baseline opt-in false, demo seeding disabled, and docs disabled unless intentionally enabled.
3. Request `/api/health` (liveness) and `/api/health/ready` (database readiness), then `/api/version`. The version response contains only `service`, `version`, and `builtAt`; Maven generates build metadata during compile/package. Expect `stockflow-backend`, `1.3.0`, and the artifact's build timestamp. An IDE launch without generated metadata explicitly returns `development`/`unknown`. The existing health JSON remains unchanged.
4. Confirm migration history is successful, existing data/activation states match the backup, and a second restart adds no duplicate migrations. Sign in as ADMIN and STAFF and spot-check their existing permissions.
5. Verify the configured browser origin works, an unlisted origin is rejected, unauthenticated `/api/products` returns 401, and production docs return 404 when disabled.

## Production configuration review

- Secrets remain runtime-only: no JWT/database values are baked into images or returned by version/health endpoints. `.env` and local secret configuration are ignored/excluded from Docker contexts. Environment templates contain placeholders only. Restrict deployment-host/container-inspection access because container environment variables are visible to operators.
- Production requires nonblank database credentials and explicit, exact HTTP(S) CORS origins without wildcards, paths, userinfo, queries, or fragments. Configure HTTPS termination and the exact external browser origin; only Nginx publishes a host port in Compose.
- JWT signing still requires Base64 of at least 32 random bytes; HS256, issuer, expiry, allowed TTL, active-account and role checks are unchanged. Keep the signing key stable across normal deployments; intentional rotation invalidates existing tokens. No token/credential logging has been added.
- No Actuator dependency or management endpoints are introduced. Public health exposes only generic status; the new public version endpoint exposes build identity only. Normal APIs remain protected. OpenAPI/Swagger exposure is opt-in in production.
- Production SQL formatting/show-SQL and JDBC bind/extract logging are disabled, security logging remains INFO, and error responses omit stack traces and binding details. Do not override logging to DEBUG/TRACE on a production deployment handling credentials.

Implementation references: [Spring Boot Flyway initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [Flyway explicit baseline adoption](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html), and [springdoc OpenAPI](https://springdoc.org/).

## V1.3 printing, exports, and inventory history

Open a sales order, choose **Invoice / print**, review the saved order, and choose **Print invoice**. The browser print dialog can print or save to PDF without a backend PDF service. The view includes a company-details placeholder, order number/date/status, current customer contact details, product/SKU, quantity, saved unit prices/line totals, and saved order total. The print stylesheet hides application navigation and controls, repeats table headers, and allows long orders to flow over multiple pages. Replace the company placeholder before sharing a real document. This is an order summary: taxes, payment status, and statutory invoicing are outside StockFlow's current scope. Customer/product names reflect the current linked records, not an immutable historical billing snapshot.

**Export CSV** is available on Products, Inventory transaction history, Sales orders, and Purchase orders. Apply filters first; the export downloads every matching page, not just the visible rows. Order exports contain one row per order (use the invoice for line-item detail). CSV uses UTF-8 with a BOM, CRLF record separators, quoted/escaped cells, and spreadsheet-formula protection for text fields. Empty results produce headers only. Failed/cancelled exports do not download partial files. Exporting uses the same authenticated APIs and permissions as viewing. Concurrent edits may affect multi-page exports; these are convenient list exports, not transactionally frozen audit snapshots.

Inventory history now exposes the existing reference type and exact reference-ID filters alongside product, movement type, inclusive UTC date range, and pagination. Order references use the numeric ID shown in the received/fulfilled order details; select `PURCHASE_ORDER` or `SALES_ORDER` plus that ID. Choose 10, 20, 50, or 100 rows per page. Changes to filters/page size reset pagination to the first page. Stock calculations and movement APIs are unchanged.

## Playwright E2E smoke test

The smoke test drives the actual UI: ADMIN login → category/supplier/product → purchase order received (stock 0 → 10) → customer → sales order fulfilled (stock 10 → 7) → filtered inventory movement → logout → protected page shows login. It also checks all four CSV downloads, the invoice fields/total, the print action, and print-media visibility. No business/API calls are mocked. Only the native print dialog is replaced during automation so the test can complete unattended.

For a local isolated run, install dependencies and a browser:

```powershell
Set-Location D:\JavaFullStack\Projects\stockflow\stockflow-frontend
npm.cmd ci
npx.cmd playwright install chromium
npm.cmd test
npm.cmd run test:e2e
```

Alternatively, use an already installed Chrome browser by setting `$env:E2E_BROWSER_CHANNEL='chrome'` before the E2E command. Local E2E starts its own test-classpath backend on `127.0.0.1:18080` and Vite on `127.0.0.1:4173`. Both ports must be free; existing servers are never reused. The test-only Java launcher hardcodes a unique in-memory H2 datasource and Flyway connection, applies the unchanged migrations, and creates a temporary ADMIN with a generated password. It cannot run from the production jar. It never resets or connects to your development/production database. The servers stop when Playwright finishes. The Maven wrapper cache/repository for this local runner live under ignored `.tools/` in the repository.

The separate CI `e2e` job uses MySQL plus the production backend and Nginx frontend, with an explicit CI-only bootstrap overlay (`docker-compose.e2e.yml`). It generates masked, random credentials, builds/starts an isolated Compose project, runs the same test with `E2E_BASE_URL`, and removes only that CI project's disposable services/volume. The overlay is not for a normal deployment. Browser reports and failure screenshots are retained for seven days; traces and persisted authentication state are disabled.

To target an already running **disposable** demo environment, explicitly set `E2E_BASE_URL`, `E2E_USERNAME`, `E2E_PASSWORD`, and `E2E_ALLOW_WRITES=true`. This mode creates uniquely named records and leaves them in place; it performs no API cleanup/deletion. Do not point it at a working business database. `E2E_BASE_URL` must identify the frontend, not the backend. Reports are written to ignored `playwright-report/` and `test-results/` directories. See [Playwright web-server setup](https://playwright.dev/docs/test-webserver) and [CI guidance](https://playwright.dev/docs/ci).

## GitHub release readiness — v1.3.0

V1.3 changes presentation, exports, test infrastructure, and release metadata only. The V1/V2 migrations and existing business endpoints remain unchanged. A database already on V1.2 needs no new migration or baseline operation.

| Check | Release expectation |
| --- | --- |
| Backend targeted checks | 28 inventory/API documentation tests pass |
| Backend full suite | 93 tests, zero failures/errors/skips |
| Frontend focused tests | 7 CSV/filter tests pass |
| Frontend production build | `npm run build` succeeds |
| Playwright | 1 smoke scenario passes, including the full purchase-to-sale workflow |
| GitHub `backend` | Java 21 backend suite passes |
| GitHub `frontend` | `npm ci`, CSV tests, and Vite production build pass |
| GitHub `docker` | Both images build; real MySQL fresh/adopted schema and HTTP checks pass |
| GitHub `e2e` | Separate real MySQL/backend/Nginx browser workflow passes |

Local H2/browser results do not substitute for the GitHub Docker/MySQL checks. Require all four jobs to be green on the exact commit being tagged; inspect uploaded E2E reports if the browser job fails. After deployment, verify healthy containers, unchanged successful migration versions 1/2, and `/api/version` showing `1.3.0`. Existing databases and ADMIN/STAFF permissions must remain intact.

### Suggested portfolio demo

1. Start a separate demo deployment and sign in as ADMIN. Use optional development demo data only in an empty development database, or create a category, supplier, product, and customer manually.
2. Create a purchase order for 10 units and receive it. Show the increased product stock and the linked `PURCHASE_ORDER` inventory movement.
3. Create a sales order for 3 units, confirm and fulfill it. Show the reduced stock, the linked `SALES_ORDER` movement, and its previous/new balances.
4. Open **Invoice / print**, show the customer/items/status/total, and preview printing or saving to PDF.
5. Apply product/order/history filters and download the matching CSV files. Demonstrate history reference filters and page-size controls.
6. Show the ADMIN Users page, then sign out. Demonstrate that revisiting a protected page presents the login screen.

### Screenshots checklist

Capture only demo data and omit passwords, tokens, and database credentials:

- [ ] Login screen
- [ ] Dashboard with demo metrics
- [ ] Products showing stock after receipt/fulfillment
- [ ] Received purchase order
- [ ] Fulfilled sales order
- [ ] Invoice preview and browser print preview
- [ ] Filtered inventory history with order reference and pagination
- [ ] CSV export opened in a spreadsheet
- [ ] ADMIN Users page
- [ ] Four green GitHub checks and healthy Docker services

Suggested location: `docs/screenshots/`. Replace earlier placeholders with reviewed images when available; screenshots are a manual release task.

### Release tagging

Review the diff and confirm that V1/V2 migration files are unchanged. Commit the reviewed V1.3 changes through your normal branch/PR process, wait for all four GitHub jobs on the release commit, and capture the screenshots above. Then, from a clean checkout of that verified commit:

```bash
git tag -a v1.3.0 -m "StockFlow V1.3 portfolio/demo polish"
git push origin v1.3.0
```

Create a GitHub release from `v1.3.0` with the feature summary, test results, deployment verification, and demo screenshots. Tagging/publishing is a manual step; these commands are not run automatically. Build/deploy the tagged source, preserve the existing MySQL volume, and never use `down -v` on an installation containing business data.
