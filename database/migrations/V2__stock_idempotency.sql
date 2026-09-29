ALTER TABLE stock_movements
  ADD COLUMN idempotency_key VARCHAR(64) NULL AFTER reason;

CREATE UNIQUE INDEX uk_stock_movements_idempotency
  ON stock_movements(idempotency_key);
