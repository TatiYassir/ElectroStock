# ElectroStock

ElectroStock is a desktop inventory management application designed for small and medium-sized electronics businesses.

The application manages products, suppliers, purchases, sales, stock movements, serial-numbered devices, currencies, and business reports.

## Technologies

- **Java** — Application development
- **JavaFX** — Desktop user interface
- **Spring Boot** — Backend REST API and business logic
- **MariaDB** — Relational database
- **Maven** — Dependency management and build automation
- **Git & GitHub** — Version control

## Planned Features

- Product and category management
- Supplier management
- Purchase and sales management
- Stock tracking and adjustments
- Selective serial-number tracking
- Multi-currency transactions with MAD as the base currency
- Automatic exchange-rate retrieval with cached fallback
- Inventory valuation and gross profit reports
- Product warranty tracking

## Architecture

ElectroStock uses a layered architecture:

JavaFX Desktop → REST API → Spring Boot Services → Repositories → MariaDB

The backend is responsible for business rules, validation, transactions, and database operations.

## Project Structure

```text
ElectroStock/
├── backend/             # Spring Boot (planned)
├── desktop/             # JavaFX (planned)
├── database/            # SQL schema
├── docs/                # Requirements and architecture
├── .gitignore
└── README.md
```

## Project Status

**Design phase — database schema not yet validated.**

The project requirements, domain model, architecture, and initial relational schema have been drafted.

Implementation has not started yet.

## Roadmap

1. Finalize and validate the database schema.
2. Set up the Spring Boot backend.
3. Implement entities, repositories, and services.
4. Develop the JavaFX desktop interface.
5. Test application workflows and business rules.
6. Prepare the application for future deployment.
