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
  IF COALESCE(p_current_stock,0) <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto novo deve iniciar com estoque zero; registre a entrada por lote';
  END IF;

  INSERT INTO products(
    sku, barcode, name, category, cost_price, sale_price, current_stock, minimum_stock
  )
  VALUES(
    p_sku, NULLIF(p_barcode,''), p_name, p_category, p_cost_price, p_sale_price,
    0, p_minimum_stock
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


DROP PROCEDURE IF EXISTS sp_product_update;
DELIMITER //
CREATE PROCEDURE sp_product_update(
  IN p_id BIGINT,
  IN p_sku VARCHAR(50),
  IN p_barcode VARCHAR(32),
  IN p_name VARCHAR(160),
  IN p_category VARCHAR(100),
  IN p_cost_price DECIMAL(12,2),
  IN p_sale_price DECIMAL(12,2),
  IN p_minimum_stock DECIMAL(12,3)
)
BEGIN
  DECLARE v_exists BIGINT DEFAULT NULL;

  START TRANSACTION;

  SELECT id
    INTO v_exists
    FROM products
   WHERE id = p_id
   FOR UPDATE;

  IF v_exists IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado';
  END IF;

  UPDATE products
     SET sku = TRIM(p_sku),
         barcode = NULLIF(TRIM(p_barcode),''),
         name = TRIM(p_name),
         category = TRIM(p_category),
         cost_price = COALESCE(p_cost_price,0),
         sale_price = COALESCE(p_sale_price,0),
         minimum_stock = COALESCE(p_minimum_stock,0)
   WHERE id = p_id;

  COMMIT;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_product_set_active;
DELIMITER //
CREATE PROCEDURE sp_product_set_active(
  IN p_id BIGINT,
  IN p_active BOOLEAN
)
BEGIN
  DECLARE v_exists BIGINT DEFAULT NULL;
  DECLARE v_stock DECIMAL(12,3) DEFAULT 0;
  DECLARE v_batch_stock DECIMAL(12,3) DEFAULT 0;
  DECLARE v_open_inventories INT DEFAULT 0;

  START TRANSACTION;

  SELECT id, current_stock
    INTO v_exists, v_stock
    FROM products
   WHERE id = p_id
   FOR UPDATE;

  IF v_exists IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado';
  END IF;

  IF p_active = FALSE THEN
    SELECT COALESCE(SUM(quantity),0)
      INTO v_batch_stock
      FROM stock_batches
     WHERE product_id = p_id;

    SELECT COUNT(*)
      INTO v_open_inventories
      FROM blind_inventory_sessions
     WHERE status = 'OPEN';

    IF COALESCE(v_stock,0) <> 0 THEN
      ROLLBACK;
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Zere o estoque antes de inativar o produto';
    END IF;

    IF COALESCE(v_batch_stock,0) <> 0 THEN
      ROLLBACK;
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Zere os lotes antes de inativar o produto';
    END IF;

    IF v_open_inventories > 0 THEN
      ROLLBACK;
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Encerre os inventários abertos antes de inativar produtos';
    END IF;
  END IF;

  UPDATE products
     SET active = p_active
   WHERE id = p_id;

  COMMIT;
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
  IN p_idempotency_key VARCHAR(64),
  OUT p_movement_id BIGINT,
  OUT p_balance_before DECIMAL(12,3),
  OUT p_balance_after DECIMAL(12,3)
)
main: BEGIN
  DECLARE v_product_lock BIGINT DEFAULT NULL;
  DECLARE v_existing_movement BIGINT DEFAULT NULL;
  DECLARE v_existing_product BIGINT DEFAULT NULL;
  DECLARE v_existing_type VARCHAR(20) DEFAULT NULL;
  DECLARE v_existing_quantity DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_balance_before DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_balance_after DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_movement_batch BIGINT DEFAULT NULL;
  DECLARE v_delta DECIMAL(12,3);
  DECLARE v_type VARCHAR(20);
  DECLARE v_batch_count INT DEFAULT 0;

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

  IF p_idempotency_key IS NULL OR CHAR_LENGTH(TRIM(p_idempotency_key)) < 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe uma chave de idempotência válida';
  END IF;

  START TRANSACTION;

  SET v_product_lock = NULL;
  SELECT id
    INTO v_product_lock
    FROM products
   WHERE id = p_product_id
   FOR UPDATE;

  IF v_product_lock IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado';
  END IF;

  SET v_existing_movement = NULL;
  SELECT id, product_id, movement_type, quantity, balance_before, balance_after, batch_id
    INTO v_existing_movement, v_existing_product, v_existing_type, v_existing_quantity,
         v_existing_balance_before, v_existing_balance_after, v_existing_movement_batch
    FROM stock_movements
   WHERE idempotency_key = TRIM(p_idempotency_key)
   LIMIT 1;

  IF v_existing_movement IS NULL AND EXISTS (
    SELECT 1
      FROM stock_movements_archive
     WHERE idempotency_key = TRIM(p_idempotency_key)
  ) THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Chave de idempotência pertence a operação arquivada';
  END IF;

  IF v_existing_movement IS NOT NULL THEN
    IF v_existing_product <> p_product_id
       OR v_existing_type <> v_type
       OR v_existing_quantity <> ABS(p_quantity) THEN
      ROLLBACK;
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Chave de idempotência já utilizada por outra operação';
    END IF;

    SET p_movement_id = v_existing_movement;
    SET p_balance_before = v_existing_balance_before;
    SET p_balance_after = v_existing_balance_after;
    COMMIT;
    LEAVE main;
  END IF;

  SELECT current_stock
    INTO p_balance_before
    FROM products
   WHERE id = p_product_id
   FOR UPDATE;

  IF p_balance_before IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado';
  END IF;

  SELECT COUNT(*)
    INTO v_batch_count
    FROM stock_batches
   WHERE product_id = p_product_id;

  IF v_batch_count > 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto rastreado por lote exige movimentação por lote';
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
    product_id, movement_type, quantity, balance_before, balance_after, reason, idempotency_key
  )
  VALUES(
    p_product_id,
    v_type,
    ABS(p_quantity),
    p_balance_before,
    p_balance_after,
    NULLIF(TRIM(p_reason),''),
    TRIM(p_idempotency_key)
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
    sm.performed_by,
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


DROP PROCEDURE IF EXISTS sp_stock_batch_entry;
DELIMITER //
CREATE PROCEDURE sp_stock_batch_entry(
  IN p_product_id BIGINT,
  IN p_lot_code VARCHAR(80),
  IN p_expires_at DATE,
  IN p_quantity DECIMAL(12,3),
  IN p_unit_cost DECIMAL(12,2),
  IN p_reason VARCHAR(255),
  IN p_idempotency_key VARCHAR(64),
  OUT p_movement_id BIGINT,
  OUT p_batch_id BIGINT,
  OUT p_balance_before DECIMAL(12,3),
  OUT p_balance_after DECIMAL(12,3)
)
main: BEGIN
  DECLARE v_product_lock BIGINT DEFAULT NULL;
  DECLARE v_existing_movement BIGINT DEFAULT NULL;
  DECLARE v_existing_product BIGINT DEFAULT NULL;
  DECLARE v_existing_type VARCHAR(20) DEFAULT NULL;
  DECLARE v_existing_quantity DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_balance_before DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_balance_after DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_movement_batch BIGINT DEFAULT NULL;
  DECLARE v_existing_batch BIGINT;
  DECLARE v_default_location BIGINT DEFAULT NULL;

  IF p_quantity IS NULL OR p_quantity <= 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A quantidade da entrada deve ser maior que zero';
  END IF;

  IF p_lot_code IS NULL OR CHAR_LENGTH(TRIM(p_lot_code)) = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe o lote da entrada';
  END IF;

  IF p_expires_at IS NOT NULL AND p_expires_at < CURDATE() THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Não é permitido receber lote já vencido';
  END IF;

  IF p_idempotency_key IS NULL OR CHAR_LENGTH(TRIM(p_idempotency_key)) < 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe uma chave de idempotência válida';
  END IF;

  START TRANSACTION;

  SET v_product_lock = NULL;
  SELECT id
    INTO v_product_lock
    FROM products
   WHERE id = p_product_id
   FOR UPDATE;

  IF v_product_lock IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado';
  END IF;

  SET v_existing_movement = NULL;
  SELECT id, product_id, movement_type, quantity, balance_before, balance_after, batch_id
    INTO v_existing_movement, v_existing_product, v_existing_type, v_existing_quantity,
         v_existing_balance_before, v_existing_balance_after, v_existing_movement_batch
    FROM stock_movements
   WHERE idempotency_key = TRIM(p_idempotency_key)
   LIMIT 1;

  IF v_existing_movement IS NULL AND EXISTS (
    SELECT 1
      FROM stock_movements_archive
     WHERE idempotency_key = TRIM(p_idempotency_key)
  ) THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Chave de idempotência pertence a operação arquivada';
  END IF;

  IF v_existing_movement IS NOT NULL THEN
    IF v_existing_product <> p_product_id
       OR v_existing_type <> 'ENTRY'
       OR v_existing_quantity <> ABS(p_quantity) THEN
      ROLLBACK;
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Chave de idempotência já utilizada por outra operação';
    END IF;

    SET p_movement_id = v_existing_movement;
    SET p_balance_before = v_existing_balance_before;
    SET p_balance_after = v_existing_balance_after;
    SET p_batch_id = v_existing_movement_batch;
    COMMIT;
    LEAVE main;
  END IF;

  SET p_balance_before = NULL;
  SELECT current_stock
    INTO p_balance_before
    FROM products
   WHERE id = p_product_id
     AND active = TRUE
   FOR UPDATE;

  IF p_balance_before IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado ou inativo';
  END IF;

  SELECT sl.id
    INTO v_default_location
    FROM stock_locations sl
    JOIN warehouses w ON w.id = sl.warehouse_id
   WHERE w.code = 'MAIN'
     AND sl.code = 'GERAL'
     AND w.active = TRUE
     AND sl.active = TRUE
   LIMIT 1;

  IF v_default_location IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Local padrão de estoque não configurado';
  END IF;

  SELECT MAX(id)
    INTO v_existing_batch
    FROM stock_batches
   WHERE product_id = p_product_id
     AND lot_code = TRIM(p_lot_code)
     AND location_id = v_default_location;

  IF v_existing_batch IS NULL THEN
    INSERT INTO stock_batches(
      product_id, location_id, lot_code, expires_at, quantity, unit_cost
    )
    VALUES(
      p_product_id,
      v_default_location,
      TRIM(p_lot_code),
      p_expires_at,
      p_quantity,
      COALESCE(p_unit_cost,0)
    );
    SET p_batch_id = LAST_INSERT_ID();
  ELSE
    UPDATE stock_batches
       SET quantity = quantity + p_quantity,
           expires_at = COALESCE(p_expires_at, expires_at),
           unit_cost = CASE
             WHEN p_unit_cost IS NULL OR p_unit_cost = 0 THEN unit_cost
             ELSE p_unit_cost
           END
     WHERE id = v_existing_batch;
    SET p_batch_id = v_existing_batch;
  END IF;

  SET p_balance_after = p_balance_before + p_quantity;

  UPDATE products
     SET current_stock = p_balance_after
   WHERE id = p_product_id;

  INSERT INTO stock_movements(
    product_id, batch_id, movement_type, quantity, balance_before, balance_after, reason, idempotency_key
  )
  VALUES(
    p_product_id,
    p_batch_id,
    'ENTRY',
    p_quantity,
    p_balance_before,
    p_balance_after,
    NULLIF(TRIM(p_reason),''),
    TRIM(p_idempotency_key)
  );

  SET p_movement_id = LAST_INSERT_ID();

  INSERT INTO stock_movement_allocations(
    movement_id, batch_id, quantity
  )
  VALUES(
    p_movement_id, p_batch_id, p_quantity
  );

  COMMIT;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_stock_exit_fefo;
DELIMITER //
CREATE PROCEDURE sp_stock_exit_fefo(
  IN p_product_id BIGINT,
  IN p_quantity DECIMAL(12,3),
  IN p_reason VARCHAR(255),
  IN p_idempotency_key VARCHAR(64),
  OUT p_movement_id BIGINT,
  OUT p_balance_before DECIMAL(12,3),
  OUT p_balance_after DECIMAL(12,3)
)
main: BEGIN
  DECLARE v_product_lock BIGINT DEFAULT NULL;
  DECLARE v_existing_movement BIGINT DEFAULT NULL;
  DECLARE v_existing_product BIGINT DEFAULT NULL;
  DECLARE v_existing_type VARCHAR(20) DEFAULT NULL;
  DECLARE v_existing_quantity DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_balance_before DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_balance_after DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_movement_batch BIGINT DEFAULT NULL;
  DECLARE v_batch_total DECIMAL(12,3) DEFAULT 0;
  DECLARE v_legacy_gap DECIMAL(12,3) DEFAULT 0;
  DECLARE v_remaining DECIMAL(12,3) DEFAULT 0;
  DECLARE v_batch_id BIGINT;
  DECLARE v_batch_quantity DECIMAL(12,3);
  DECLARE v_take DECIMAL(12,3);
  DECLARE v_default_location BIGINT DEFAULT NULL;

  IF p_quantity IS NULL OR p_quantity <= 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A quantidade da saída deve ser maior que zero';
  END IF;

  IF p_idempotency_key IS NULL OR CHAR_LENGTH(TRIM(p_idempotency_key)) < 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe uma chave de idempotência válida';
  END IF;

  START TRANSACTION;

  SET v_product_lock = NULL;
  SELECT id
    INTO v_product_lock
    FROM products
   WHERE id = p_product_id
   FOR UPDATE;

  IF v_product_lock IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado';
  END IF;

  SET v_existing_movement = NULL;
  SELECT id, product_id, movement_type, quantity, balance_before, balance_after, batch_id
    INTO v_existing_movement, v_existing_product, v_existing_type, v_existing_quantity,
         v_existing_balance_before, v_existing_balance_after, v_existing_movement_batch
    FROM stock_movements
   WHERE idempotency_key = TRIM(p_idempotency_key)
   LIMIT 1;

  IF v_existing_movement IS NULL AND EXISTS (
    SELECT 1
      FROM stock_movements_archive
     WHERE idempotency_key = TRIM(p_idempotency_key)
  ) THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Chave de idempotência pertence a operação arquivada';
  END IF;

  IF v_existing_movement IS NOT NULL THEN
    IF v_existing_product <> p_product_id
       OR v_existing_type <> 'EXIT'
       OR v_existing_quantity <> ABS(p_quantity) THEN
      ROLLBACK;
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Chave de idempotência já utilizada por outra operação';
    END IF;

    SET p_movement_id = v_existing_movement;
    SET p_balance_before = v_existing_balance_before;
    SET p_balance_after = v_existing_balance_after;
    COMMIT;
    LEAVE main;
  END IF;

  SET p_balance_before = NULL;
  SELECT current_stock
    INTO p_balance_before
    FROM products
   WHERE id = p_product_id
     AND active = TRUE
   FOR UPDATE;

  IF p_balance_before IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado ou inativo';
  END IF;

  IF p_balance_before < p_quantity THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Estoque insuficiente para a saída';
  END IF;

  SELECT COALESCE(SUM(quantity),0)
    INTO v_batch_total
    FROM stock_batches
   WHERE product_id = p_product_id;

  SELECT sl.id
    INTO v_default_location
    FROM stock_locations sl
    JOIN warehouses w ON w.id = sl.warehouse_id
   WHERE w.code = 'MAIN'
     AND sl.code = 'GERAL'
   LIMIT 1;

  SET v_legacy_gap = p_balance_before - v_batch_total;

  IF v_legacy_gap > 0 THEN
    INSERT INTO stock_batches(
      product_id, location_id, lot_code, expires_at, quantity, unit_cost
    )
    VALUES(
      p_product_id, v_default_location, 'SALDO-LEGADO', NULL, v_legacy_gap, 0
    )
    ON DUPLICATE KEY UPDATE
      quantity = quantity + VALUES(quantity);
  END IF;

  SET p_balance_after = p_balance_before - p_quantity;

  UPDATE products
     SET current_stock = p_balance_after
   WHERE id = p_product_id;

  INSERT INTO stock_movements(
    product_id, batch_id, movement_type, quantity, balance_before, balance_after, reason, idempotency_key
  )
  VALUES(
    p_product_id,
    NULL,
    'EXIT',
    p_quantity,
    p_balance_before,
    p_balance_after,
    NULLIF(TRIM(p_reason),''),
    TRIM(p_idempotency_key)
  );

  SET p_movement_id = LAST_INSERT_ID();
  SET v_remaining = p_quantity;

  WHILE v_remaining > 0 DO
    SET v_batch_id = NULL;
    SET v_batch_quantity = NULL;

    SELECT id, quantity
      INTO v_batch_id, v_batch_quantity
      FROM stock_batches
     WHERE product_id = p_product_id
       AND quantity > 0
     ORDER BY
       CASE WHEN expires_at IS NULL THEN 1 ELSE 0 END ASC,
       expires_at ASC,
       received_at ASC,
       id ASC
     LIMIT 1
     FOR UPDATE;

    IF v_batch_id IS NULL OR v_batch_quantity IS NULL THEN
      ROLLBACK;
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Saldo por lote insuficiente para concluir FEFO';
    END IF;

    SET v_take = LEAST(v_remaining, v_batch_quantity);

    UPDATE stock_batches
       SET quantity = quantity - v_take
     WHERE id = v_batch_id;

    INSERT INTO stock_movement_allocations(
      movement_id, batch_id, quantity
    )
    VALUES(
      p_movement_id, v_batch_id, v_take
    );

    SET v_remaining = v_remaining - v_take;
  END WHILE;

  COMMIT;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_stock_batch_list;
DELIMITER //
CREATE PROCEDURE sp_stock_batch_list(
  IN p_product_id BIGINT
)
BEGIN
  SELECT
    b.id,
    b.product_id,
    b.location_id,
    w.id AS warehouse_id,
    w.code AS warehouse_code,
    w.name AS warehouse_name,
    w.branch_name,
    sl.code AS location_code,
    sl.aisle,
    sl.shelf,
    sl.bin_code,
    p.sku,
    p.name AS product_name,
    b.lot_code,
    b.expires_at,
    b.quantity,
    b.unit_cost,
    b.received_at,
    DATEDIFF(b.expires_at, CURDATE()) AS days_to_expiry,
    CASE
      WHEN b.expires_at IS NULL THEN 'NO_EXPIRY'
      WHEN b.expires_at < CURDATE() THEN 'EXPIRED'
      WHEN DATEDIFF(b.expires_at, CURDATE()) <= 30 THEN 'CRITICAL'
      WHEN DATEDIFF(b.expires_at, CURDATE()) <= 90 THEN 'ATTENTION'
      ELSE 'OK'
    END AS expiry_status,
    ROW_NUMBER() OVER(
      PARTITION BY b.product_id
      ORDER BY
        CASE WHEN b.expires_at IS NULL THEN 1 ELSE 0 END ASC,
        b.expires_at ASC,
        b.received_at ASC,
        b.id ASC
    ) AS fefo_position
  FROM stock_batches b
  JOIN products p ON p.id = b.product_id
  JOIN stock_locations sl ON sl.id = b.location_id
  JOIN warehouses w ON w.id = sl.warehouse_id
  WHERE b.quantity > 0
    AND (p_product_id IS NULL OR b.product_id = p_product_id)
  ORDER BY
    p.name ASC,
    CASE WHEN b.expires_at IS NULL THEN 1 ELSE 0 END ASC,
    b.expires_at ASC,
    b.received_at ASC,
    b.id ASC;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_stock_movement_allocations;
DELIMITER //
CREATE PROCEDURE sp_stock_movement_allocations(
  IN p_movement_id BIGINT
)
BEGIN
  SELECT
    a.id,
    a.movement_id,
    a.batch_id,
    b.lot_code,
    b.expires_at,
    a.quantity
  FROM stock_movement_allocations a
  JOIN stock_batches b ON b.id = a.batch_id
  WHERE a.movement_id = p_movement_id
  ORDER BY
    CASE WHEN b.expires_at IS NULL THEN 1 ELSE 0 END ASC,
    b.expires_at ASC,
    b.received_at ASC,
    b.id ASC;
END //
DELIMITER ;


DROP PROCEDURE IF EXISTS sp_stock_batch_return;
DELIMITER //
CREATE PROCEDURE sp_stock_batch_return(
  IN p_product_id BIGINT,
  IN p_batch_id BIGINT,
  IN p_quantity DECIMAL(12,3),
  IN p_reason VARCHAR(255),
  IN p_idempotency_key VARCHAR(64),
  OUT p_movement_id BIGINT,
  OUT p_balance_before DECIMAL(12,3),
  OUT p_balance_after DECIMAL(12,3)
)
main: BEGIN
  DECLARE v_product_lock BIGINT DEFAULT NULL;
  DECLARE v_existing_movement BIGINT DEFAULT NULL;
  DECLARE v_existing_product BIGINT DEFAULT NULL;
  DECLARE v_existing_type VARCHAR(20) DEFAULT NULL;
  DECLARE v_existing_quantity DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_balance_before DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_balance_after DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_movement_batch BIGINT DEFAULT NULL;
  DECLARE v_batch_quantity DECIMAL(12,3);

  IF p_quantity IS NULL OR p_quantity <= 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'A quantidade da devolução deve ser maior que zero';
  END IF;

  IF p_idempotency_key IS NULL OR CHAR_LENGTH(TRIM(p_idempotency_key)) < 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe uma chave de idempotência válida';
  END IF;

  START TRANSACTION;

  SET v_product_lock = NULL;
  SELECT id
    INTO v_product_lock
    FROM products
   WHERE id = p_product_id
   FOR UPDATE;

  IF v_product_lock IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado';
  END IF;

  SET v_existing_movement = NULL;
  SELECT id, product_id, movement_type, quantity, balance_before, balance_after, batch_id
    INTO v_existing_movement, v_existing_product, v_existing_type, v_existing_quantity,
         v_existing_balance_before, v_existing_balance_after, v_existing_movement_batch
    FROM stock_movements
   WHERE idempotency_key = TRIM(p_idempotency_key)
   LIMIT 1;

  IF v_existing_movement IS NULL AND EXISTS (
    SELECT 1
      FROM stock_movements_archive
     WHERE idempotency_key = TRIM(p_idempotency_key)
  ) THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Chave de idempotência pertence a operação arquivada';
  END IF;

  IF v_existing_movement IS NOT NULL THEN
    IF v_existing_product <> p_product_id
       OR v_existing_type <> 'RETURN'
       OR v_existing_quantity <> ABS(p_quantity) THEN
      ROLLBACK;
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Chave de idempotência já utilizada por outra operação';
    END IF;

    SET p_movement_id = v_existing_movement;
    SET p_balance_before = v_existing_balance_before;
    SET p_balance_after = v_existing_balance_after;
    COMMIT;
    LEAVE main;
  END IF;

  SET p_balance_before = NULL;
  SELECT current_stock
    INTO p_balance_before
    FROM products
   WHERE id = p_product_id
     AND active = TRUE
   FOR UPDATE;

  IF p_balance_before IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado ou inativo';
  END IF;

  SET v_batch_quantity = NULL;
  SELECT quantity
    INTO v_batch_quantity
    FROM stock_batches
   WHERE id = p_batch_id
     AND product_id = p_product_id
   FOR UPDATE;

  IF v_batch_quantity IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Lote não encontrado para o produto';
  END IF;

  SET p_balance_after = p_balance_before + p_quantity;

  UPDATE stock_batches
     SET quantity = quantity + p_quantity
   WHERE id = p_batch_id;

  UPDATE products
     SET current_stock = p_balance_after
   WHERE id = p_product_id;

  INSERT INTO stock_movements(
    product_id, batch_id, movement_type, quantity, balance_before, balance_after, reason, idempotency_key
  )
  VALUES(
    p_product_id,
    p_batch_id,
    'RETURN',
    p_quantity,
    p_balance_before,
    p_balance_after,
    NULLIF(TRIM(p_reason),''),
    TRIM(p_idempotency_key)
  );

  SET p_movement_id = LAST_INSERT_ID();

  INSERT INTO stock_movement_allocations(
    movement_id, batch_id, quantity
  )
  VALUES(
    p_movement_id, p_batch_id, p_quantity
  );

  COMMIT;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_stock_batch_adjustment;
DELIMITER //
CREATE PROCEDURE sp_stock_batch_adjustment(
  IN p_product_id BIGINT,
  IN p_batch_id BIGINT,
  IN p_quantity_delta DECIMAL(12,3),
  IN p_reason VARCHAR(255),
  IN p_idempotency_key VARCHAR(64),
  OUT p_movement_id BIGINT,
  OUT p_balance_before DECIMAL(12,3),
  OUT p_balance_after DECIMAL(12,3)
)
main: BEGIN
  DECLARE v_product_lock BIGINT DEFAULT NULL;
  DECLARE v_existing_movement BIGINT DEFAULT NULL;
  DECLARE v_existing_product BIGINT DEFAULT NULL;
  DECLARE v_existing_type VARCHAR(20) DEFAULT NULL;
  DECLARE v_existing_quantity DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_balance_before DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_balance_after DECIMAL(12,3) DEFAULT NULL;
  DECLARE v_existing_movement_batch BIGINT DEFAULT NULL;
  DECLARE v_batch_before DECIMAL(12,3);
  DECLARE v_batch_after DECIMAL(12,3);

  IF p_quantity_delta IS NULL OR p_quantity_delta = 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'O ajuste deve ser diferente de zero';
  END IF;

  IF p_idempotency_key IS NULL OR CHAR_LENGTH(TRIM(p_idempotency_key)) < 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Informe uma chave de idempotência válida';
  END IF;

  START TRANSACTION;

  SET v_product_lock = NULL;
  SELECT id
    INTO v_product_lock
    FROM products
   WHERE id = p_product_id
   FOR UPDATE;

  IF v_product_lock IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado';
  END IF;

  SET v_existing_movement = NULL;
  SELECT id, product_id, movement_type, quantity, balance_before, balance_after, batch_id
    INTO v_existing_movement, v_existing_product, v_existing_type, v_existing_quantity,
         v_existing_balance_before, v_existing_balance_after, v_existing_movement_batch
    FROM stock_movements
   WHERE idempotency_key = TRIM(p_idempotency_key)
   LIMIT 1;

  IF v_existing_movement IS NULL AND EXISTS (
    SELECT 1
      FROM stock_movements_archive
     WHERE idempotency_key = TRIM(p_idempotency_key)
  ) THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Chave de idempotência pertence a operação arquivada';
  END IF;

  IF v_existing_movement IS NOT NULL THEN
    IF v_existing_product <> p_product_id
       OR v_existing_type <> 'ADJUSTMENT'
       OR v_existing_quantity <> ABS(p_quantity_delta) THEN
      ROLLBACK;
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Chave de idempotência já utilizada por outra operação';
    END IF;

    SET p_movement_id = v_existing_movement;
    SET p_balance_before = v_existing_balance_before;
    SET p_balance_after = v_existing_balance_after;
    COMMIT;
    LEAVE main;
  END IF;

  SET p_balance_before = NULL;
  SELECT current_stock
    INTO p_balance_before
    FROM products
   WHERE id = p_product_id
     AND active = TRUE
   FOR UPDATE;

  IF p_balance_before IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Produto não encontrado ou inativo';
  END IF;

  SET v_batch_before = NULL;
  SELECT quantity
    INTO v_batch_before
    FROM stock_batches
   WHERE id = p_batch_id
     AND product_id = p_product_id
   FOR UPDATE;

  IF v_batch_before IS NULL THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Lote não encontrado para o produto';
  END IF;

  SET v_batch_after = v_batch_before + p_quantity_delta;
  SET p_balance_after = p_balance_before + p_quantity_delta;

  IF v_batch_after < 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ajuste deixaria o lote negativo';
  END IF;

  IF p_balance_after < 0 THEN
    ROLLBACK;
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ajuste deixaria o estoque negativo';
  END IF;

  UPDATE stock_batches
     SET quantity = v_batch_after
   WHERE id = p_batch_id;

  UPDATE products
     SET current_stock = p_balance_after
   WHERE id = p_product_id;

  INSERT INTO stock_movements(
    product_id, batch_id, movement_type, quantity, balance_before, balance_after, reason, idempotency_key
  )
  VALUES(
    p_product_id,
    p_batch_id,
    'ADJUSTMENT',
    ABS(p_quantity_delta),
    p_balance_before,
    p_balance_after,
    NULLIF(TRIM(p_reason),''),
    TRIM(p_idempotency_key)
  );

  SET p_movement_id = LAST_INSERT_ID();

  INSERT INTO stock_movement_allocations(
    movement_id, batch_id, quantity
  )
  VALUES(
    p_movement_id, p_batch_id, ABS(p_quantity_delta)
  );

  COMMIT;
END //
DELIMITER ;


DROP PROCEDURE IF EXISTS sp_archive_stock_movements;
DELIMITER //
CREATE PROCEDURE sp_archive_stock_movements(
  IN p_before DATETIME,
  IN p_limit INT,
  IN p_actor VARCHAR(120),
  OUT p_archived INT
)
BEGIN
  DECLARE v_limit INT DEFAULT 1000;

  SET v_limit = LEAST(GREATEST(COALESCE(p_limit,1000),1),10000);
  SET p_archived = 0;

  START TRANSACTION;

  CREATE TEMPORARY TABLE IF NOT EXISTS tmp_archive_movement_ids(
    id BIGINT PRIMARY KEY
  ) ENGINE=MEMORY;

  TRUNCATE TABLE tmp_archive_movement_ids;

  INSERT INTO tmp_archive_movement_ids(id)
  SELECT sm.id
    FROM stock_movements sm
   WHERE sm.created_at < p_before
   ORDER BY sm.created_at, sm.id
   LIMIT v_limit;

  INSERT IGNORE INTO stock_movement_allocations_archive(
    original_allocation_id,
    original_movement_id,
    original_batch_id,
    lot_code,
    expires_at,
    quantity,
    created_at
  )
  SELECT
    sma.id,
    sma.movement_id,
    sma.batch_id,
    sb.lot_code,
    sb.expires_at,
    sma.quantity,
    sma.created_at
  FROM stock_movement_allocations sma
  JOIN tmp_archive_movement_ids ids ON ids.id = sma.movement_id
  JOIN stock_batches sb ON sb.id = sma.batch_id;

  INSERT IGNORE INTO stock_movements_archive(
    original_movement_id,
    product_id,
    product_sku,
    product_name,
    batch_id,
    lot_code,
    movement_type,
    quantity,
    balance_before,
    balance_after,
    reason,
    idempotency_key,
    performed_by,
    created_at
  )
  SELECT
    sm.id,
    sm.product_id,
    p.sku,
    p.name,
    sm.batch_id,
    sb.lot_code,
    sm.movement_type,
    sm.quantity,
    sm.balance_before,
    sm.balance_after,
    sm.reason,
    sm.idempotency_key,
    sm.performed_by,
    sm.created_at
  FROM stock_movements sm
  JOIN tmp_archive_movement_ids ids ON ids.id = sm.id
  JOIN products p ON p.id = sm.product_id
  LEFT JOIN stock_batches sb ON sb.id = sm.batch_id;

  DELETE sma
    FROM stock_movement_allocations sma
    JOIN tmp_archive_movement_ids ids ON ids.id = sma.movement_id;

  DELETE sm
    FROM stock_movements sm
    JOIN tmp_archive_movement_ids ids ON ids.id = sm.id;

  SET p_archived = ROW_COUNT();

  INSERT INTO stock_retention_runs(
    cutoff_at,
    archived_movements,
    actor_username
  )
  VALUES(
    p_before,
    p_archived,
    COALESCE(NULLIF(TRIM(p_actor),''),'system')
  );

  DROP TEMPORARY TABLE IF EXISTS tmp_archive_movement_ids;

  COMMIT;
END //
DELIMITER ;
