DROP PROCEDURE IF EXISTS sp_seed_demo_data;
DELIMITER //
CREATE PROCEDURE sp_seed_demo_data()
BEGIN
  DECLARE v_product_count INT DEFAULT 0;

  SELECT COUNT(*) INTO v_product_count FROM products;

  IF v_product_count = 0 THEN
    INSERT INTO products (
      sku, barcode, name, category, cost_price, sale_price,
      current_stock, minimum_stock, active
    ) VALUES
      ('MED-001', NULL, 'Dipirona 500 mg', 'Medicamentos', 6.85, 9.90, 120.000, 60.000, TRUE),
      ('MED-014', NULL, 'Amoxicilina 500 mg', 'Medicamentos', 18.40, 26.90, 42.000, 80.000, TRUE),
      ('MER-031', NULL, 'Arroz tipo 1 1 kg', 'Mercearia', 5.10, 7.49, 248.000, 90.000, TRUE),
      ('REF-008', NULL, 'Iogurte natural 170 g', 'Refrigerados', 2.15, 3.49, 48.000, 24.000, TRUE),
      ('HIG-022', NULL, 'Detergente neutro 500 ml', 'Higiene e limpeza', 1.95, 3.29, 76.000, 30.000, TRUE);

    INSERT INTO stock_batches (
      product_id, lot_code, expires_at, quantity, unit_cost, received_at
    )
    SELECT id, 'DIP2609A', '2026-12-18', 120.000, 6.85, CURRENT_TIMESTAMP
      FROM products WHERE sku = 'MED-001'
    UNION ALL
    SELECT id, 'AMX2608C', '2027-02-10', 42.000, 18.40, CURRENT_TIMESTAMP
      FROM products WHERE sku = 'MED-014'
    UNION ALL
    SELECT id, 'ARZ0926', '2027-08-14', 248.000, 5.10, CURRENT_TIMESTAMP
      FROM products WHERE sku = 'MER-031'
    UNION ALL
    SELECT id, 'IOG2809', '2026-10-07', 48.000, 2.15, CURRENT_TIMESTAMP
      FROM products WHERE sku = 'REF-008'
    UNION ALL
    SELECT id, 'DET26091', NULL, 76.000, 1.95, CURRENT_TIMESTAMP
      FROM products WHERE sku = 'HIG-022';
  END IF;

  IF NOT EXISTS (
    SELECT 1
      FROM stock_movements
     WHERE reason = 'Carga inicial de demonstração'
  ) THEN
    INSERT INTO stock_movements (
      product_id, batch_id, movement_type, quantity,
      balance_before, balance_after, reason
    )
    SELECT
      p.id,
      b.id,
      'ENTRY',
      p.current_stock,
      0,
      p.current_stock,
      'Carga inicial de demonstração'
    FROM products p
    JOIN stock_batches b
      ON b.product_id = p.id
    WHERE p.sku IN ('MED-001','MED-014','MER-031','REF-008','HIG-022');

    INSERT INTO stock_movement_allocations (
      movement_id, batch_id, quantity
    )
    SELECT
      sm.id,
      sm.batch_id,
      sm.quantity
    FROM stock_movements sm
    WHERE sm.reason = 'Carga inicial de demonstração'
      AND sm.batch_id IS NOT NULL
      AND NOT EXISTS (
        SELECT 1
          FROM stock_movement_allocations a
         WHERE a.movement_id = sm.id
           AND a.batch_id = sm.batch_id
      );
  END IF;
END //
DELIMITER ;

CALL sp_seed_demo_data();
DROP PROCEDURE IF EXISTS sp_seed_demo_data;
