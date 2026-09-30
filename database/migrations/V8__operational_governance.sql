CREATE TABLE product_replenishment_policies (
  product_id BIGINT PRIMARY KEY,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  target_coverage_days INT NOT NULL DEFAULT 14,
  safety_stock_multiplier DECIMAL(6,3) NOT NULL DEFAULT 1.000,
  minimum_order_quantity DECIMAL(12,3) NOT NULL DEFAULT 1.000,
  order_multiple DECIMAL(12,3) NOT NULL DEFAULT 1.000,
  preferred_supplier_id BIGINT NULL,
  updated_by VARCHAR(120) NOT NULL DEFAULT 'system',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_replenishment_policy_product
    FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT fk_replenishment_policy_supplier
    FOREIGN KEY (preferred_supplier_id) REFERENCES suppliers(id),
  CONSTRAINT chk_policy_target_days CHECK (target_coverage_days BETWEEN 1 AND 365),
  CONSTRAINT chk_policy_safety_multiplier CHECK (safety_stock_multiplier BETWEEN 0 AND 10),
  CONSTRAINT chk_policy_min_order CHECK (minimum_order_quantity > 0),
  CONSTRAINT chk_policy_order_multiple CHECK (order_multiple > 0)
);

CREATE TABLE operational_exceptions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  exception_type ENUM('REPLENISHMENT_PAUSE','COUNTING_PAUSE') NOT NULL,
  reason VARCHAR(255) NOT NULL,
  status ENUM('ACTIVE','CANCELLED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
  starts_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  created_by VARCHAR(120) NOT NULL,
  cancelled_by VARCHAR(120) NULL,
  cancelled_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_operational_exception_product
    FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT chk_operational_exception_window CHECK (expires_at > starts_at)
);

CREATE TABLE operational_action_states (
  action_key VARCHAR(180) PRIMARY KEY,
  action_type VARCHAR(60) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  title_snapshot VARCHAR(255) NOT NULL,
  description_snapshot VARCHAR(255) NOT NULL,
  value_snapshot VARCHAR(120) NOT NULL,
  action_target VARCHAR(80) NOT NULL,
  status ENUM('OPEN','ACKNOWLEDGED','RESOLVED') NOT NULL DEFAULT 'OPEN',
  first_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged_at TIMESTAMP NULL,
  acknowledged_by VARCHAR(120) NULL,
  acknowledgement_note VARCHAR(255) NULL,
  resolved_at TIMESTAMP NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_operational_exceptions_product_status
  ON operational_exceptions(product_id, status, expires_at, id);

CREATE INDEX idx_operational_exceptions_status_expiry
  ON operational_exceptions(status, expires_at, id);

CREATE INDEX idx_operational_action_states_status_first_seen
  ON operational_action_states(status, first_seen_at, action_key);
