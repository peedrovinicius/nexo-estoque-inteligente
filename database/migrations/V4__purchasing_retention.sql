CREATE TABLE suppliers (
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

CREATE TABLE purchase_orders (
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

CREATE TABLE purchase_order_items (
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

CREATE TABLE stock_movements_archive (
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

CREATE TABLE stock_movement_allocations_archive (
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

CREATE TABLE stock_retention_runs (
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
