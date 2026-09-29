CREATE TABLE IF NOT EXISTS inventory_alert_settings (
  id TINYINT PRIMARY KEY,
  expiry_warning_days INT NOT NULL DEFAULT 30,
  low_coverage_days INT NOT NULL DEFAULT 7,
  slow_moving_days INT NOT NULL DEFAULT 90,
  purchase_overdue_days INT NOT NULL DEFAULT 1,
  coverage_window_days INT NOT NULL DEFAULT 30,
  updated_by VARCHAR(120) NOT NULL DEFAULT 'system',
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_inventory_alert_singleton CHECK (id = 1),
  CONSTRAINT chk_inventory_alert_expiry CHECK (expiry_warning_days BETWEEN 1 AND 365),
  CONSTRAINT chk_inventory_alert_coverage CHECK (low_coverage_days BETWEEN 1 AND 365),
  CONSTRAINT chk_inventory_alert_slow CHECK (slow_moving_days BETWEEN 1 AND 3650),
  CONSTRAINT chk_inventory_alert_purchase CHECK (purchase_overdue_days BETWEEN 1 AND 365),
  CONSTRAINT chk_inventory_alert_window CHECK (coverage_window_days BETWEEN 7 AND 365)
);

INSERT INTO inventory_alert_settings(
  id, expiry_warning_days, low_coverage_days, slow_moving_days,
  purchase_overdue_days, coverage_window_days, updated_by
)
VALUES(1,30,7,90,1,30,'migration')
ON DUPLICATE KEY UPDATE id=id;
