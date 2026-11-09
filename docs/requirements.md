\# ElectroStock — Functional Requirements



\## 1. Overview



ElectroStock is an electronics inventory management system intended initially for a single administrator.



The application's base currency is Moroccan Dirham (MAD).



\## 2. Product Management



\- Create, view, update, search, and deactivate products.

\- Organize products into categories.

\- Assign unique SKUs.

\- Support both serial-tracked and quantity-tracked products.

\- Preserve products with transaction history.

\- Allow inactive products to be reactivated.

\- Prevent normal stock operations involving inactive products.



\## 3. Supplier Management



\- Register and edit suppliers.

\- Activate and deactivate suppliers.

\- Preserve purchase history.

\- Prevent inactive suppliers from being used for new purchases.

\- Revalidate supplier status when confirming purchase drafts.



\## 4. Purchase Management



\- Create and modify draft purchases.

\- Associate a purchase with a supplier.

\- Add products and quantities.

\- Support purchases in different currencies.

\- Confirm purchases and increase inventory atomically.

\- Preserve confirmed purchase history.

\- Allow cancellation only when purchased stock remains eligible for reversal.

\- Prevent direct modification of confirmed purchases.



\## 5. Sales Management



\- Create and edit draft sales.

\- Verify stock before confirmation.

\- Decrease stock when confirming sales.

\- Record cost of goods sold when confirming sales.

\- Record which serial-numbered units were sold.

\- Allow cancellation before product handover.

\- Record handover of the complete sale.

\- Prohibit cancellation after handover.



\## 6. Stock Management



\- Maintain a history of stock movements.

\- Record opening stock explicitly.

\- Record purchases, sales, adjustments, and reversals.

\- Prevent negative available stock.

\- Require adjustment reasons: DAMAGED, MISSING, COUNTING\_CORRECTION, or OTHER.

\- Require an explanation when OTHER is selected.

\- Preserve historical movements instead of deleting them.



\## 7. Serial Tracking and Warranties



\- Track selected electronics individually using unique serial numbers.

\- Associate individual units with products.

\- Store each serial unit's acquisition cost.

\- Track warranty dates.

\- Preserve historical sale-to-unit associations.



\## 8. Multi-Currency Support



\- Use MAD as the base currency.

\- Support transactions in other currencies.

\- Retrieve exchange rates automatically when available.

\- Cache retrieved exchange rates.

\- Use the latest cached rate with a warning when the provider is unavailable.

\- Support administrator rate overrides.

\- Preserve the exact rate used when a transaction is confirmed.



\## 9. Inventory Costing and Reports



\- Use moving weighted-average costing for non-serial products.

\- Use specific identification for serial-tracked products.

\- Capture cost of goods sold at sale confirmation.

\- Generate stock and low-stock reports.

\- Generate purchase and sales reports.

\- Calculate gross profit, excluding operating expenses.



\## 10. Scope Exclusions



The initial MVP does not include:



\- Multiple administrator or employee accounts

\- Customer management

\- Customer returns and refunds

\- Partial sale handover

\- Operating expense accounting

\- Landed-cost allocation



These may be considered in future versions.

