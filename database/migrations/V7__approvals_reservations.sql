CREATE TABLE purchase_order_approvals (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  purchase_order_id BIGINT NOT NULL UNIQUE,
  status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  requested_by VARCHAR(120) NOT NULL,
  requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_by VARCHAR(120) NULL,
  decided_at TIMESTAMP NULL,
  decision_reason VARCHAR(255) NULL,
  CONSTRAINT fk_purchase_approval_order
    FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id)
);

CREATE TABLE stock_reservations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  reference_code VARCHAR(120) NOT NULL,
  notes VARCHAR(255) NULL,
  status ENUM('ACTIVE','CANCELLED','EXPIRED','RELEASED') NOT NULL DEFAULT 'ACTIVE',
  reserved_by VARCHAR(120) NOT NULL,
  expires_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  cancelled_by VARCHAR(120) NULL,
  cancelled_at TIMESTAMP NULL,
  CONSTRAINT fk_stock_reservation_product
    FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT chk_stock_reservation_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_purchase_approvals_status_requested
  ON purchase_order_approvals(status, requested_at, id);

CREATE INDEX idx_stock_reservations_product_status
  ON stock_reservations(product_id, status, expires_at, id);

CREATE INDEX idx_stock_reservations_status_expiry
  ON stock_reservations(status, expires_at, id);
