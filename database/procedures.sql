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
