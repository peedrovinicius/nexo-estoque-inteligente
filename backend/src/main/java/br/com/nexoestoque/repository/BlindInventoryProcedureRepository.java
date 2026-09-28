package br.com.nexoestoque.repository;

import br.com.nexoestoque.model.BlindInventoryItem;
import br.com.nexoestoque.model.BlindInventorySession;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BlindInventoryProcedureRepository {
    private final DataSource dataSource;

    public BlindInventoryProcedureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public BlindInventorySession create(String name) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_blind_inventory_create(?,?)}")) {
            statement.setString(1, name);
            statement.registerOutParameter(2, Types.BIGINT);
            statement.execute();
            return findById(connection, statement.getLong(2));
        }
    }

    public List<BlindInventorySession> findAll() throws SQLException {
        List<BlindInventorySession> sessions = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_blind_inventory_list()}");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) sessions.add(mapSession(rs));
        }
        return sessions;
    }

    public List<BlindInventoryItem> findItems(long sessionId) throws SQLException {
        List<BlindInventoryItem> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_blind_inventory_items(?)}")) {
            statement.setLong(1, sessionId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Timestamp countedAt = rs.getTimestamp("counted_at");
                    items.add(new BlindInventoryItem(
                            rs.getLong("product_id"),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            rs.getBigDecimal("counted_quantity"),
                            rs.getBigDecimal("system_quantity_snapshot"),
                            rs.getBigDecimal("difference_quantity"),
                            countedAt == null ? null : countedAt.toLocalDateTime(),
                            rs.getBoolean("revealed")
                    ));
                }
            }
        }
        return items;
    }

    public void count(long sessionId, long productId, BigDecimal countedQuantity) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_blind_inventory_count(?,?,?)}")) {
            statement.setLong(1, sessionId);
            statement.setLong(2, productId);
            statement.setBigDecimal(3, countedQuantity);
            statement.execute();
        }
    }

    public BlindInventorySession close(long sessionId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             CallableStatement statement = connection.prepareCall("{call sp_blind_inventory_close(?)}")) {
            statement.setLong(1, sessionId);
            statement.execute();
            return findById(connection, sessionId);
        }
    }

    private BlindInventorySession findById(Connection connection, long sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.id, s.name, s.status, s.started_at, s.closed_at,
                       COUNT(c.id) AS counted_items,
                       CASE
                           WHEN s.status = 'CLOSED' THEN SUM(CASE WHEN c.difference_quantity <> 0 THEN 1 ELSE 0 END)
                           ELSE 0
                       END AS divergent_items
                FROM blind_inventory_sessions s
                LEFT JOIN blind_inventory_counts c ON c.session_id = s.id
                WHERE s.id = ?
                GROUP BY s.id, s.name, s.status, s.started_at, s.closed_at
                """)) {
            statement.setLong(1, sessionId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return mapSession(rs);
            }
        }
        throw new SQLException("Sessão de inventário não encontrada", "45000");
    }

    private BlindInventorySession mapSession(ResultSet rs) throws SQLException {
        Timestamp startedAt = rs.getTimestamp("started_at");
        Timestamp closedAt = rs.getTimestamp("closed_at");
        return new BlindInventorySession(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("status"),
                startedAt == null ? null : startedAt.toLocalDateTime(),
                closedAt == null ? null : closedAt.toLocalDateTime(),
                rs.getInt("counted_items"),
                rs.getInt("divergent_items")
        );
    }
}
