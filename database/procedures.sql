USE nexo_estoque;

DROP PROCEDURE IF EXISTS sp_product_create;
DELIMITER //
CREATE PROCEDURE sp_product_create(
  IN p_sku VARCHAR(50),
  IN p_barcode VARCHAR(32),
  IN p_name VARCHAR(160),
  IN p_category VARCHAR(100),
  IN p_cost_price DECIMAL(12,2),
  IN p_sale_price DECIMAL(12,2),
  IN p_current_stock DECIMAL(12,3),
  IN p_minimum_stock DECIMAL(12,3),
  OUT p_id BIGINT
)
BEGIN
  INSERT INTO products(
    sku, barcode, name, category, cost_price, sale_price, current_stock, minimum_stock
  )
  VALUES(
    p_sku, NULLIF(p_barcode,''), p_name, p_category, p_cost_price, p_sale_price,
    p_current_stock, p_minimum_stock
  );
  SET p_id = LAST_INSERT_ID();
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_product_list;
DELIMITER //
CREATE PROCEDURE sp_product_list()
BEGIN
  SELECT id, sku, barcode, name, category, cost_price, sale_price,
         current_stock, minimum_stock, active
  FROM products
  ORDER BY active DESC, name ASC;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_stock_adjust;
DELIMITER //
CREATE PROCEDURE sp_stock_adjust(
  IN p_product_id BIGINT,
  IN p_quantity_delta DECIMAL(12,3),
  IN p_reason VARCHAR(255)
)
BEGIN
  DECLARE v_before DECIMAL(12,3);
  DECLARE v_after DECIMAL(12,3);

  START TRANSACTION;
  SELECT current_stock INTO v_before FROM products WHERE id = p_product_id FOR UPDATE;
  SET v_after = v_before + p_quantity_delta;

  IF v_after < 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Movimentação deixaria o estoque negativo';
  END IF;

  UPDATE products SET current_stock = v_after WHERE id = p_product_id;

  INSERT INTO stock_movements(
    product_id, movement_type, quantity, balance_before, balance_after, reason
  )
  VALUES(
    p_product_id,
    'ADJUSTMENT',
    ABS(p_quantity_delta),
    v_before,
    v_after,
    p_reason
  );
  COMMIT;
END //
DELIMITER ;


DROP PROCEDURE IF EXISTS sp_stock_move;
DELIMITER //
CREATE PROCEDURE sp_stock_move(
  IN p_product_id BIGINT,
  IN p_movement_type VARCHAR(20),
  IN p_quantity DECIMAL(12,3),
  IN p_reason VARCHAR(255),
  OUT p_movement_id BIGINT,
  OUT p_balance_before DECIMAL(12,3),
  OUT p_balance_after DECIMAL(12,3)
)
BEGIN
  DECLARE v_delta DECIMAL(12,3);
  DECLARE v_type VARCHAR(20);

  SET v_type = UPPER(TRIM(p_movement_type));

  IF v_type NOT IN ('ENTRY','EXIT','ADJUSTMENT','RETURN') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Tipo de movimentação inválido';
  END IF;

  IF p_quantity = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Quantidade não pode ser zero';
  END IF;

  IF v_type <> 'ADJUSTMENT' AND p_quantity < 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Quantidade deve ser positiva';
  END IF;

  SET v_delta = CASE
    WHEN v_type = 'EXIT' THEN -ABS(p_quantity)
    WHEN v_type IN ('ENTRY','RETURN') THEN ABS(p_quantity)
    ELSE p_quantity
  END;

  START TRANSACTION;

  SELECT current_stock
    INTO p_balance_before
    FROM products
   WHERE id = p_product_id
   FOR UPDATE;

  IF p_balance_before IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado';
  END IF;

  SET p_balance_after = p_balance_before + v_delta;

  IF p_balance_after < 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Movimentação deixaria o estoque negativo';
  END IF;

  UPDATE products
     SET current_stock = p_balance_after
   WHERE id = p_product_id;

  INSERT INTO stock_movements(
    product_id, movement_type, quantity, balance_before, balance_after, reason
  )
  VALUES(
    p_product_id,
    v_type,
    ABS(p_quantity),
    p_balance_before,
    p_balance_after,
    NULLIF(TRIM(p_reason),'')
  );

  SET p_movement_id = LAST_INSERT_ID();

  COMMIT;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_stock_movement_list;
DELIMITER //
CREATE PROCEDURE sp_stock_movement_list(
  IN p_limit INT
)
BEGIN
  SELECT
    sm.id,
    sm.product_id,
    p.name AS product_name,
    sm.movement_type,
    sm.quantity,
    sm.balance_before,
    sm.balance_after,
    sm.reason,
    sm.created_at
  FROM stock_movements sm
  JOIN products p ON p.id = sm.product_id
  ORDER BY sm.created_at DESC, sm.id DESC
  LIMIT p_limit;
END //
DELIMITER ;
