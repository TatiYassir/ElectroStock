# \# ElectroStock — Technical Architecture

# &#x20;

# \## 1. Overview

# &#x20;

# ElectroStock is a client-server application. A JavaFX desktop client talks to a Spring Boot REST API over HTTP and JSON, and the API stores everything in MariaDB. The client holds no business rules; the backend validates every request and enforces every rule.

# &#x20;

# ```text

# ┌──────────────────┐  HTTP / JSON   ┌───────────────────────────────────────────┐   JDBC   ┌──────────┐

# │  JavaFX client   │ ─────────────▶ │  Spring Boot API (port 8080)              │ ───────▶ │ MariaDB  │

# │  (frontend/)     │ ◀───────────── │  controller → service → repository        │          │ (3307)   │

# └──────────────────┘                └───────────────────────┬───────────────────┘          └──────────┘

# &#x20;                                                           │ HTTPS

# &#x20;                                                           ▼

# &#x20;                                                 Frankfurter exchange-rate API

# ```

# &#x20;

# | Part | Technology |

# | --- | --- |

# | Backend | Java 25, Spring Boot 4.1 (Web MVC, Data JPA, Validation), `mariadb-java-client` |

# | Frontend | JavaFX 21, `java.net.http.HttpClient`, Jackson 3 (`tools.jackson`) |

# | Database | MariaDB 11.4 |

# | Build | Maven (the backend includes the Maven wrapper) |

# &#x20;

# \## 2. Backend

# &#x20;

# The package is `com.electrostock.backend`.

# &#x20;

# | Package | Contents |

# | --- | --- |

# | `controller` | REST controllers. They take request DTOs, call a service and return DTOs or entities. |

# | `service` | Business rules and transactions. |

# | `repository` | Spring Data JPA repositories, plus JPQL queries for reports and stock totals. |

# | `model` | JPA entities with their own state-change methods (for example `Sale.cancelBeforeHandover`). |

# | `dto` | Request and response records, grouped by area. Request DTOs use Bean Validation annotations. |

# | `enums` | `ProductStatus`, `SupplierStatus`, `PurchaseStatus`, `SaleStatus`, `UnitStatus`, `MovementType`, `AdjustmentReason`. |

# | `exception` | Domain exceptions and `GlobalExceptionHandler`. |

# &#x20;

# \### 2.1 Services

# &#x20;

# | Service | Responsibility |

# | --- | --- |

# | `CategoryService` | Category CRUD. Names are trimmed, limited to 100 characters and unique. Deleting a category with products is refused. |

# | `ProductService` | Create, edit, deactivate and reactivate products. Checks SKU and name, and reads current stock. |

# | `SupplierService` | Supplier CRUD and deactivate or reactivate. |

# | `PurchaseService` | Purchase drafts, item changes, confirmation (with serial numbers) and cancellation. |

# | `SaleService` | Sale drafts, item changes, discarding drafts, confirmation, handover and cancellation. |

# | `StockService` | Opening stock, adjustments, tracked-unit removal, addition and recovery, movement history, serial trace. |

# | `CostingService` | Weighted-average cost and the cost of untracked quantities leaving stock. |

# | `ExchangeRateService` | Fetches a rate from Frankfurter, caches it, and falls back to the cache. |

# | `CurrencyService` | Lists currencies and creates new ones after checking that a rate exists. |

# | `InventoryReportService`, `SaleReportService`, `PurchaseReportService`, `ProductReportService` | Read-only reports. |

# &#x20;

# \### 2.2 REST API

# &#x20;

# All routes are under `/api`. Controllers keep the HTTP layer thin; each route calls a single service method.

# &#x20;

# | Base path | Routes |

# | --- | --- |

# | `/api/categories` | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}` |

# | `/api/products` | `GET`, `GET /{id}`, `GET /{id}/stock`, `POST`, `PUT /{id}`, `PATCH /{id}/deactivate`, `PATCH /{id}/reactivate` |

# | `/api/products/{productId}/…` | `POST opening-stock`, `POST stock-adjustments`, `POST stock-adjustments/tracked-addition`, `…/tracked-removal`, `…/tracked-recovery`, `GET stock-movements`, `GET units/status?serialNumber=` |

# | `/api/suppliers` | `GET`, `GET /{id}`, `POST`, `PUT /{id}`, `PATCH /{id}/deactivate`, `PATCH /{id}/reactivate` |

# | `/api/purchases` | `GET`, `GET /{id}`, `POST`, `POST /{id}/items`, `DELETE /{id}/items/{itemId}`, `POST /{id}/confirm`, `POST /{id}/cancel` |

# | `/api/sales` | `GET`, `GET /{id}`, `POST`, `DELETE /{id}` (drafts), `POST /{id}/items`, `PUT /{id}/items/{itemId}`, `DELETE /{id}/items/{itemId}`, `POST /{id}/confirm`, `POST /{id}/handover`, `POST /{id}/cancel`, `GET /products/{productId}/available-serials` |

# | `/api/currencies` | `GET`, `POST` |

# | `/api/reports` | `inventory-valuation`, `low-stock`, `stock-availability`, `stock-movements`, `stock-movements/recent`, `sales-by-product`, `sales-by-category`, `gross-profit-by-product`, `daily-sales?from=\&to=`, `purchases-by-supplier`, `units/trace?serialNumber=` |

# &#x20;

# \### 2.3 Error handling

# &#x20;

# `GlobalExceptionHandler` returns a plain-text message with a status code:

# &#x20;

# | Exception | Status |

# | --- | --- |

# | `IllegalArgumentException` (invalid input) | `400` |

# | `IllegalStateException` (an action that is not allowed in the current state, such as insufficient stock or confirming a non-draft) | `409` |

# | `SaleCancellationNotAllowedException`, `PurchaseCancellationNotAllowedException` | `409` |

# | `CategoryNotFoundException` | `404` |

# | `CategoryAlreadyExistsException`, `CategoryInUseException` | `409` |

# | `ProductNotFoundException`, `SupplierNotFoundException` | `404` (via `@ResponseStatus`, without a message body) |

# | `ProductAlreadyExistsException` | `409` (via `@ResponseStatus`, without a message body) |

# &#x20;

# Request bodies that fail Bean Validation are rejected by Spring with `400` before reaching a service.

# &#x20;

# \## 3. Data model

# &#x20;

# `database/schema.sql` is the schema. Hibernate is set to `ddl-auto=none`, so entities never create or alter tables.

# &#x20;

# ```text

# categories ─< products ─< purchase\_items >─ purchases >─ suppliers

# &#x20;                │              │               └──────── currencies

# &#x20;                │              └─< product\_units >─< sale\_item\_units >─ sale\_items >─ sales >─ currencies

# &#x20;                └─< stock\_movements (links to product, unit, purchase item, sale item, or a reversed movement)

# &#x20;

# exchange\_rate\_cache   (one row per foreign currency, quoted in MAD)

# ```

# &#x20;

# Design rules in the schema:

# &#x20;

# \- \*\*Stock is derived.\*\* Quantity on hand is `SUM(quantity\_change)` and stock value is `SUM(value\_change\_mad)` over a product's `stock\_movements`. There is no stored quantity column to drift out of sync.

# \- \*\*Movements are append-only.\*\* A cancellation adds `REVERSAL` rows that point at the original movement (one reversal per movement, enforced by a unique key).

# \- \*\*Lifecycle checks.\*\* `CHECK` constraints tie each purchase and sale status to the timestamps and snapshots it requires (for example a confirmed sale must have a rate and a MAD total, and a cancelled sale cannot have a handover time).

# \- \*\*Snapshots.\*\* Purchases and sales store the exchange rate, its source, whether it came from the cache, and the MAD totals. Purchase items store the unit cost in MAD, and sale items store COGS and the category name at confirmation.

# \- \*\*Foreign keys use `ON DELETE RESTRICT`\*\*, so history cannot be removed by deleting a parent row.

# \- \*\*Serial units\*\* have a unique serial number, a status, an acquisition cost, and warranty dates that are filled when the sale is handed over.

# \## 4. Key workflows

# &#x20;

# \### 4.1 Confirming a purchase

# &#x20;

# ```text

# POST /api/purchases/{id}/confirm   (serial numbers per line)

# &#x20;└─ one transaction

# &#x20;    1. check the purchase is a draft with at least one item

# &#x20;    2. check serial counts, uniqueness, and that none already exist

# &#x20;    3. fetch the rate to MAD (live, else cached)

# &#x20;    4. per line: store MAD cost; create units + PURCHASE movements (serial-tracked)

# &#x20;       or one PURCHASE movement and a new average cost (quantity-tracked)

# &#x20;    5. mark the purchase CONFIRMED with the rate snapshot and MAD total

# ```

# &#x20;

# \### 4.2 Confirming a sale

# &#x20;

# ```text

# POST /api/sales/{id}/confirm   (selected serial numbers per line)

# &#x20;└─ one transaction

# &#x20;    1. check the sale is a draft with at least one item

# &#x20;    2. check each selected unit is IN\_STOCK, belongs to the product, and is chosen once

# &#x20;    3. check stock for every product (summed across lines)

# &#x20;    4. fetch the rate to MAD

# &#x20;    5. per line: take COGS from the unit costs or the weighted average,

# &#x20;       mark units SOLD, record SALE movements, copy the category name

# &#x20;    6. mark the sale CONFIRMED with the rate snapshot and MAD total

# ```

# &#x20;

# \### 4.3 Handover and cancellation

# &#x20;

# Handover sets `handed\_over\_at` and starts the warranty on every sold unit. Cancelling a sale is allowed only while `handed\_over\_at` is empty: it records `REVERSAL` movements and puts the units back in stock. Cancelling a purchase first checks that its stock can still be taken back (units unsold, no negative balance) and then voids the units.

# &#x20;

# \## 5. Transactions and consistency

# &#x20;

# \- Confirmation, cancellation and every stock adjustment run in a single `@Transactional` service method. A failure rolls the whole operation back, so stock, units and statuses never end up half-updated.

# \- The sale confirmation checks stock twice: when an item is added or changed, and again at confirmation, because nothing is reserved while a sale is a draft.

# \- Money is `BigDecimal` in Java and `DECIMAL` in MariaDB. Average costs use 6 decimal places; MAD amounts use 4.

# \- Spring's `open-in-view` is disabled, so entities are not loaded lazily during JSON rendering.

# \## 6. Exchange rates

# &#x20;

# `ExchangeRateService.getRateToMad(code)`:

# &#x20;

# 1\. `MAD` returns a rate of 1 with source `System`.

# 2\. Any other code calls `https://api.frankfurter.dev/v2/rate/{code}/MAD`. On success the rate is written to `exchange\_rate\_cache` and returned.

# 3\. If the call fails, the cached rate is returned and flagged as `fromCache`. If there is none, the operation fails with a conflict error.

# The rate is requested when a purchase or sale is \*\*confirmed\*\*, not when the draft is created, and it is then stored on the document.

# &#x20;

# \## 7. Frontend

# &#x20;

# The package is `com.electrostock.frontend`.

# &#x20;

# | Package | Contents |

# | --- | --- |

# | `api` | One HTTP client per area (`ProductApiClient`, `SaleApiClient`, `PurchaseApiClient`, `SupplierApiClient`, `InventoryReportClient`, `ReportsApiClient`). Calls return `CompletableFuture`, so the UI thread is never blocked. |

# | `view` | One view per screen: Dashboard, Products, Sales, Purchases, Suppliers, Stock, Reports, plus the sidebar and the dialogs for stock, sales and purchases. |

# | `resources/…/view` | One stylesheet per view (`dashboard.css`, `products.css`, and so on). |

# &#x20;

# \- `ElectroStockApp` is the entry point (`mvn javafx:run`). `AppSidebar` switches between views, grouped as Overview, Trade, Inventory and Insights.

# \- Views refresh their data when opened and show backend error messages as notices.

# \- Responses are updated on the JavaFX thread with `Platform.runLater`.

# \- The base URL (`http://localhost:8080/api/`) is written into each client class.

# \## 8. Configuration and deployment

# &#x20;

# `application.properties`:

# &#x20;

# | Property | Value |

# | --- | --- |

# | `spring.datasource.url` | `jdbc:mariadb://127.0.0.1:3307/electrostock` |

# | `spring.datasource.username` | `${DB\_USERNAME:root}` |

# | `spring.datasource.password` | `${DB\_PASSWORD}` |

# | `spring.jpa.hibernate.ddl-auto` | `none` |

# | `spring.jpa.open-in-view` | `false` |

# | `server.port` | `8080` |

# &#x20;

# The application is built for one machine: client, API and database all run locally. There is no authentication, so the API should not be exposed to a network. Moving the backend to another host would need the base URL made configurable in the client and some form of authentication added to the API.

# &#x20;

