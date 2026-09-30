ALTER TABLE stock_batches
  ADD COLUMN quality_status ENUM('AVAILABLE','QUARANTINED','BLOCKED') NOT NULL DEFAULT 'AVAILABLE' AFTER unit_cost,
  ADD COLUMN quality_reason VARCHAR(255) NULL AFTER quality_status,
  ADD COLUMN quality_updated_by VARCHAR(120) NULL AFTER quality_reason,
  ADD COLUMN quality_updated_at TIMESTAMP NULL AFTER quality_updated_by;

CREATE TABLE batch_quality_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL,
  from_status ENUM('AVAILABLE','QUARANTINED','BLOCKED') NOT NULL,
  to_status ENUM('AVAILABLE','QUARANTINED','BLOCKED') NOT NULL,
  reason VARCHAR(255) NOT NULL,
  actor_username VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_batch_quality_event_batch
    FOREIGN KEY (batch_id) REFERENCES stock_batches(id)
);

CREATE TABLE lot_recalls (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  lot_code VARCHAR(80) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  status ENUM('OPEN','CLOSED') NOT NULL DEFAULT 'OPEN',
  created_by VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  closed_by VARCHAR(120) NULL,
  closed_at TIMESTAMP NULL,
  resolution VARCHAR(255) NULL,
  CONSTRAINT fk_lot_recall_product
    FOREIGN KEY (product_id) REFERENCES products(id)
);

CREATE TABLE lot_recall_batches (
  recall_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  previous_quality_status ENUM('AVAILABLE','QUARANTINED','BLOCKED') NOT NULL,
  previous_quality_reason VARCHAR(255) NULL,
  quantity_snapshot DECIMAL(12,3) NOT NULL,
  PRIMARY KEY (recall_id, batch_id),
  CONSTRAINT fk_lot_recall_batch_recall
    FOREIGN KEY (recall_id) REFERENCES lot_recalls(id),
  CONSTRAINT fk_lot_recall_batch_batch
    FOREIGN KEY (batch_id) REFERENCES stock_batches(id)
);

CREATE TABLE purchase_receipt_variances (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  purchase_order_id BIGINT NOT NULL,
  purchase_order_item_id BIGINT NOT NULL,
  purchase_receipt_id BIGINT NULL,
  variance_type ENUM('SHORT','EXCESS','DAMAGED','REJECTED','OTHER') NOT NULL,
  quantity DECIMAL(12,3) NOT NULL,
  reason VARCHAR(255) NOT NULL,
  reported_by VARCHAR(120) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_receipt_variance_order
    FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
  CONSTRAINT fk_receipt_variance_order_item
    FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(id),
  CONSTRAINT fk_receipt_variance_receipt
    FOREIGN KEY (purchase_receipt_id) REFERENCES purchase_receipts(id),
  CONSTRAINT chk_receipt_variance_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_batches_quality_product
  ON stock_batches(quality_status, product_id, quantity, expires_at, id);

CREATE INDEX idx_batch_quality_events_batch_created
  ON batch_quality_events(batch_id, created_at, id);

CREATE INDEX idx_lot_recalls_status_product_lot
  ON lot_recalls(status, product_id, lot_code, id);

CREATE INDEX idx_receipt_variances_order_created
  ON purchase_receipt_variances(purchase_order_id, created_at, id);
