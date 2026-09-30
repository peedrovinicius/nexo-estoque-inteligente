package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.StockTransferRequest;
import br.com.nexoestoque.model.StockTransferResult;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

@Repository
public class StockTransferRepository {
    private final DataSource dataSource;

    public StockTransferRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public StockTransferResult transfer(StockTransferRequest request, String actor) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                StockTransferResult existing = findByIdempotency(connection, request.idempotencyKey());
                if (existing != null) {
                    connection.commit();
                    return existing;
                }

                long productId;
                long sourceLocationId;
                String lotCode;
                LocalDate expiresAt;
                BigDecimal sourceQuantity;
                BigDecimal unitCost;
                String sourceQualityStatus;

                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT b.product_id, b.location_id, b.lot_code, b.expires_at,
                               b.quantity, b.unit_cost, b.quality_status
                          FROM stock_batches b
                         WHERE b.id = ?
                         FOR UPDATE
                        """)) {
                    statement.setLong(1, request.sourceBatchId());
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw business("Lote de origem não encontrado");
                        productId = rs.getLong("product_id");
                        sourceLocationId = rs.getLong("location_id");
                        lotCode = rs.getString("lot_code");
                        Date expiry = rs.getDate("expires_at");
                        expiresAt = expiry == null ? null : expiry.toLocalDate();
                        sourceQuantity = rs.getBigDecimal("quantity");
                        unitCost = rs.getBigDecimal("unit_cost");
                        sourceQualityStatus = rs.getString("quality_status");
                    }
                }

                if (!"AVAILABLE".equals(sourceQualityStatus)) {
                    throw business("Lote de origem está em quarentena ou bloqueado");
                }
                if (sourceLocationId == request.destinationLocationId()) {
                    throw business("Origem e destino devem ser diferentes");
                }
                if (sourceQuantity.compareTo(request.quantity()) < 0) {
                    throw business("Quantidade insuficiente no lote de origem");
                }

                ensureActiveDestination(connection, request.destinationLocationId());

                Long destinationBatchId = null;
                String destinationQualityStatus = null;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT id, quality_status
                          FROM stock_batches
                         WHERE product_id = ?
                           AND lot_code = ?
                           AND location_id = ?
                         FOR UPDATE
                        """)) {
                    statement.setLong(1, productId);
                    statement.setString(2, lotCode);
                    statement.setLong(3, request.destinationLocationId());
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            destinationBatchId = rs.getLong("id");
                            destinationQualityStatus = rs.getString("quality_status");
                        }
                    }
                }

                if (destinationBatchId != null && !"AVAILABLE".equals(destinationQualityStatus)) {
                    throw business("Lote no destino está em quarentena ou bloqueado");
                }

                if (destinationBatchId == null) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO stock_batches(
                                product_id, location_id, lot_code, expires_at, quantity, unit_cost
                            )
                            VALUES(?,?,?,?,?,?)
                            """, Statement.RETURN_GENERATED_KEYS)) {
                        statement.setLong(1, productId);
                        statement.setLong(2, request.destinationLocationId());
                        statement.setString(3, lotCode);
                        if (expiresAt == null) statement.setNull(4, Types.DATE);
                        else statement.setDate(4, Date.valueOf(expiresAt));
                        statement.setBigDecimal(5, request.quantity());
                        statement.setBigDecimal(6, unitCost == null ? BigDecimal.ZERO : unitCost);
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Transferência criou lote sem identificador");
                            destinationBatchId = keys.getLong(1);
                        }
                    }
                } else {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE stock_batches
                               SET quantity = quantity + ?
                             WHERE id = ?
                            """)) {
                        statement.setBigDecimal(1, request.quantity());
                        statement.setLong(2, destinationBatchId);
                        statement.executeUpdate();
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE stock_batches
                           SET quantity = quantity - ?
                         WHERE id = ?
                        """)) {
                    statement.setBigDecimal(1, request.quantity());
                    statement.setLong(2, request.sourceBatchId());
                    statement.executeUpdate();
                }

                long transferId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO stock_transfers(
                            product_id, source_batch_id, destination_batch_id,
                            source_location_id, destination_location_id,
                            quantity, reason, idempotency_key, performed_by
                        )
                        VALUES(?,?,?,?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, productId);
                    statement.setLong(2, request.sourceBatchId());
                    statement.setLong(3, destinationBatchId);
                    statement.setLong(4, sourceLocationId);
                    statement.setLong(5, request.destinationLocationId());
                    statement.setBigDecimal(6, request.quantity());
                    String reason = request.reason() == null ? "" : request.reason().trim();
                    if (reason.isEmpty()) statement.setNull(7, Types.VARCHAR);
                    else statement.setString(7, reason);
                    statement.setString(8, request.idempotencyKey().trim());
                    statement.setString(9, actor == null || actor.isBlank() ? "system" : actor);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Transferência criada sem identificador");
                        transferId = keys.getLong(1);
                    }
                }

                connection.commit();
                return findById(transferId);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public StockTransferResult findById(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return findById(connection, id);
        }
    }

    private StockTransferResult findByIdempotency(Connection connection, String key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id FROM stock_transfers WHERE idempotency_key = ?
                """)) {
            statement.setString(1, key.trim());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? findById(connection, rs.getLong(1)) : null;
            }
        }
    }

    private StockTransferResult findById(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, product_id, source_batch_id, destination_batch_id,
                       source_location_id, destination_location_id, quantity,
                       reason, performed_by, created_at
                  FROM stock_transfers
                 WHERE id = ?
                """)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                Timestamp createdAt = rs.getTimestamp("created_at");
                return new StockTransferResult(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getLong("source_batch_id"),
                        rs.getLong("destination_batch_id"),
                        rs.getLong("source_location_id"),
                        rs.getLong("destination_location_id"),
                        rs.getBigDecimal("quantity"),
                        rs.getString("reason"),
                        rs.getString("performed_by"),
                        createdAt == null ? null : createdAt.toLocalDateTime()
                );
            }
        }
    }

    private void ensureActiveDestination(Connection connection, long locationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT l.active AS location_active, w.active AS warehouse_active
                  FROM stock_locations l
                  JOIN warehouses w ON w.id = l.warehouse_id
                 WHERE l.id = ?
                 FOR SHARE
                """)) {
            statement.setLong(1, locationId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw business("Local de destino não encontrado");
                if (!rs.getBoolean("location_active") || !rs.getBoolean("warehouse_active")) {
                    throw business("Local de destino inativo");
                }
            }
        }
    }

    private SQLException business(String message) {
        return new SQLException(message, "45000");
    }
}
