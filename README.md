# StockFlow V1

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

Java 21 · Spring Boot 4.1.1 · Spring MVC · Spring Security · OAuth2 Resource Server JWT support · BCrypt · Spring Data JPA/Hibernate · Jakarta Validation · MySQL 8 · Maven wrapper 3.9.16 · React 19 · Vite 8 · Axios. Integration tests use JUnit, MockMvc, Spring Security Test, and test-scoped H2.

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
│   │   ├── security/         # JWT, role rules, development bootstrap
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
| Create users through `POST /api/users` | Yes | No |

All API routes require ADMIN or STAFF except `GET /api/health` and `POST /api/auth/login`. All DELETE API requests and `/api/users/**` require ADMIN. There is no public registration and no user-management screen. User creation is a deliberately small admin API, accepting `username`, `password`, and `role` (`ADMIN` or `STAFF`). Usernames contain 3–100 letters/numbers/dots/underscores/hyphens and are stored lowercase. New passwords require at least 12 characters and at most 72 UTF-8 bytes. Password hashes are never returned.

JWTs are signed with HS256, checked for signature, issuer and expiry, and expire after one hour by default. Send `Authorization: Bearer <token>`. Missing/invalid/expired tokens return 401; forbidden actions return 403. The UI stores the session in tab-scoped `sessionStorage`, validates it using `/api/auth/me`, expires it with a timer, and clears it on a 401. Logout removes the local session and unmounts operational screens. Stateless tokens are not revoked server-side on logout; a copied token remains usable until expiry. There are no refresh tokens. Keep token lifetimes short and use HTTPS outside local development. Roles in issued tokens remain unchanged until a new login.

The backend is the authorization boundary; hiding delete buttons is only a usability measure. CSRF is disabled because APIs use explicit bearer headers, not cookie authentication. CORS accepts only configured origins and permits the Authorization header.

## Concurrency and rollback

Transactional services and pessimistic row locks serialize stock updates. Multiple products are locked in a consistent order during order execution to reduce deadlocks. Stock cannot become negative or overflow a Java integer. Database uniqueness constraints and optimistic versions provide additional conflict protection.

Order execution writes stock, transaction history and order status within one transaction. Failure on any line rolls back the entire operation. Locked status transitions prevent repeat receiving/fulfillment. Normal product edits cannot overwrite quantity, referenced master records cannot be deleted, and stock history has no edit/delete endpoints. Existing integration tests cover concurrency, duplicate execution and atomic rollback. H2 tests are not a substitute for production MySQL load testing.

## Database and prerequisites

Install JDK 21, MySQL 8, Node.js 22.12+ (or supported Node 20.19+), and npm. Create the database with a MySQL administrator:

```sql
CREATE DATABASE stockflow_db;
```

Use a dedicated account with access to this database. Development uses `spring.jpa.hibernate.ddl-auto=update`, so the account needs schema-update permissions. The application does not create the database. Versioned production migrations are not included. Set `JAVA_HOME` for your installation.

## Safe first administrator setup

No default password or signing secret is committed. The `dev` profile creates an ADMIN only when `BOOTSTRAP_ADMIN_PASSWORD_HASH` is supplied and no ADMIN exists; it never resets an existing account. Use a lowercase bootstrap username and a BCrypt hash with cost 10–16. Other profiles do not run this bootstrap.

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

After the first successful startup, remove the bootstrap variables from future launches. Sign in with the username and original password you chose. ADMIN can provision STAFF through `POST /api/users` using its bearer token. Never commit passwords, hashes, tokens, or signing secrets.

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
| `BOOTSTRAP_ADMIN_USERNAME` | `admin`; development bootstrap only |
| `BOOTSTRAP_ADMIN_PASSWORD_HASH` | Empty; opt-in first ADMIN BCrypt hash |
| `SPRING_PROFILES_ACTIVE` | Default profile `dev`; use `dev` explicitly for bootstrap |
| `SERVER_PORT` | `8080` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173`; comma-separated exact origins |
| `VITE_API_BASE_URL` | `http://localhost:8080` |
| `JAVA_HOME` | JDK 21 directory |
| `MAVEN_USER_HOME` | Optional Maven wrapper cache directory |

Backend `.env.example` is reference only: Spring Boot does not automatically load `.env` files. Export variables in the launching terminal or configure the IDE. Vite reads `.env.local`; its variables are public, so never place secrets there. The development profile logs SQL without bound parameter values.

## API overview

| Methods | Endpoint | Purpose |
| --- | --- | --- |
| POST / GET | `/api/auth/login`, `/api/auth/me` | Login / current identity |
| POST | `/api/users` | ADMIN creates a user |
| GET | `/api/health` | Public liveness |
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

Tests use an isolated in-memory H2 database and a test-only signing key; MySQL credentials are unnecessary. Existing business tests run with an authenticated ADMIN test identity, while four focused authentication tests exercise real login/JWTs, missing/invalid/expired token rejection, ADMIN access and STAFF restrictions. The frontend output is `stockflow-frontend/dist/`. To create an executable backend jar separately, run `mvnw.cmd -DskipTests package`; serve the frontend output with a static host and configure the API URL at build time.

## Future improvements

AI inventory insights, demand forecasting, barcode scanning, multi-warehouse support, and notifications are possible future work and are not implemented. Production hardening could add versioned migrations, login throttling, account recovery and token revocation.
