# ElectroStock

**Desktop inventory management for small and medium-sized electronics businesses.**

ElectroStock tracks products, suppliers, purchases, sales and stock movements in one place. It handles serial-numbered devices individually, supports multi-currency purchasing with MAD as the base currency, and reports inventory value and gross profit from historical cost data.

![Java](https://img.shields.io/badge/Java-25-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-6DB33F)
![JavaFX](https://img.shields.io/badge/JavaFX-21-blue)
![MariaDB](https://img.shields.io/badge/MariaDB-11.4-003545)

---

## Table of contents

- [Features](#features)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [REST API](#rest-api)
- [Business rules](#business-rules)
- [Project structure](#project-structure)
- [Documentation](#documentation)
- [Scope and roadmap](#scope-and-roadmap)

---

## Features

| Area | Highlights |
| --- | --- |
| **Dashboard** | Sales performance (revenue, gross profit, units sold) for the month or today, stock availability donut, potential profit by product, latest stock movements |
| **Products** | Catalogue with search and filters (category, stock level, status), create, edit, deactivate and reactivate, category management, per-product icons |
| **Stock** | Opening stock, quantity adjustments, serial-unit additions, removals and recovery, full movement history per product |
| **Sales** | Draft sales, stock verification, confirmation with cost-of-goods capture, handover, cancellation before handover |
| **Purchases** | Draft purchases per supplier in any supported currency, confirmation increases stock atomically, cancellation when stock is still reversible |
| **Suppliers** | Register, edit, activate and deactivate, with purchase history preserved |
| **Reports** | Inventory valuation, low stock, sales by product and category, gross profit by product, purchases by supplier, serial-unit tracing |

## Architecture

ElectroStock is a client-server application. The JavaFX client contains no business logic; every rule is enforced by the backend.

```text
JavaFX desktop  ──HTTP/JSON──▶  Spring Boot REST API  ──▶  Services  ──▶  Repositories  ──▶  MariaDB
                                                              │
                                                              └──▶  Frankfurter API (exchange rates, cached)
```

- **Controllers** expose the REST endpoints and map requests to DTOs.
- **Services** hold business rules and run each operation in a single transaction.
- **Repositories** (Spring Data JPA) access MariaDB. The schema is managed manually through `database/schema.sql`; Hibernate does not alter it (`ddl-auto=none`).
- **Exchange rates** come from the [Frankfurter](https://frankfurter.dev) API, with the latest cached rate used as a fallback when the provider is unreachable.

See [`docs/architecture.md`](docs/architecture.md) for the full design notes.

## Tech stack

| Layer | Technology |
| --- | --- |
| Backend | Java 25, Spring Boot 4.1 (Web MVC, Data JPA, Validation) |
| Frontend | JavaFX 21, Jackson 3, `java.net.http` client |
| Database | MariaDB 11.4 (schema targets MariaDB 10.4+) |
| Build | Maven (wrapper included for the backend) |

## Getting started

### Prerequisites

- JDK 25 (the frontend compiles for Java 17 and above)
- Maven 3.9+ for the frontend (the backend ships with `mvnw`)
- MariaDB 10.4 or newer

### 1. Create the database

```bash
mysql -u root -p -P 3307 < database/schema.sql
```

This creates the `electrostock` database with all tables, constraints and indexes.

### 2. Start the backend

```bash
cd backend
# Linux / macOS
export DB_USERNAME=root
export DB_PASSWORD=your_password
./mvnw spring-boot:run
```

```powershell
# Windows PowerShell
cd backend
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your_password"
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:8080`.

### 3. Start the desktop client

```bash
cd frontend
mvn javafx:run
```

The client connects to `http://localhost:8080`, so start the backend first.

## Configuration

Backend settings live in `backend/src/main/resources/application.properties`:

| Property | Default | Notes |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:mariadb://127.0.0.1:3307/electrostock` | Change the host or port to match your MariaDB |
| `spring.datasource.username` | `${DB_USERNAME:root}` | Set the `DB_USERNAME` environment variable |
| `spring.datasource.password` | `${DB_PASSWORD}` | Set the `DB_PASSWORD` environment variable; never commit credentials |
| `server.port` | `8080` | The frontend currently expects this port |

## REST API

All endpoints are under `/api`. Errors return a plain-text message with an appropriate status: `400` for invalid input, `404` when something is not found, `409` for conflicts such as a duplicate name, a category still in use, or a cancellation that is no longer allowed.

| Resource | Base path | Operations |
| --- | --- | --- |
| Categories | `/api/categories` | list, get, create, update, delete |
| Products | `/api/products` | list, get, create, update, deactivate, reactivate, stock lookup |
| Stock | `/api/products/{id}/…` | opening stock, adjustments, tracked-unit addition, removal and recovery, movement history, unit status |
| Suppliers | `/api/suppliers` | list, get, create, update, deactivate, reactivate |
| Purchases | `/api/purchases` | create draft, manage items, confirm, cancel |
| Sales | `/api/sales` | create draft, manage items, confirm, handover, cancel |
| Currencies | `/api/currencies` | list, create |
| Reports | `/api/reports` | inventory valuation, low stock, stock availability, daily sales, sales by product and category, gross profit, purchases by supplier, recent and full stock movements, serial-unit trace |

## Business rules

- **Atomic operations.** Confirming or cancelling a purchase or sale, and every stock adjustment, run in one transaction. A failure leaves no partial stock or status changes.
- **No negative stock.** Sales and removals are rejected if they would take available stock below zero.
- **Costing.** Non-serial products use a moving weighted average. Serial-tracked products use specific identification, so each unit keeps its own acquisition cost.
- **Cost of goods sold** is captured when a sale is confirmed, so later cost changes never rewrite past profit.
- **Money.** All calculations use `BigDecimal` and `DECIMAL` columns. MAD is the base currency, and the exchange rate used is stored with each confirmed transaction.
- **History is preserved.** Stock movements are never deleted; corrections are recorded as reversals or adjustments. Adjustments require a reason (`DAMAGED`, `MISSING`, `COUNTING_CORRECTION` or `OTHER`), and `OTHER` requires an explanation.
- **Inactive records.** Inactive products and suppliers keep their history but cannot be used in new stock operations or purchases.
- **Sales lifecycle.** A sale can be cancelled before handover and not after.

## Project structure

```text
ElectroStock/
├── backend/            Spring Boot REST API
│   └── src/main/java/com/electrostock/backend/
│       ├── controller/ REST endpoints
│       ├── service/    Business rules and transactions
│       ├── repository/ Spring Data JPA repositories
│       ├── model/      JPA entities
│       ├── dto/        Request and response objects
│       ├── enums/      Statuses, movement types, adjustment reasons
│       └── exception/  Domain exceptions and the global error handler
├── frontend/           JavaFX desktop client
│   └── src/main/
│       ├── java/com/electrostock/frontend/
│       │   ├── api/    HTTP clients for the backend
│       │   └── view/   Screens and dialogs
│       └── resources/  Per-view stylesheets
├── database/           schema.sql
└── docs/               Requirements and architecture
```

## Documentation

- [`docs/requirements.md`](docs/requirements.md): functional requirements and scope exclusions
- [`docs/architecture.md`](docs/architecture.md): layers, transaction integrity and financial calculation rules
- [`database/schema.sql`](database/schema.sql): the full relational schema

## Scope and roadmap

ElectroStock is built for a **single administrator**. The first version intentionally excludes:

- Multiple user accounts and roles
- Customer management, returns and refunds
- Partial sale handover
- Operating-expense accounting
- Landed-cost allocation
- Manual exchange-rate entry and overrides

These are candidates for future versions, along with remote deployment, which the client-server split is designed to allow.