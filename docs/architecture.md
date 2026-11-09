# ElectroStock — Technical Architecture

## 1. Overview

ElectroStock follows a client-server architecture.

The JavaFX desktop application communicates with a Spring Boot REST API. The backend owns the business logic and accesses MariaDB.

## 2. Layers

### JavaFX Desktop

Responsibilities:

- Display application screens
- Collect administrator input
- Perform basic user-interface validation
- Send requests to the backend
- Display results and errors

### REST Controllers

Responsibilities:

- Expose HTTP endpoints
- Receive and validate request data
- Call application services
- Return response DTOs

### Service Layer

Planned services:

- ProductService
- CategoryService
- SupplierService
- PurchaseService
- SaleService
- StockManagementService
- CostingService
- ExchangeRateService
- ReportService

Responsibilities:

- Enforce business rules
- Coordinate repositories
- Manage database transactions
- Maintain inventory and financial consistency

### Repository Layer

Responsibilities:

- Read and write database records
- Query products, suppliers, transactions, and stock movements
- Support transactional database operations

The persistence implementation (Spring Data JPA or JDBC) will be selected during backend setup.

### MariaDB

Stores:

- Product catalog
- Suppliers
- Purchases and purchase items
- Sales and sale items
- Serial-numbered units
- Inventory movements
- Currencies and exchange rates

### External Exchange-Rate Provider

The ExchangeRateService communicates with an external provider and maintains cached rates for fallback.

The provider has not yet been selected.

## 3. Transaction Integrity

Purchase confirmation, sale confirmation, cancellation, and stock adjustments must execute atomically.

Failed operations must not leave partially updated stock quantities, unit statuses, or transaction records.

## 4. Financial Calculations

Use Java BigDecimal for financial calculations and database DECIMAL types for persistence.

Store transaction exchange-rate snapshots and historical cost of goods sold so later changes do not alter historical reports.

## 5. Deployment

Initial development is local, with JavaFX, Spring Boot, and MariaDB running on the developer's computer.

The architecture separates the desktop client and backend to support future remote deployment.
