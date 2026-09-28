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


DROP PROCEDURE IF EXISTS sp_blind_inventory_create;
DELIMITER //
CREATE PROCEDURE sp_blind_inventory_create(
  IN p_name VARCHAR(120),
  OUT p_session_id BIGINT
)
BEGIN
  IF p_name IS NULL OR CHAR_LENGTH(TRIM(p_name)) = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe um nome para o inventário';
  END IF;

  INSERT INTO blind_inventory_sessions(name, status)
  VALUES(TRIM(p_name), 'OPEN');

  SET p_session_id = LAST_INSERT_ID();
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_blind_inventory_list;
DELIMITER //
CREATE PROCEDURE sp_blind_inventory_list()
BEGIN
  SELECT
    s.id,
    s.name,
    s.status,
    s.started_at,
    s.closed_at,
    COUNT(c.id) AS counted_items,
    CASE
      WHEN s.status = 'CLOSED' THEN SUM(CASE WHEN c.difference_quantity <> 0 THEN 1 ELSE 0 END)
      ELSE 0
    END AS divergent_items
  FROM blind_inventory_sessions s
  LEFT JOIN blind_inventory_counts c ON c.session_id = s.id
  GROUP BY s.id, s.name, s.status, s.started_at, s.closed_at
  ORDER BY
    CASE s.status WHEN 'OPEN' THEN 0 WHEN 'CLOSED' THEN 1 ELSE 2 END,
    s.started_at DESC,
    s.id DESC;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_blind_inventory_items;
DELIMITER //
CREATE PROCEDURE sp_blind_inventory_items(
  IN p_session_id BIGINT
)
BEGIN
  DECLARE v_status VARCHAR(20);

  SELECT status
    INTO v_status
    FROM blind_inventory_sessions
   WHERE id = p_session_id;

  IF v_status IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sessão de inventário não encontrada';
  END IF;

  SELECT
    p.id AS product_id,
    p.sku,
    p.name AS product_name,
    c.counted_quantity,
    CASE WHEN v_status = 'CLOSED' THEN c.system_quantity_snapshot ELSE NULL END AS system_quantity_snapshot,
    CASE WHEN v_status = 'CLOSED' THEN c.difference_quantity ELSE NULL END AS difference_quantity,
    c.counted_at,
    (v_status = 'CLOSED') AS revealed
  FROM products p
  LEFT JOIN blind_inventory_counts c
    ON c.product_id = p.id
   AND c.session_id = p_session_id
  WHERE p.active = TRUE
  ORDER BY p.name ASC, p.id ASC;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_blind_inventory_count;
DELIMITER //
CREATE PROCEDURE sp_blind_inventory_count(
  IN p_session_id BIGINT,
  IN p_product_id BIGINT,
  IN p_counted_quantity DECIMAL(12,3)
)
BEGIN
  DECLARE v_status VARCHAR(20);
  DECLARE v_system_quantity DECIMAL(12,3);
  DECLARE v_existing_id BIGINT;

  IF p_counted_quantity IS NULL OR p_counted_quantity < 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A contagem não pode ser negativa';
  END IF;

  START TRANSACTION;

  SELECT status
    INTO v_status
    FROM blind_inventory_sessions
   WHERE id = p_session_id
   FOR UPDATE;

  IF v_status IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sessão de inventário não encontrada';
  END IF;

  IF v_status <> 'OPEN' THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Somente inventários abertos aceitam contagens';
  END IF;

  SELECT current_stock
    INTO v_system_quantity
    FROM products
   WHERE id = p_product_id
     AND active = TRUE
   FOR UPDATE;

  IF v_system_quantity IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado ou inativo';
  END IF;

  SET v_existing_id = NULL;
  SELECT id
    INTO v_existing_id
    FROM blind_inventory_counts
   WHERE session_id = p_session_id
     AND product_id = p_product_id
   ORDER BY id ASC
   LIMIT 1;

  IF v_existing_id IS NULL THEN
    INSERT INTO blind_inventory_counts(
      session_id,
      product_id,
      counted_quantity,
      system_quantity_snapshot,
      difference_quantity,
      counted_at
    )
    VALUES(
      p_session_id,
      p_product_id,
      p_counted_quantity,
      v_system_quantity,
      p_counted_quantity - v_system_quantity,
      CURRENT_TIMESTAMP
    );
  ELSE
    UPDATE blind_inventory_counts
       SET counted_quantity = p_counted_quantity,
           system_quantity_snapshot = v_system_quantity,
           difference_quantity = p_counted_quantity - v_system_quantity,
           counted_at = CURRENT_TIMESTAMP
     WHERE id = v_existing_id;
  END IF;

  COMMIT;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_blind_inventory_close;
DELIMITER //
CREATE PROCEDURE sp_blind_inventory_close(
  IN p_session_id BIGINT
)
BEGIN
  DECLARE v_status VARCHAR(20);
  DECLARE v_active_products INT DEFAULT 0;
  DECLARE v_counted_products INT DEFAULT 0;

  START TRANSACTION;

  SELECT status
    INTO v_status
    FROM blind_inventory_sessions
   WHERE id = p_session_id
   FOR UPDATE;

  IF v_status IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Sessão de inventário não encontrada';
  END IF;

  IF v_status <> 'OPEN' THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O inventário já foi encerrado';
  END IF;

  SELECT COUNT(*)
    INTO v_active_products
    FROM products
   WHERE active = TRUE;

  SELECT COUNT(DISTINCT product_id)
    INTO v_counted_products
    FROM blind_inventory_counts
   WHERE session_id = p_session_id;

  IF v_active_products = 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Não há produtos ativos para inventariar';
  END IF;

  IF v_counted_products < v_active_products THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existem produtos sem contagem';
  END IF;

  UPDATE blind_inventory_sessions
     SET status = 'CLOSED',
         closed_at = CURRENT_TIMESTAMP
   WHERE id = p_session_id;

  COMMIT;
END //
DELIMITER ;
