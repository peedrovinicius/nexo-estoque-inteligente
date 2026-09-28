package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.BatchEntryRequest;
import br.com.nexoestoque.dto.FefoExitRequest;
import br.com.nexoestoque.model.MovementAllocation;
import br.com.nexoestoque.model.StockBatch;
import br.com.nexoestoque.model.StockOperationResult;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class StockBatchProcedureRepository {
    private final DataSource dataSource;

    public StockBatchProcedureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public StockOperationResult entry(BatchEntryRequest request) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_stock_batch_entry(?,?,?,?,?,?,?,?,?,?)}")) {
            statement.setLong(1, request.productId());
            statement.setString(2, request.lotCode());
            if (request.expiresAt() == null) statement.setNull(3, Types.DATE);
            else statement.setDate(3, Date.valueOf(request.expiresAt()));
            statement.setBigDecimal(4, request.quantity());
            statement.setBigDecimal(5, request.unitCost());
            statement.setString(6, request.reason());
            statement.registerOutParameter(7, Types.BIGINT);
            statement.registerOutParameter(8, Types.BIGINT);
            statement.registerOutParameter(9, Types.DECIMAL);
            statement.registerOutParameter(10, Types.DECIMAL);
            statement.execute();

            long movementId = statement.getLong(7);
            long batchId = statement.getLong(8);

            return new StockOperationResult(
                    movementId,
                    batchId,
                    statement.getBigDecimal(9),
                    statement.getBigDecimal(10),
                    findAllocations(connection, movementId)
            );
        }
    }

    public StockOperationResult exitFefo(FefoExitRequest request) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_stock_exit_fefo(?,?,?,?,?)}")) {
            statement.setLong(1, request.productId());
            statement.setBigDecimal(2, request.quantity());
            statement.setString(3, request.reason());
            statement.registerOutParameter(4, Types.BIGINT);
            statement.registerOutParameter(5, Types.DECIMAL);
            statement.execute();

            long movementId = statement.getLong(4);
            return new StockOperationResult(
                    movementId,
                    null,
                    statement.getBigDecimal(5),
                    findBalanceAfter(connection, movementId),
                    findAllocations(connection, movementId)
            );
        }
    }

    public List<StockBatch> findBatches(Long productId) throws SQLException {
        List<StockBatch> batches = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_stock_batch_list(?)}")) {
            if (productId == null) statement.setNull(1, Types.BIGINT);
            else statement.setLong(1, productId);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Date expiresAt = rs.getDate("expires_at");
                    Timestamp receivedAt = rs.getTimestamp("received_at");
                    Integer daysToExpiry = (Integer) rs.getObject("days_to_expiry");

                    batches.add(new StockBatch(
                            rs.getLong("id"),
                            rs.getLong("product_id"),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            rs.getString("lot_code"),
                            expiresAt == null ? null : expiresAt.toLocalDate(),
                            rs.getBigDecimal("quantity"),
                            rs.getBigDecimal("unit_cost"),
                            receivedAt == null ? null : receivedAt.toLocalDateTime(),
                            daysToExpiry,
                            rs.getString("expiry_status"),
                            rs.getInt("fefo_position")
                    ));
                }
            }
        }
        return batches;
    }

    public List<MovementAllocation> findAllocations(long movementId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return findAllocations(connection, movementId);
        }
    }

    private List<MovementAllocation> findAllocations(Connection connection, long movementId) throws SQLException {
        List<MovementAllocation> allocations = new ArrayList<>();
        try (CallableStatement statement = connection.prepareCall("{call sp_stock_movement_allocations(?)}")) {
            statement.setLong(1, movementId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Date expiresAt = rs.getDate("expires_at");
                    allocations.add(new MovementAllocation(
                            rs.getLong("id"),
                            rs.getLong("movement_id"),
                            rs.getLong("batch_id"),
                            rs.getString("lot_code"),
                            expiresAt == null ? null : expiresAt.toLocalDate(),
                            rs.getBigDecimal("quantity")
                    ));
                }
            }
        }
        return allocations;
    }

    private java.math.BigDecimal findBalanceAfter(Connection connection, long movementId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance_after FROM stock_movements WHERE id = ?")) {
            statement.setLong(1, movementId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getBigDecimal("balance_after");
            }
        }
        return null;
    }
}
