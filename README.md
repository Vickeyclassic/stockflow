# StockFlow — Phase 2

Inventory & Order Management System. **PHASE 2 COMPLETE**, verified in this repository on 2026-09-17. Phase 3 is not implemented.

## Workspace and stack

The repository root is `D:\JavaFullStack\Projects\stockflow`.

- Backend: Java 21, Spring Boot 4.1.1, Spring MVC, JPA, Validation, MySQL Connector/J; Maven wrapper 3.9.16.
- Frontend: React 19, Vite 8, Axios, locked npm dependencies.
- `stockflow-backend/src/main/java/com/stockflow/`: controllers, DTOs, services, entities, repositories, errors, and CORS configuration.
- `stockflow-backend/src/test/`: health/CORS and inventory integration tests using test-scoped H2.
- `stockflow-frontend/src/`: Products, Categories, Suppliers, reusable forms/dialogs, and API client.
- `scripts/bootstrap-maven.cjs`: optional checksum-verified Maven bootstrap.
- `scripts/verify-phase2.cjs`: live API/CORS and concurrent stock verification against an isolated test database.
- `.tools/` and `work/`: ignored local tools, caches, verification logs, and temporary database files.

## Phase 2 features

- Category create/list/view/edit/delete, required name, and case-insensitive unique names.
- Supplier create/list/view/edit/delete with contact person, email, phone, address, and validation.
- Product create/list/view/edit/delete, unique uppercase SKU, name, description, cost/selling prices, unit, reorder level, stock, and active status.
- Required category and optional supplier relationships. Referenced categories/suppliers cannot be deleted.
- Case-insensitive partial name/SKU search; category, supplier, active, and low-stock filters combine with AND.
- Server pagination, newest ID first; browser pages contain 20 products.
- Low stock is derived as `quantityInStock <= reorderLevel`, including equality and inactive products.
- Stock adjustments accept positive or negative whole quantities, reject zero, negative resulting balances, and integer overflow. Transactional row locking prevents concurrent withdrawals from overspending stock.
- Normal product edits preserve stock; stock changes use the dedicated endpoint.
- Browser forms, loading/empty/error states, validation messages, delete confirmations, filter reset, pagination, and connection status/retry.

No authentication, orders, payments, inventory transaction history, AI, or Phase 3 functionality is included. Active status is a catalog flag, not a restriction on stock adjustments. Prices have two-decimal validation; no multi-currency conversion is implemented. Deletes are permanent.

## Prerequisites and database

Install JDK 21, Node.js 22.12+ (or compatible Node 20.19+), npm, and MySQL 8. Set `JAVA_HOME` to your JDK directory.

Create the database using your MySQL administrator/client:

```sql
CREATE DATABASE stockflow_db;
```

Use an account with access to this database and development schema-update permissions. The database name is fixed in `application.properties`; the app does not create it. `spring.jpa.hibernate.ddl-auto=update` creates/updates entity tables for development. Versioned production migrations are not included.

## Backend startup — PowerShell

```powershell
Set-Location D:\JavaFullStack\Projects\stockflow
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11' # adjust for your installation
$env:MAVEN_USER_HOME = "$PWD\.tools\maven"
# Optional if the wrapper's PowerShell downloader fails TLS negotiation:
node .\scripts\bootstrap-maven.cjs

$env:DB_HOST = 'localhost'
$env:DB_PORT = '3306'
$env:DB_USERNAME = 'your_mysql_user'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'MySQL password' -AsSecureString)).Password
Set-Location .\stockflow-backend
.\mvnw.cmd '-Dmaven.repo.local=D:\JavaFullStack\Projects\stockflow\.tools\repository' spring-boot:run
```

The repository-local Maven cache avoids this environment's incorrect/unwritable `C:\.m2\repository` default. Maven needs network access on its first run. The bootstrap verifies the official SHA-512 checksum and uses Windows `tar.exe`; it does not disable TLS checks. Normally the wrapper downloads Maven without the optional bootstrap.

Verify liveness:

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP","service":"stockflow-backend","message":"StockFlow backend is running"}
```

Health is application liveness, not a continuous database readiness query. JPA establishes its database connection during startup.

## Frontend startup — separate PowerShell terminal

```powershell
Set-Location D:\JavaFullStack\Projects\stockflow\stockflow-frontend
npm.cmd ci
npm.cmd run dev
```

Open `http://localhost:5173`. The header should show **Connected**; click the connection button to retry. Create a category, then optionally a supplier, then create a product. Use **Edit**, **Adjust stock**, and **Delete** from product rows.

## Environment variables

| Variable | Default / meaning |
| --- | --- |
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_USERNAME` | Required MySQL account |
| `DB_PASSWORD` | Required MySQL password |
| `SERVER_PORT` | `8080` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173`; comma-separated exact origins |
| `SPRING_PROFILES_ACTIVE` | Optional; default profile is `dev` |
| `VITE_API_BASE_URL` | `http://localhost:8080` |
| `JAVA_HOME` | Local JDK 21 path |
| `MAVEN_USER_HOME` | Optional wrapper/cache location |
| `API_BASE_URL` | Live verification script only; defaults to `http://localhost:8081` |

Backend `.env.example` is documentation: Spring Boot does not automatically read `.env`. Export backend variables in the launching terminal. Vite reads `.env.local`; restart after changes. Vite variables are public and must contain no secrets. The development profile logs SQL without bound parameter logging. Local credentials, build outputs, tools, and test databases are ignored by Git.

## Alternate ports

Use free ports rather than stopping unrelated applications. For example, in the backend terminal:

```powershell
$env:SERVER_PORT = '8081'
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5174'
.\mvnw.cmd '-Dmaven.repo.local=D:\JavaFullStack\Projects\stockflow\.tools\repository' spring-boot:run
```

In the frontend terminal:

```powershell
$env:VITE_API_BASE_URL = 'http://localhost:8081'
npm.cmd run dev -- --port 5174
```

Open `http://localhost:5174`. CORS must match scheme, hostname, and port exactly (`127.0.0.1` differs from `localhost`). CORS allows GET, POST, PUT, PATCH, DELETE, OPTIONS and Accept/Content-Type headers under `/api/**`, without credential sharing. Vite uses a strict port and fails rather than silently selecting another one.

## API contract

All write requests use `Content-Type: application/json`.

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/health` | Liveness JSON |
| GET / POST | `/api/categories` | List array / create |
| GET / PUT / DELETE | `/api/categories/{id}` | Read / replace details / delete |
| GET / POST | `/api/suppliers` | List array / create |
| GET / PUT / DELETE | `/api/suppliers/{id}` | Read / replace details / delete |
| GET / POST | `/api/products` | Filtered page / create |
| GET / PUT / DELETE | `/api/products/{id}` | Read / replace details / delete |
| PATCH | `/api/products/{id}/stock` | Adjust quantity atomically |

Creates return 201 and a Location header. Reads/updates return 200; deletes return 204. Invalid values return 400, missing resources 404, and duplicate values/in-use references/concurrent conflicts 409.

Category body: `name` (required, max 100), `description` (optional, max 1000).
Supplier body: `name` (required, max 150), `contactPerson` (max 100), valid `email` (max 254), `phone` (max 30, digits and phone punctuation), `address` (max 500). Optional fields may be null.

Example product creation (replace IDs with existing records):

```json
{
  "sku": "DRILL-001",
  "name": "Cordless drill",
  "description": "Workshop equipment",
  "categoryId": 1,
  "supplierId": 1,
  "costPrice": "20.10",
  "sellingPrice": "30.25",
  "quantityInStock": 5,
  "reorderLevel": 5,
  "unit": "piece",
  "active": true
}
```

SKU: max 64, starts with a letter/number and otherwise permits letters, numbers, dots, underscores, and hyphens. Name max 150; description max 2000; unit required/max 30. Category must exist; supplier may be null. Prices are nonnegative, up to 10 integer and 2 fractional digits. Reorder level is a required nonnegative integer. Initial stock defaults to zero and active defaults to true when omitted on creation.

PUT uses the same detail fields but **omits `quantityInStock`** and requires `active`. Unknown fields and fractional integers are rejected. Responses include IDs/names of relationships, derived `lowStock`, and creation/update timestamps.

Stock body: `{"adjustment":-2}`. Resulting stock must remain between 0 and 2147483647.

Product query parameters: `name`, `sku`, `categoryId`, `supplierId`, `active`, `lowStock`, `page` (zero-based, default 0), `size` (1–100, default 20). Example:

```text
/api/products?name=drill&active=true&lowStock=true&page=0&size=20
```

Page response: `{ "content": [...], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }`.
Errors have `timestamp`, `status`, `code`, `message`, `path`, and `fieldErrors`; stack traces and internal exception messages are not sent to clients.

## Build and tests

With the JDK/wrapper environment configured:

```powershell
Set-Location D:\JavaFullStack\Projects\stockflow\stockflow-backend
.\mvnw.cmd '-Dmaven.repo.local=D:\JavaFullStack\Projects\stockflow\.tools\repository' clean verify
Set-Location ..\stockflow-frontend
npm.cmd run build
```

Backend output: `stockflow-backend/target/stockflow-backend-0.0.1-SNAPSHOT.jar` (run with `java -jar` and the same database/port environment). Frontend output: `stockflow-frontend/dist/`.

Backend tests use ephemeral H2 with `create-drop`, not your MySQL database. H2 is test-scoped and excluded from the production jar.

For live checks, start the backend against an **isolated disposable MySQL database**, allow origin `http://localhost:5174`, then run:

```powershell
Set-Location D:\JavaFullStack\Projects\stockflow
$env:API_BASE_URL = 'http://localhost:8081'
node .\scripts\verify-phase2.cjs
```

The script creates API-prefixed fixtures and deletes its records on success. A failed run can leave fixtures in the isolated database; inspect/reset that test database before retrying. Do not run it against a populated business database.

## Current verification — 2026-09-17

These are fresh results from `D:\JavaFullStack\Projects\stockflow`, not inherited historical results:

- Workspace: shell and temporary file create/read/delete passed; branch `main`; backend/frontend present. No `setup refresh had errors` occurred.
- Backend `clean verify`: **BUILD SUCCESS; 29 tests, 0 failures, 0 errors, 0 skipped** (26 inventory cases and 3 health/CORS cases).
- Packaged backend started on 8081 against a fresh isolated MySQL **8.0.46** on loopback port **13307**. No normal MySQL credentials were needed or verified.
- Live script: **36 HTTP/CORS checks passed**, plus simultaneous MySQL withdrawals yielding one 200 and one 400 with final stock 2, never negative.
- Live coverage: health, category/supplier/product CRUD, supplier validation, duplicates, invalid quantities/prices, reference deletion protection, relationships, combined search/filters, active status, two-page pagination, low-stock boundary and stock adjustment.
- Frontend production build passed (75 modules). Vite started on 5174 against backend 8081; browser displayed Connected.
- Browser: category and supplier create/list/edit/delete; supplier phone validation; product create/list/edit/delete; relationship display; inactive status; combined name/SKU/category/supplier/status/stock filters; negative-stock error; successful adjustment from 5 to 7 and low-stock result removal.
- Browser pagination: 21 products across two pages; Next reached the final product; deleting it recovered to page 1 of 1. All browser and pagination fixtures were removed afterward.
- Mockito emitted inline mock-maker self-attachment, dynamically loaded Byte Buddy agent, and class-sharing warnings. They were not suppressed. All tests passed on Java 21; future JDK agent-loading restrictions may require explicit Mockito agent configuration.
- Initial environment issues were resolved using a repository-local Maven cache and granted network access for missing dependencies. No application code changes were required by verification.

README and a reusable live verification script were updated. Existing project files remain untracked on `main`; no commit was created. Phase 3 remains unimplemented.

Temporary backend, frontend, and isolated MySQL verification servers were stopped after verification. The ignored test database/logs remain under work/.
