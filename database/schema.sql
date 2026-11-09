
-- ======================================================
-- ELECTROSTOCK DATABASE SCHEMA - VERSION 1
-- Target: MariaDB 10.4+
-- ======================================================

CREATE DATABASE IF NOT EXISTS electrostock
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE electrostock;

-- ======================================================
-- 1. CATEGORIES
-- ======================================================

CREATE TABLE categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT
) ENGINE=InnoDB;


-- ======================================================
-- 2. SUPPLIERS
-- ======================================================

CREATE TABLE suppliers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(254),
    address TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT chk_supplier_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB;


-- ======================================================
-- 3. CURRENCIES
-- ======================================================

CREATE TABLE currencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code CHAR(3) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    symbol VARCHAR(10)
) ENGINE=InnoDB;


-- ======================================================
-- 4. PRODUCTS
-- ======================================================

CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT NOT NULL,
    sku VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    tracks_serials BOOLEAN NOT NULL DEFAULT FALSE,
    selling_price_mad DECIMAL(19,4) NOT NULL DEFAULT 0,
    average_cost_mad DECIMAL(19,6),
    low_stock_threshold INT NOT NULL DEFAULT 0,
    warranty_months INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    INDEX idx_products_category (category_id),
    INDEX idx_products_status (status),

    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id)
        REFERENCES categories(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_product_price
        CHECK (selling_price_mad >= 0),

    CONSTRAINT chk_product_average_cost
        CHECK (average_cost_mad IS NULL
               OR average_cost_mad >= 0),

    CONSTRAINT chk_product_threshold
        CHECK (low_stock_threshold >= 0),

    CONSTRAINT chk_product_warranty
        CHECK (warranty_months >= 0),

    CONSTRAINT chk_product_tracking
        CHECK (tracks_serials IN (0, 1)),

    CONSTRAINT chk_product_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB;


-- ======================================================
-- 5. EXCHANGE RATES
-- ======================================================

CREATE TABLE exchange_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    currency_id BIGINT NOT NULL,
    rate_to_mad DECIMAL(20,10) NOT NULL,
    effective_at DATETIME NOT NULL,
    retrieved_at DATETIME,
    provider_name VARCHAR(100) NOT NULL,
    manually_overridden BOOLEAN NOT NULL DEFAULT FALSE,

    INDEX idx_rates_currency_time
        (currency_id, effective_at),

    CONSTRAINT fk_rates_currency
        FOREIGN KEY (currency_id)
        REFERENCES currencies(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_rate_positive
        CHECK (rate_to_mad > 0),

    CONSTRAINT chk_rate_override
        CHECK (manually_overridden IN (0, 1)),

    CONSTRAINT chk_rate_source_time
        CHECK (retrieved_at IS NOT NULL
               OR manually_overridden = 1)
) ENGINE=InnoDB;


-- ======================================================
-- 6. PURCHASES
-- ======================================================

CREATE TABLE purchases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    supplier_id BIGINT NOT NULL,
    currency_id BIGINT NOT NULL,
    purchase_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    rate_to_mad_snapshot DECIMAL(20,10),
    rate_source_snapshot VARCHAR(100),
    rate_retrieved_at_snapshot DATETIME,
    rate_overridden_snapshot BOOLEAN NOT NULL DEFAULT FALSE,

    total_currency DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_mad DECIMAL(19,4),

    confirmed_at DATETIME,
    cancelled_at DATETIME,

    INDEX idx_purchases_supplier (supplier_id),
    INDEX idx_purchases_currency (currency_id),
    INDEX idx_purchases_status_date (status, purchase_date),

    CONSTRAINT fk_purchases_supplier
        FOREIGN KEY (supplier_id)
        REFERENCES suppliers(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_purchases_currency
        FOREIGN KEY (currency_id)
        REFERENCES currencies(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_purchase_status
        CHECK (status IN (
            'DRAFT', 'CONFIRMED', 'CANCELLED'
        )),

    CONSTRAINT chk_purchase_total
        CHECK (total_currency >= 0
               AND (total_mad IS NULL OR total_mad >= 0)),

    CONSTRAINT chk_purchase_rate
        CHECK (rate_to_mad_snapshot IS NULL
               OR rate_to_mad_snapshot > 0),

    CONSTRAINT chk_purchase_override
        CHECK (rate_overridden_snapshot IN (0, 1)),

    CONSTRAINT chk_purchase_lifecycle CHECK (
        (status = 'DRAFT'
         AND confirmed_at IS NULL
         AND cancelled_at IS NULL)

        OR

        (status = 'CONFIRMED'
         AND confirmed_at IS NOT NULL
         AND cancelled_at IS NULL
         AND rate_to_mad_snapshot IS NOT NULL
         AND total_mad IS NOT NULL)

        OR

        (status = 'CANCELLED'
         AND confirmed_at IS NOT NULL
         AND cancelled_at IS NOT NULL
         AND rate_to_mad_snapshot IS NOT NULL
         AND total_mad IS NOT NULL)
    )
) ENGINE=InnoDB;


-- ======================================================
-- 7. PURCHASE ITEMS
-- ======================================================

CREATE TABLE purchase_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    purchase_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price_currency DECIMAL(19,4) NOT NULL,
    unit_cost_mad_snapshot DECIMAL(19,6),
    line_total_currency DECIMAL(19,4) NOT NULL,
    line_total_mad DECIMAL(19,4),

    INDEX idx_purchase_items_purchase (purchase_id),
    INDEX idx_purchase_items_product (product_id),

    CONSTRAINT fk_purchase_items_purchase
        FOREIGN KEY (purchase_id)
        REFERENCES purchases(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_purchase_items_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_purchase_item_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_purchase_item_values
        CHECK (
            unit_price_currency >= 0
            AND line_total_currency >= 0
            AND (unit_cost_mad_snapshot IS NULL
                 OR unit_cost_mad_snapshot >= 0)
            AND (line_total_mad IS NULL
                 OR line_total_mad >= 0)
        )
) ENGINE=InnoDB;


-- ======================================================
-- 8. PRODUCT UNITS (SERIAL TRACKING)
-- ======================================================

CREATE TABLE product_units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    purchase_item_id BIGINT,
    serial_number VARCHAR(150) NOT NULL UNIQUE,
    acquisition_cost_mad DECIMAL(19,4) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'IN_STOCK',
    warranty_start DATE,
    warranty_end DATE,

    INDEX idx_product_units_product (product_id),
    INDEX idx_product_units_purchase_item (purchase_item_id),
    INDEX idx_product_units_status (status),

    CONSTRAINT fk_units_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_units_purchase_item
        FOREIGN KEY (purchase_item_id)
        REFERENCES purchase_items(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_unit_cost
        CHECK (acquisition_cost_mad >= 0),

    CONSTRAINT chk_unit_status
        CHECK (status IN (
            'IN_STOCK',
            'SOLD',
            'UNDER_REPAIR',
            'LOST',
            'VOIDED'
        )),

    CONSTRAINT chk_unit_warranty
        CHECK (
            warranty_start IS NULL
            OR warranty_end IS NULL
            OR warranty_end >= warranty_start
        )
) ENGINE=InnoDB;


-- ======================================================
-- 9. SALES
-- ======================================================

CREATE TABLE sales (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    currency_id BIGINT NOT NULL,
    sale_date DATE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    rate_to_mad_snapshot DECIMAL(20,10),
    rate_source_snapshot VARCHAR(100),
    rate_retrieved_at_snapshot DATETIME,
    rate_overridden_snapshot BOOLEAN NOT NULL DEFAULT FALSE,

    total_currency DECIMAL(19,4) NOT NULL DEFAULT 0,
    total_mad DECIMAL(19,4),

    confirmed_at DATETIME,
    handed_over_at DATETIME,
    cancelled_at DATETIME,

    INDEX idx_sales_currency (currency_id),
    INDEX idx_sales_status_date (status, sale_date),

    CONSTRAINT fk_sales_currency
        FOREIGN KEY (currency_id)
        REFERENCES currencies(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_sale_status
        CHECK (status IN (
            'DRAFT', 'CONFIRMED', 'CANCELLED'
        )),

    CONSTRAINT chk_sale_total
        CHECK (total_currency >= 0
               AND (total_mad IS NULL OR total_mad >= 0)),

    CONSTRAINT chk_sale_rate
        CHECK (rate_to_mad_snapshot IS NULL
               OR rate_to_mad_snapshot > 0),

    CONSTRAINT chk_sale_override
        CHECK (rate_overridden_snapshot IN (0, 1)),

    CONSTRAINT chk_sale_lifecycle CHECK (
        (status = 'DRAFT'
         AND confirmed_at IS NULL
         AND cancelled_at IS NULL
         AND handed_over_at IS NULL)

        OR

        (status = 'CONFIRMED'
         AND confirmed_at IS NOT NULL
         AND cancelled_at IS NULL
         AND rate_to_mad_snapshot IS NOT NULL
         AND total_mad IS NOT NULL)

        OR

        (status = 'CANCELLED'
         AND confirmed_at IS NOT NULL
         AND cancelled_at IS NOT NULL
         AND handed_over_at IS NULL
         AND rate_to_mad_snapshot IS NOT NULL
         AND total_mad IS NOT NULL)
    )
) ENGINE=InnoDB;


-- ======================================================
-- 10. SALE ITEMS
-- ======================================================

CREATE TABLE sale_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price_currency DECIMAL(19,4) NOT NULL,
    line_total_currency DECIMAL(19,4) NOT NULL,
    line_total_mad DECIMAL(19,4),
    cost_of_goods_sold_mad DECIMAL(19,4),

    INDEX idx_sale_items_sale (sale_id),
    INDEX idx_sale_items_product (product_id),

    CONSTRAINT fk_sale_items_sale
        FOREIGN KEY (sale_id)
        REFERENCES sales(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_sale_items_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_sale_item_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_sale_item_values
        CHECK (
            unit_price_currency >= 0
            AND line_total_currency >= 0
            AND (line_total_mad IS NULL
                 OR line_total_mad >= 0)
            AND (cost_of_goods_sold_mad IS NULL
                 OR cost_of_goods_sold_mad >= 0)
        )
) ENGINE=InnoDB;


-- ======================================================
-- 11. SALE ITEM UNITS
-- ======================================================

CREATE TABLE sale_item_units (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_item_id BIGINT NOT NULL,
    product_unit_id BIGINT NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uq_sale_item_unit
        (sale_item_id, product_unit_id),

    INDEX idx_sale_item_units_unit (product_unit_id),

    CONSTRAINT fk_siu_sale_item
        FOREIGN KEY (sale_item_id)
        REFERENCES sale_items(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_siu_product_unit
        FOREIGN KEY (product_unit_id)
        REFERENCES product_units(id)
        ON DELETE RESTRICT
) ENGINE=InnoDB;


-- ======================================================
-- 12. STOCK MOVEMENTS
-- ======================================================

CREATE TABLE stock_movements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    product_unit_id BIGINT,
    purchase_item_id BIGINT,
    sale_item_id BIGINT,
    reversal_of_movement_id BIGINT,

    movement_type VARCHAR(25) NOT NULL,
    quantity_change INT NOT NULL,
    value_change_mad DECIMAL(19,4) NOT NULL,

    adjustment_reason VARCHAR(30),
    explanation TEXT,
    movement_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_movements_product_date
        (product_id, movement_at),

    INDEX idx_movements_unit (product_unit_id),
    INDEX idx_movements_purchase_item (purchase_item_id),
    INDEX idx_movements_sale_item (sale_item_id),

    UNIQUE KEY uq_movement_reversal
        (reversal_of_movement_id),

    CONSTRAINT fk_movements_product
        FOREIGN KEY (product_id)
        REFERENCES products(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_movements_unit
        FOREIGN KEY (product_unit_id)
        REFERENCES product_units(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_movements_purchase_item
        FOREIGN KEY (purchase_item_id)
        REFERENCES purchase_items(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_movements_sale_item
        FOREIGN KEY (sale_item_id)
        REFERENCES sale_items(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_movements_reversal
        FOREIGN KEY (reversal_of_movement_id)
        REFERENCES stock_movements(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_movement_type CHECK (
        movement_type IN (
            'OPENING_STOCK',
            'PURCHASE',
            'SALE',
            'ADJUSTMENT',
            'REVERSAL'
        )
    ),

    CONSTRAINT chk_movement_quantity
        CHECK (quantity_change <> 0),

    CONSTRAINT chk_movement_unit_quantity
        CHECK (
            product_unit_id IS NULL
            OR quantity_change IN (-1, 1)
        ),

    CONSTRAINT chk_movement_source CHECK (
        (
            movement_type = 'PURCHASE'
            AND purchase_item_id IS NOT NULL
            AND sale_item_id IS NULL
            AND reversal_of_movement_id IS NULL
        )
        OR
        (
            movement_type = 'SALE'
            AND sale_item_id IS NOT NULL
            AND purchase_item_id IS NULL
            AND reversal_of_movement_id IS NULL
        )
        OR
        (
            movement_type IN ('OPENING_STOCK', 'ADJUSTMENT')
            AND purchase_item_id IS NULL
            AND sale_item_id IS NULL
            AND reversal_of_movement_id IS NULL
        )
        OR
        (
            movement_type = 'REVERSAL'
            AND reversal_of_movement_id IS NOT NULL
            AND purchase_item_id IS NULL
            AND sale_item_id IS NULL
        )
    ),

    CONSTRAINT chk_adjustment_reason CHECK (
        (
            movement_type = 'ADJUSTMENT'
            AND adjustment_reason IN (
                'DAMAGED',
                'MISSING',
                'COUNTING_CORRECTION',
                'OTHER'
            )
            AND (
                adjustment_reason <> 'OTHER'
                OR (
                    explanation IS NOT NULL
                    AND CHAR_LENGTH(TRIM(explanation)) > 0
                )
            )
        )
        OR
        (
            movement_type <> 'ADJUSTMENT'
            AND adjustment_reason IS NULL
        )
    )
) ENGINE=InnoDB;


-- ======================================================
-- INITIAL CURRENCY
-- ======================================================

INSERT INTO currencies (code, name, symbol)
VALUES ('MAD', 'Moroccan Dirham', 'DH');

-- ======================================================
-- END OF SCHEMA
-- ======================================================
