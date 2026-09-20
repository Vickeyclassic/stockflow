-- Isolated migration-test data only; never part of production migrations.
INSERT INTO app_users (id,created_at,updated_at,version,username,password_hash,role)
VALUES (1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'migration.staff','not-a-usable-password-hash','STAFF');
INSERT INTO categories (id,created_at,updated_at,version,name,normalized_name) VALUES (1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'Keep category','keep category');
INSERT INTO suppliers (id,created_at,updated_at,version,name) VALUES (1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'Keep supplier');
INSERT INTO customers (id,created_at,updated_at,version,name,active) VALUES (1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'Keep customer',TRUE);
INSERT INTO products (id,created_at,updated_at,version,sku,name,category_id,supplier_id,cost_price,selling_price,quantity_in_stock,reorder_level,unit,active)
VALUES (1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,'KEEP-SKU','Keep product',1,1,2.50,4.00,10,2,'each',TRUE);
INSERT INTO inventory_transactions (id,product_id,product_sku,product_name,transaction_type,quantity,previous_stock,new_stock,reference_type,reason,created_at)
VALUES (1,1,'KEEP-SKU','Keep product','STOCK_IN',10,0,10,'OPENING_STOCK','Keep history',CURRENT_TIMESTAMP);
INSERT INTO purchase_orders (id,order_number,supplier_id,order_date,status,total_amount,created_at)
VALUES (1,'KEEP-PO',1,CURRENT_DATE,'DRAFT',5.00,CURRENT_TIMESTAMP);
INSERT INTO purchase_order_items (id,purchase_order_id,product_id,quantity,unit_price,line_total) VALUES (1,1,1,2,2.50,5.00);
INSERT INTO sales_orders (id,order_number,customer_id,order_date,status,total_amount,created_at)
VALUES (1,'KEEP-SO',1,CURRENT_DATE,'DRAFT',4.00,CURRENT_TIMESTAMP);
INSERT INTO sales_order_items (id,sales_order_id,product_id,quantity,unit_price,line_total) VALUES (1,1,1,1,4.00,4.00);
INSERT INTO stock_documents (id,kind,document_number,document_date,supplier_id,supplier_name,created_at)
VALUES (1,'PURCHASE_RECEIPT','KEEP-DOC',CURRENT_DATE,1,'Keep supplier',CURRENT_TIMESTAMP);
INSERT INTO stock_document_lines (id,document_id,product_id,product_sku,product_name,quantity,unit_price)
VALUES (1,1,1,'KEEP-SKU','Keep product',1,2.50);
