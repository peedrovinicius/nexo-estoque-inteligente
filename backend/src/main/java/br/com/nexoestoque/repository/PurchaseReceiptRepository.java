package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.PurchaseReceiptRequest;
import br.com.nexoestoque.model.PurchaseReceiptResult;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

@Repository
public class PurchaseReceiptRepository {
    private final DataSource dataSource;

    public PurchaseReceiptRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public PurchaseReceiptResult receive(
            long purchaseOrderId,
            PurchaseReceiptRequest request,
            String actor
    ) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                PurchaseReceiptResult existing = findByIdempotency(connection, request.idempotencyKey());
                if (existing != null) {
                    if (!existing.purchaseOrderId().equals(purchaseOrderId)) {
                        throw business("Chave de idempotência já utilizada em outro pedido");
                    }
                    connection.commit();
                    return existing;
                }

                String orderStatus;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT status
                          FROM purchase_orders
                         WHERE id = ?
                         FOR UPDATE
                        """)) {
                    statement.setLong(1, purchaseOrderId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw business("Pedido de compra não encontrado");
                        orderStatus = rs.getString("status");
                    }
                }

                if (!"SENT".equals(orderStatus) && !"PARTIALLY_RECEIVED".equals(orderStatus)) {
                    throw business("O pedido precisa estar enviado para receber mercadoria");
                }

                long productId;
                BigDecimal orderedQuantity;
                BigDecimal receivedQuantity;
                BigDecimal unitCost;

                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT product_id, quantity, received_quantity, unit_cost
                          FROM purchase_order_items
                         WHERE id = ?
                           AND purchase_order_id = ?
                         FOR UPDATE
                        """)) {
                    statement.setLong(1, request.purchaseOrderItemId());
                    statement.setLong(2, purchaseOrderId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw business("Item não pertence ao pedido informado");
                        productId = rs.getLong("product_id");
                        orderedQuantity = rs.getBigDecimal("quantity");
                        receivedQuantity = rs.getBigDecimal("received_quantity");
                        unitCost = rs.getBigDecimal("unit_cost");
                    }
                }

                BigDecimal remainingBefore = orderedQuantity.subtract(receivedQuantity);
                if (remainingBefore.compareTo(request.quantity()) < 0) {
                    throw business("Quantidade recebida supera o saldo pendente do item");
                }

                ensureActiveLocation(connection, request.locationId());

                BigDecimal balanceBefore;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT current_stock
                          FROM products
                         WHERE id = ?
                           AND active = TRUE
                         FOR UPDATE
                        """)) {
                    statement.setLong(1, productId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw business("Produto não encontrado ou inativo");
                        balanceBefore = rs.getBigDecimal("current_stock");
                    }
                }

                Long batchId = null;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT id
                          FROM stock_batches
                         WHERE product_id = ?
                           AND lot_code = ?
                           AND location_id = ?
                         FOR UPDATE
                        """)) {
                    statement.setLong(1, productId);
                    statement.setString(2, request.lotCode().trim());
                    statement.setLong(3, request.locationId());
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) batchId = rs.getLong(1);
                    }
                }

                if (batchId == null) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO stock_batches(
                                product_id, location_id, lot_code, expires_at,
                                quantity, unit_cost
                            )
                            VALUES(?,?,?,?,?,?)
                            """, Statement.RETURN_GENERATED_KEYS)) {
                        statement.setLong(1, productId);
                        statement.setLong(2, request.locationId());
                        statement.setString(3, request.lotCode().trim());
                        if (request.expiresAt() == null) statement.setNull(4, Types.DATE);
                        else statement.setDate(4, Date.valueOf(request.expiresAt()));
                        statement.setBigDecimal(5, request.quantity());
                        statement.setBigDecimal(6, unitCost == null ? BigDecimal.ZERO : unitCost);
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Recebimento criou lote sem identificador");
                            batchId = keys.getLong(1);
                        }
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE stock_batches
                               SET quantity = quantity + ?,
                                   expires_at = COALESCE(?, expires_at),
                                   unit_cost = CASE
                                     WHEN ? IS NULL OR ? = 0 THEN unit_cost
                                     ELSE ?
                                   END
                             WHERE id = ?
                            """)) {
                        statement.setBigDecimal(1, request.quantity());
                        if (request.expiresAt() == null) statement.setNull(2, Types.DATE);
                        else statement.setDate(2, Date.valueOf(request.expiresAt()));
                        statement.setBigDecimal(3, unitCost);
                        statement.setBigDecimal(4, unitCost);
                        statement.setBigDecimal(5, unitCost);
                        statement.setLong(6, batchId);
                        statement.executeUpdate();
                    }
                }

                BigDecimal balanceAfter = balanceBefore.add(request.quantity());
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE products SET current_stock = ? WHERE id = ?
                        """)) {
                    statement.setBigDecimal(1, balanceAfter);
                    statement.setLong(2, productId);
                    statement.executeUpdate();
                }

                long movementId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO stock_movements(
                            product_id, batch_id, movement_type, quantity,
                            balance_before, balance_after, reason,
                            idempotency_key, performed_by
                        )
                        VALUES(?,?,'ENTRY',?,?,?,
                               ?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, productId);
                    statement.setLong(2, batchId);
                    statement.setBigDecimal(3, request.quantity());
                    statement.setBigDecimal(4, balanceBefore);
                    statement.setBigDecimal(5, balanceAfter);
                    statement.setString(6, "Recebimento do pedido #" + purchaseOrderId);
                    statement.setString(7, request.idempotencyKey().trim());
                    statement.setString(8, actor == null || actor.isBlank() ? "system" : actor);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Recebimento criou movimentação sem identificador");
                        movementId = keys.getLong(1);
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO stock_movement_allocations(
                            movement_id, batch_id, quantity
                        )
                        VALUES(?,?,?)
                        """)) {
                    statement.setLong(1, movementId);
                    statement.setLong(2, batchId);
                    statement.setBigDecimal(3, request.quantity());
                    statement.executeUpdate();
                }

                long receiptId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO purchase_receipts(
                            purchase_order_id, location_id, idempotency_key, received_by
                        )
                        VALUES(?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, purchaseOrderId);
                    statement.setLong(2, request.locationId());
                    statement.setString(3, request.idempotencyKey().trim());
                    statement.setString(4, actor == null || actor.isBlank() ? "system" : actor);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Recebimento criado sem identificador");
                        receiptId = keys.getLong(1);
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO purchase_receipt_items(
                            purchase_receipt_id, purchase_order_item_id,
                            batch_id, stock_movement_id, quantity,
                            lot_code, expires_at, unit_cost
                        )
                        VALUES(?,?,?,?,?,?,?,?)
                        """)) {
                    statement.setLong(1, receiptId);
                    statement.setLong(2, request.purchaseOrderItemId());
                    statement.setLong(3, batchId);
                    statement.setLong(4, movementId);
                    statement.setBigDecimal(5, request.quantity());
                    statement.setString(6, request.lotCode().trim());
                    if (request.expiresAt() == null) statement.setNull(7, Types.DATE);
                    else statement.setDate(7, Date.valueOf(request.expiresAt()));
                    statement.setBigDecimal(8, unitCost == null ? BigDecimal.ZERO : unitCost);
                    statement.executeUpdate();
                }

                BigDecimal newReceived = receivedQuantity.add(request.quantity());
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE purchase_order_items
                           SET received_quantity = ?
                         WHERE id = ?
                        """)) {
                    statement.setBigDecimal(1, newReceived);
                    statement.setLong(2, request.purchaseOrderItemId());
                    statement.executeUpdate();
                }

                String newStatus;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT COUNT(*)
                          FROM purchase_order_items
                         WHERE purchase_order_id = ?
                           AND received_quantity < quantity
                        """)) {
                    statement.setLong(1, purchaseOrderId);
                    try (ResultSet rs = statement.executeQuery()) {
                        rs.next();
                        newStatus = rs.getLong(1) == 0 ? "RECEIVED" : "PARTIALLY_RECEIVED";
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE purchase_orders
                           SET status = ?,
                               received_at = CASE WHEN ? = 'RECEIVED'
                                                  THEN CURRENT_TIMESTAMP
                                                  ELSE received_at END
                         WHERE id = ?
                        """)) {
                    statement.setString(1, newStatus);
                    statement.setString(2, newStatus);
                    statement.setLong(3, purchaseOrderId);
                    statement.executeUpdate();
                }

                connection.commit();
                return findById(receiptId);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public PurchaseReceiptResult findById(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return findById(connection, id);
        }
    }

    private PurchaseReceiptResult findByIdempotency(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM purchase_receipts WHERE idempotency_key = ?
                """)) {
            statement.setString(1, key.trim());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? findById(connection, rs.getLong(1)) : null;
            }
        }
    }

    private PurchaseReceiptResult findById(Connection connection, long receiptId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT pr.id AS receipt_id, pr.purchase_order_id,
                       pri.purchase_order_item_id, poi.product_id,
                       pri.batch_id, pri.stock_movement_id, pr.location_id,
                       pri.quantity,
                       (poi.quantity - poi.received_quantity) AS remaining_quantity,
                       sm.balance_before, sm.balance_after,
                       po.status AS order_status, pr.received_by, pr.created_at
                  FROM purchase_receipts pr
                  JOIN purchase_receipt_items pri ON pri.purchase_receipt_id = pr.id
                  JOIN purchase_order_items poi ON poi.id = pri.purchase_order_item_id
                  JOIN purchase_orders po ON po.id = pr.purchase_order_id
                  JOIN stock_movements sm ON sm.id = pri.stock_movement_id
                 WHERE pr.id = ?
                """)) {
            statement.setLong(1, receiptId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                Timestamp created = rs.getTimestamp("created_at");
                return new PurchaseReceiptResult(
                        rs.getLong("receipt_id"),
                        rs.getLong("purchase_order_id"),
                        rs.getLong("purchase_order_item_id"),
                        rs.getLong("product_id"),
                        rs.getLong("batch_id"),
                        rs.getLong("stock_movement_id"),
                        rs.getLong("location_id"),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("remaining_quantity"),
                        rs.getBigDecimal("balance_before"),
                        rs.getBigDecimal("balance_after"),
                        rs.getString("order_status"),
                        rs.getString("received_by"),
                        created == null ? null : created.toLocalDateTime()
                );
            }
        }
    }

    private void ensureActiveLocation(Connection connection, long locationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT l.active AS location_active, w.active AS warehouse_active
                  FROM stock_locations l
                  JOIN warehouses w ON w.id = l.warehouse_id
                 WHERE l.id = ?
                 FOR SHARE
                """)) {
            statement.setLong(1, locationId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw business("Local de recebimento não encontrado");
                if (!rs.getBoolean("location_active") || !rs.getBoolean("warehouse_active")) {
                    throw business("Local de recebimento inativo");
                }
            }
        }
    }

    private SQLException business(String message) {
        return new SQLException(message, "45000");
    }
}
