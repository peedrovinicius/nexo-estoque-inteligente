CREATE TABLE warehouses (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  branch_name VARCHAR(120) NULL,
  address VARCHAR(255) NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE stock_locations (
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

CREATE TABLE purchase_receipts (
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

CREATE TABLE purchase_receipt_items (
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

CREATE TABLE stock_transfers (
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
