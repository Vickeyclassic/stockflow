-- Original StockFlow V1 schema. Existing V1/V1.1 databases baseline at version 1.
-- Never edit an applied migration; add a new version instead.
CREATE TABLE app_users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role ENUM('ADMIN','STAFF') NOT NULL,
    CONSTRAINT uk_user_username UNIQUE (username)
);

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    CONSTRAINT uk_category_normalized_name UNIQUE (normalized_name)
);

CREATE TABLE suppliers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    name VARCHAR(150) NOT NULL,
    contact_person VARCHAR(100),
    email VARCHAR(254),
    phone VARCHAR(30),
    address VARCHAR(500)
);

CREATE TABLE customers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(30),
    address VARCHAR(500),
    active BIT NOT NULL
);

CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    category_id BIGINT NOT NULL,
    supplier_id BIGINT,
    cost_price DECIMAL(12,2) NOT NULL,
    selling_price DECIMAL(12,2) NOT NULL,
    quantity_in_stock INT NOT NULL,
    reorder_level INT NOT NULL,
    unit VARCHAR(30) NOT NULL,
    active BIT NOT NULL,
    CONSTRAINT uk_product_sku UNIQUE (sku),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id),
    CONSTRAINT fk_product_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT ck_product_values CHECK (cost_price >= 0 AND selling_price >= 0 AND quantity_in_stock >= 0 AND reorder_level >= 0)
);

CREATE TABLE inventory_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    product_sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    transaction_type ENUM('STOCK_IN','STOCK_OUT','ADJUSTMENT_IN','ADJUSTMENT_OUT') NOT NULL,
    quantity INT NOT NULL,
    previous_stock INT NOT NULL,
    new_stock INT NOT NULL,
    reference_type ENUM('MANUAL','OPENING_STOCK','LEGACY_ADJUSTMENT','PURCHASE_RECEIPT','SALES_ISSUE','SALES_ORDER','PURCHASE_ORDER') NOT NULL,
    reference_id VARCHAR(100),
    reason VARCHAR(250) NOT NULL,
    notes VARCHAR(2000),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_movement_values CHECK (quantity > 0 AND previous_stock >= 0 AND new_stock >= 0)
);
CREATE INDEX idx_movement_created ON inventory_transactions(created_at,id);
CREATE INDEX idx_movement_product ON inventory_transactions(product_id,created_at);

CREATE TABLE purchase_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(100) NOT NULL,
    supplier_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    status ENUM('DRAFT','ORDERED','RECEIVED','CANCELLED') NOT NULL,
    notes VARCHAR(2000),
    total_amount DECIMAL(30,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_purchase_order_number UNIQUE (order_number),
    CONSTRAINT fk_purchase_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE TABLE purchase_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    line_total DECIMAL(30,2) NOT NULL,
    CONSTRAINT fk_purchase_item_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_purchase_item_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE sales_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(100) NOT NULL,
    customer_id BIGINT NOT NULL,
    order_date DATE NOT NULL,
    status ENUM('DRAFT','CONFIRMED','FULFILLED','CANCELLED') NOT NULL,
    notes VARCHAR(2000),
    total_amount DECIMAL(30,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_sales_order_number UNIQUE (order_number),
    CONSTRAINT fk_sales_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE TABLE sales_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sales_order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    line_total DECIMAL(30,2) NOT NULL,
    CONSTRAINT fk_sales_item_order FOREIGN KEY (sales_order_id) REFERENCES sales_orders(id),
    CONSTRAINT fk_sales_item_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE stock_documents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kind ENUM('MANUAL','OPENING_STOCK','LEGACY_ADJUSTMENT','PURCHASE_RECEIPT','SALES_ISSUE','SALES_ORDER','PURCHASE_ORDER') NOT NULL,
    document_number VARCHAR(100) NOT NULL,
    document_date DATE NOT NULL,
    supplier_id BIGINT,
    supplier_name VARCHAR(150),
    notes VARCHAR(2000),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_document_number UNIQUE (kind,document_number),
    CONSTRAINT fk_document_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE TABLE stock_document_lines (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_sku VARCHAR(64) NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    CONSTRAINT fk_document_line_document FOREIGN KEY (document_id) REFERENCES stock_documents(id),
    CONSTRAINT fk_document_line_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT ck_document_line_values CHECK (quantity > 0 AND unit_price >= 0)
);
