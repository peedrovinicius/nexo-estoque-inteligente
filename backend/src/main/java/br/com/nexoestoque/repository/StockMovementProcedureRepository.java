package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.StockMovementRequest;
import br.com.nexoestoque.model.StockMovement;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class StockMovementProcedureRepository {
    private final DataSource dataSource;

    public StockMovementProcedureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public StockMovement create(StockMovementRequest request) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_stock_move(?,?,?,?,?,?,?,?)}")) {
            statement.setLong(1, request.productId());
            statement.setString(2, request.movementType());
            statement.setBigDecimal(3, request.quantity());
            statement.setString(4, request.reason());
            statement.setString(5, request.idempotencyKey());
            statement.registerOutParameter(6, Types.BIGINT);
            statement.registerOutParameter(7, Types.DECIMAL);
            statement.registerOutParameter(8, Types.DECIMAL);
            statement.execute();

            long movementId = statement.getLong(6);
            BigDecimal balanceBefore = statement.getBigDecimal(7);
            BigDecimal balanceAfter = statement.getBigDecimal(8);

            return findById(connection, movementId, balanceBefore, balanceAfter);
        }
    }

    public List<StockMovement> findRecent(int limit) throws SQLException {
        List<StockMovement> movements = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_stock_movement_list(?)}")) {
            statement.setInt(1, Math.max(1, Math.min(limit, 100)));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    movements.add(map(rs));
                }
            }
        }
        return movements;
    }

    private StockMovement findById(
            Connection connection,
            long movementId,
            BigDecimal balanceBefore,
            BigDecimal balanceAfter
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sm.id, sm.product_id, p.name AS product_name, sm.movement_type,
                       sm.quantity, sm.balance_before, sm.balance_after, sm.reason, sm.created_at
                FROM stock_movements sm
                JOIN products p ON p.id = sm.product_id
                WHERE sm.id = ?
                """)) {
            statement.setLong(1, movementId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }

        return new StockMovement(
                movementId,
                null,
                null,
                null,
                null,
                balanceBefore,
                balanceAfter,
                null,
                null
        );
    }

    private StockMovement map(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new StockMovement(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("movement_type"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("balance_before"),
                rs.getBigDecimal("balance_after"),
                rs.getString("reason"),
                createdAt == null ? null : createdAt.toLocalDateTime()
        );
    }
}
