# \# ElectroStock — Functional Requirements

# 

# This document describes what ElectroStock does today. Each rule is enforced either by the backend services or by a database constraint in `database/schema.sql`. Section 12 lists the places where the application is looser than a rule you might expect.

# 

# \## 1. Overview

# 

# ElectroStock is an electronics inventory system for a \*\*single administrator\*\*. It has no login and no user accounts.

# 

# \- The base currency is the Moroccan Dirham (\*\*MAD\*\*). Every stock value, cost and profit is stored in MAD.

# \- Purchases and sales can be made in other currencies. They are converted to MAD at confirmation.

# \- Stock is not stored as a number. It is always the sum of the stock movements recorded for a product.

# 

# \## 2. Categories

# 

# \- Create, rename, list and delete categories. Each has a name (up to 100 characters, unique) and an optional description.

# \- A category cannot be deleted while any product uses it.

# \- The Products screen has a \*\*Categories\*\* dialog for this, and a \*\*+ New category\*\* button inside the product form.

# 

# \## 3. Product Management

# 

# \- Create, view, edit, search, deactivate and reactivate products.

# \- Each product belongs to one category and has a unique \*\*SKU\*\* (up to 80 characters), a name (up to 200), an optional description, a selling price in MAD, a low-stock threshold and a warranty length in months.

# \- A product is either \*\*serial-tracked\*\* or \*\*quantity-tracked\*\*. This is chosen when the product is created and cannot be changed afterwards (the edit screen only changes SKU, name, description and price).

# \- Deactivating a product keeps its history. An inactive product cannot receive opening stock or stock adjustments, and can be reactivated at any time.

# \- The product list can be filtered by search text, category, stock level (in stock, low stock, out of stock) and status. A product is \*\*low stock\*\* when its quantity is above 0 and at or below its threshold.

# 

# \## 4. Supplier Management

# 

# \- Register, edit, list, deactivate and reactivate suppliers: name (required), phone, email and address.

# \- Deactivating a supplier keeps its purchase history.

# 

# \## 5. Purchases

# 

# A purchase moves through three states: \*\*DRAFT → CONFIRMED → CANCELLED\*\*.

# 

# \- \*\*Draft.\*\* A draft is created with a supplier, a currency and a date. Items (product, quantity, unit price in the purchase currency) can be added and removed while it is a draft. Quantity and unit price must be positive and non-negative respectively.

# \- \*\*Confirm.\*\* A draft needs at least one item. For every serial-tracked line the administrator must supply exactly one serial number per unit. Serials must be unique within the purchase and must not already exist in the database. On confirmation:

# &#x20; - the exchange rate to MAD is fetched and stored with the purchase;

# &#x20; - each line's cost in MAD is calculated and stored;

# &#x20; - stock movements of type `PURCHASE` are created, one per unit for serial-tracked products and one per line otherwise;

# &#x20; - the product's average cost is recalculated for quantity-tracked products.

# \- \*\*Cancel.\*\* Only a confirmed purchase can be cancelled. The cancellation is refused with a conflict error if

# &#x20; - any received serial unit is no longer `IN\_STOCK` (for example it has been sold), or

# &#x20; - reversing the receipt would leave a product with negative stock or an inconsistent stock value.

# 

# &#x20; Otherwise `REVERSAL` movements are recorded, the received units become `VOIDED`, and average costs are recalculated.

# \- A confirmed or cancelled purchase cannot be edited. A draft purchase cannot be deleted.

# 

# \## 6. Sales

# 

# A sale moves through \*\*DRAFT → CONFIRMED → CANCELLED\*\*. Handover is recorded separately as a timestamp on a confirmed sale.

# 

# \- \*\*Draft.\*\* A draft has a currency and a date. Items (product, quantity, unit price) can be added, replaced and removed, and the whole draft can be discarded. Stock is checked each time an item is added or changed, counting the product's quantity across all lines of the same sale. Nothing is reserved while a sale is a draft.

# \- \*\*Confirm.\*\* A draft needs at least one item. For each serial-tracked line the administrator picks exactly as many in-stock serial numbers as the quantity. Stock is checked again at this point. On confirmation:

# &#x20; - the exchange rate to MAD is fetched and stored with the sale;

# &#x20; - the \*\*cost of goods sold\*\* (COGS) is captured for each line in MAD, using the units' own cost for serial-tracked products and the weighted average for the others;

# &#x20; - the product's category name is copied onto the line;

# &#x20; - selected units become `SOLD`, and `SALE` movements are recorded.

# \- \*\*Hand over.\*\* A confirmed sale can be marked as handed over once. For each serial unit sold, the warranty starts on the handover date and ends after the product's warranty months. Products with 0 warranty months get no warranty dates.

# \- \*\*Cancel.\*\* A confirmed sale can be cancelled only \*\*before handover\*\*. Cancelling records `REVERSAL` movements and returns the serial units to stock. A cancelled or handed-over sale cannot be changed.

# 

# \## 7. Stock Management

# 

# All stock changes are rows in `stock\_movements`. Rows are never edited or deleted; corrections are new `REVERSAL` or `ADJUSTMENT` rows.

# 

# | Movement type | Created by |

# | --- | --- |

# | `OPENING\_STOCK` | Recording opening stock for an active product (quantity and unit cost in MAD; serial numbers required for serial-tracked products) |

# | `PURCHASE` | Confirming a purchase |

# | `SALE` | Confirming a sale |

# | `ADJUSTMENT` | Manual stock adjustments (below) |

# | `REVERSAL` | Cancelling a purchase or a sale |

# 

# \*\*Adjustments\*\* need an active product and a reason: `DAMAGED`, `MISSING`, `COUNTING\_CORRECTION` or `OTHER`. The database requires an explanation when the reason is `OTHER`.

# 

# \- \*Quantity-tracked products:\* add or remove a quantity. Adding needs a unit cost. Removing must not include a cost, is valued at the current average cost, and cannot remove more than is in stock.

# \- \*Serial-tracked products:\* remove a unit by serial number (`MISSING` marks it `LOST`, any other reason marks it `VOIDED`), add a unit with a new serial number and cost, or \*\*recover\*\* a `LOST` unit back into stock.

# 

# Stock cannot go below zero. A product's movement history can be viewed from the Stock screen, which can also export to CSV.

# 

# \## 8. Serial Tracking and Warranties

# 

# \- Each serial number is unique across the whole database (up to 150 characters).

# \- A unit has a status: `IN\_STOCK`, `SOLD`, `UNDER\_REPAIR`, `LOST` or `VOIDED`. The application currently sets every status except `UNDER\_REPAIR`.

# \- Each unit stores its acquisition cost in MAD.

# \- The sale-to-unit link is kept permanently. A serial number can be traced to see its status, warranty dates and full movement history (Reports → Serial trace).

# \- A unit cannot go back to stock after a cancelled sale if its warranty has already started.

# 

# \## 9. Currencies and Exchange Rates

# 

# \- MAD, EUR and USD are created with the schema. More currencies can be added through the API, which first checks that a rate is available for the new code.

# \- Rates come from the \[Frankfurter](https://frankfurter.dev) API when a purchase or sale is confirmed.

# \- Every rate fetched is saved in a cache table (one row per currency). If the API cannot be reached, the cached rate is used and the transaction is flagged as `rate\_from\_cache`. If there is no cached rate either, confirmation fails.

# \- The rate, its source, the time it was retrieved and the cache flag are stored on the purchase or sale, so later rate changes never alter past figures. MAD transactions always use a rate of 1.

# \- Manual rates and rate overrides are not supported.

# 

# \## 10. Costing and Profit

# 

# \- \*\*Quantity-tracked products:\*\* moving weighted average. Average cost = total stock value ÷ total quantity, rounded to 6 decimal places.

# \- \*\*Serial-tracked products:\*\* specific identification. Each unit keeps its own cost.

# \- COGS is stored on each sale line at confirmation. \*\*Gross profit\*\* = sale total in MAD − COGS. Operating expenses are not included.

# \- All money uses `BigDecimal` in Java and `DECIMAL` columns in the database.

# 

# \## 11. Dashboard and Reports

# 

# \*\*Dashboard:\*\* revenue, gross profit and units sold for the current month or today (with a daily bar chart); stock at cost, potential sales and potential profit with a ranking of products by potential profit or loss; a stock availability donut (in stock, low, out of stock); and the five most recent stock movements (movements of the same product and type within five seconds are shown as one row).

# 

# \*\*Reports\*\* (viewed as a table or a chart, and exported to CSV from the table):

# 

# \- Inventory valuation

# \- Low stock (active products whose quantity is at or below their threshold, including those out of stock)

# \- Stock movements, for a date range

# \- Purchases by supplier (confirmed purchases only)

# \- Sales by product

# \- Sales by category (using the category name stored on each sale line)

# \- Gross profit by product

# \- Daily sales, for a date range

# \- Serial trace, for one serial number

# 

# \## 12. Current Limitations

# 

# These are gaps between the rules above and what the code enforces today.

# 

# \- \*\*Inactive suppliers and products are not blocked in trade.\*\* An inactive supplier can still be chosen for a new purchase, and an inactive product can still be added to a purchase or sale draft. Only opening stock and stock adjustments check that a product is active.

# \- \*\*No authentication.\*\* The API is open. It is meant to run on the administrator's own computer.

# \- \*\*Fixed server address.\*\* The desktop client always connects to `http://localhost:8080`.

# \- \*\*Recent activity is limited to five raw movements.\*\* The dashboard asks the backend for the last five movements and merges them afterwards, so a multi-unit purchase can leave fewer than five rows.

# \- \*\*Draft purchases cannot be discarded\*\*, unlike draft sales.

# 

# \## 13. Out of Scope

# 

# \- Multiple users, roles and logins

# \- Customer management, returns and refunds

# \- Partial sale handover

# \- Operating-expense accounting

# \- Landed-cost allocation (freight, customs)

# \- Manual exchange rates and rate overrides

