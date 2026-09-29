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
