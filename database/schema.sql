CREATE DATABASE IF NOT EXISTS nexo_estoque CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE nexo_estoque;

CREATE TABLE IF NOT EXISTS products (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sku VARCHAR(50) NOT NULL UNIQUE,
  barcode VARCHAR(32) NULL UNIQUE,
  name VARCHAR(160) NOT NULL,
  category VARCHAR(100) NOT NULL,
  cost_price DECIMAL(12,2) NOT NULL DEFAULT 0,
  sale_price DECIMAL(12,2) NOT NULL DEFAULT 0,
  current_stock DECIMAL(12,3) NOT NULL DEFAULT 0,
  minimum_stock DECIMAL(12,3) NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stock_batches (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  lot_code VARCHAR(80) NOT NULL,
  expires_at DATE NULL,
  quantity DECIMAL(12,3) NOT NULL DEFAULT 0,
  unit_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_batch_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT uk_product_lot UNIQUE (product_id, lot_code)
);

CREATE TABLE IF NOT EXISTS stock_movements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  batch_id BIGINT NULL,
  movement_type ENUM('ENTRY','EXIT','ADJUSTMENT','RETURN') NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  balance_before DECIMAL(12,3) NOT NULL,
  balance_after DECIMAL(12,3) NOT NULL,
  reason VARCHAR(255),
  idempotency_key VARCHAR(64) NULL,
  performed_by VARCHAR(120) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_movement_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_movement_batch FOREIGN KEY (batch_id) REFERENCES stock_batches(id),
  CONSTRAINT uk_stock_movements_idempotency UNIQUE (idempotency_key)
);



CREATE TABLE IF NOT EXISTS stock_movement_allocations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  movement_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_allocation_movement FOREIGN KEY (movement_id) REFERENCES stock_movements(id),
  CONSTRAINT fk_allocation_batch FOREIGN KEY (batch_id) REFERENCES stock_batches(id),
  CONSTRAINT uk_movement_batch UNIQUE (movement_id, batch_id)
);

CREATE TABLE IF NOT EXISTS blind_inventory_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  status ENUM('OPEN','CLOSED','CANCELLED') NOT NULL DEFAULT 'OPEN',
  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_at TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS blind_inventory_counts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  counted_quantity DECIMAL(12,3) NOT NULL,
  system_quantity_snapshot DECIMAL(12,3) NOT NULL,
  difference_quantity DECIMAL(12,3) NOT NULL,
  counted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_count_session FOREIGN KEY (session_id) REFERENCES blind_inventory_sessions(id),
  CONSTRAINT fk_count_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE IF NOT EXISTS decision_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NULL,
  decision_type VARCHAR(60) NOT NULL,
  input_snapshot JSON NOT NULL,
  output_snapshot JSON NOT NULL,
  rule_version VARCHAR(40) NOT NULL,
  actor_username VARCHAR(120) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_decision_product FOREIGN KEY (product_id) REFERENCES products(id)
);


CREATE TABLE IF NOT EXISTS product_change_history (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  action_type VARCHAR(30) NOT NULL,
  actor_username VARCHAR(120) NOT NULL,
  before_snapshot JSON NULL,
  after_snapshot JSON NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_product_history_product FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE INDEX idx_products_active_name ON products(active, name, id);
CREATE INDEX idx_products_category_active ON products(category, active, id);
CREATE INDEX idx_stock_batches_fefo ON stock_batches(product_id, quantity, expires_at, received_at, id);
CREATE INDEX idx_stock_movements_product_created ON stock_movements(product_id, created_at, id);
CREATE INDEX idx_stock_movements_created ON stock_movements(created_at, id);
CREATE INDEX idx_blind_inventory_status_started ON blind_inventory_sessions(status, started_at, id);
CREATE INDEX idx_decision_audit_type_created ON decision_audit(decision_type, created_at, id);
CREATE INDEX idx_decision_audit_product_created ON decision_audit(product_id, created_at, id);
CREATE INDEX idx_product_history_product_created ON product_change_history(product_id, created_at, id);


/* Purchasing and retention snapshot */
CREATE TABLE IF NOT EXISTS suppliers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(160) NOT NULL,
  tax_id VARCHAR(32) NULL UNIQUE,
  contact_name VARCHAR(120) NULL,
  email VARCHAR(160) NULL,
  phone VARCHAR(40) NULL,
  lead_time_days INT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS purchase_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  supplier_id BIGINT NOT NULL,
  status ENUM('DRAFT','SENT','PARTIALLY_RECEIVED','RECEIVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  source ENUM('MANUAL','REPLENISHMENT_RECOMMENDATION') NOT NULL DEFAULT 'MANUAL',
  rule_version VARCHAR(40) NULL,
  created_by VARCHAR(120) NOT NULL,
  expected_at DATE NULL,
  notes VARCHAR(255) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  sent_at TIMESTAMP NULL,
  received_at TIMESTAMP NULL,
  CONSTRAINT fk_purchase_order_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id)
);

CREATE TABLE IF NOT EXISTS purchase_order_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  purchase_order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  unit_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
  received_quantity DECIMAL(12,3) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_purchase_item_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
  CONSTRAINT fk_purchase_item_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT uk_purchase_order_product UNIQUE (purchase_order_id, product_id)
);

CREATE TABLE IF NOT EXISTS stock_movements_archive (
  original_movement_id BIGINT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  product_sku VARCHAR(50) NOT NULL,
  product_name VARCHAR(160) NOT NULL,
  batch_id BIGINT NULL,
  lot_code VARCHAR(80) NULL,
  movement_type VARCHAR(20) NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  balance_before DECIMAL(12,3) NOT NULL,
  balance_after DECIMAL(12,3) NOT NULL,
  reason VARCHAR(255) NULL,
  idempotency_key VARCHAR(64) NULL UNIQUE,
  performed_by VARCHAR(120) NULL,
  created_at TIMESTAMP NOT NULL,
  archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stock_movement_allocations_archive (
  original_allocation_id BIGINT PRIMARY KEY,
  original_movement_id BIGINT NOT NULL,
  original_batch_id BIGINT NOT NULL,
  lot_code VARCHAR(80) NOT NULL,
  expires_at DATE NULL,
  quantity DECIMAL(12,3) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_alloc_archive_movement(original_movement_id)
);

CREATE TABLE IF NOT EXISTS stock_retention_runs (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cutoff_at DATETIME NOT NULL,
  archived_movements INT NOT NULL,
  actor_username VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_suppliers_active_name
  ON suppliers(active, name, id);

CREATE INDEX idx_purchase_orders_supplier_status
  ON purchase_orders(supplier_id, status, created_at, id);

CREATE INDEX idx_purchase_orders_status_created
  ON purchase_orders(status, created_at, id);

CREATE INDEX idx_purchase_items_product
  ON purchase_order_items(product_id, purchase_order_id);

CREATE INDEX idx_stock_archive_product_created
  ON stock_movements_archive(product_id, created_at, original_movement_id);

CREATE INDEX idx_stock_archive_created
  ON stock_movements_archive(created_at, original_movement_id);


/* Warehouses, physical locations, receipts and transfers snapshot */
CREATE TABLE IF NOT EXISTS warehouses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  branch_name VARCHAR(120) NULL,
  address VARCHAR(255) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stock_locations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  warehouse_id BIGINT NOT NULL,
  code VARCHAR(60) NOT NULL,
  aisle VARCHAR(60) NULL,
  shelf VARCHAR(60) NULL,
  bin_code VARCHAR(60) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_stock_location_warehouse
    FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
  CONSTRAINT uk_stock_location_code
    UNIQUE (warehouse_id, code)
);

INSERT INTO warehouses(code, name, branch_name, address, active)
VALUES('MAIN', 'Depósito principal', 'Matriz', NULL, TRUE);

INSERT INTO stock_locations(warehouse_id, code, aisle, shelf, bin_code, active)
SELECT id, 'GERAL', 'Geral', NULL, NULL, TRUE
  FROM warehouses
 WHERE code = 'MAIN';

ALTER TABLE stock_batches
  ADD COLUMN location_id BIGINT NULL AFTER product_id;

UPDATE stock_batches
   SET location_id = (
     SELECT sl.id
       FROM stock_locations sl
       JOIN warehouses w ON w.id = sl.warehouse_id
      WHERE w.code = 'MAIN'
        AND sl.code = 'GERAL'
      LIMIT 1
   )
 WHERE location_id IS NULL;

ALTER TABLE stock_batches
  MODIFY location_id BIGINT NOT NULL,
  ADD CONSTRAINT fk_stock_batch_location
    FOREIGN KEY (location_id) REFERENCES stock_locations(id);

ALTER TABLE stock_batches
  DROP INDEX uk_product_lot,
  ADD CONSTRAINT uk_product_lot_location
    UNIQUE (product_id, lot_code, location_id);

CREATE TABLE IF NOT EXISTS purchase_receipts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  purchase_order_id BIGINT NOT NULL,
  location_id BIGINT NOT NULL,
  idempotency_key VARCHAR(64) NOT NULL UNIQUE,
  received_by VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_purchase_receipt_order
    FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
  CONSTRAINT fk_purchase_receipt_location
    FOREIGN KEY (location_id) REFERENCES stock_locations(id)
);

CREATE TABLE IF NOT EXISTS purchase_receipt_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  purchase_receipt_id BIGINT NOT NULL,
  purchase_order_item_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  stock_movement_id BIGINT NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  lot_code VARCHAR(80) NOT NULL,
  expires_at DATE NULL,
  unit_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_receipt_item_receipt
    FOREIGN KEY (purchase_receipt_id) REFERENCES purchase_receipts(id),
  CONSTRAINT fk_receipt_item_order_item
    FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(id),
  CONSTRAINT fk_receipt_item_batch
    FOREIGN KEY (batch_id) REFERENCES stock_batches(id),
  CONSTRAINT fk_receipt_item_movement
    FOREIGN KEY (stock_movement_id) REFERENCES stock_movements(id)
);

CREATE TABLE IF NOT EXISTS stock_transfers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  source_batch_id BIGINT NOT NULL,
  destination_batch_id BIGINT NOT NULL,
  source_location_id BIGINT NOT NULL,
  destination_location_id BIGINT NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  reason VARCHAR(255) NULL,
  idempotency_key VARCHAR(64) NOT NULL UNIQUE,
  performed_by VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_transfer_product
    FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_transfer_source_batch
    FOREIGN KEY (source_batch_id) REFERENCES stock_batches(id),
  CONSTRAINT fk_transfer_destination_batch
    FOREIGN KEY (destination_batch_id) REFERENCES stock_batches(id),
  CONSTRAINT fk_transfer_source_location
    FOREIGN KEY (source_location_id) REFERENCES stock_locations(id),
  CONSTRAINT fk_transfer_destination_location
    FOREIGN KEY (destination_location_id) REFERENCES stock_locations(id)
);

CREATE INDEX idx_locations_warehouse_active
  ON stock_locations(warehouse_id, active, code, id);

CREATE INDEX idx_batches_location_product
  ON stock_batches(location_id, product_id, quantity, id);

CREATE INDEX idx_receipts_order_created
  ON purchase_receipts(purchase_order_id, created_at, id);

CREATE INDEX idx_receipt_items_order_item
  ON purchase_receipt_items(purchase_order_item_id, created_at, id);

CREATE INDEX idx_transfers_product_created
  ON stock_transfers(product_id, created_at, id);

CREATE INDEX idx_transfers_locations_created
  ON stock_transfers(source_location_id, destination_location_id, created_at, id);

