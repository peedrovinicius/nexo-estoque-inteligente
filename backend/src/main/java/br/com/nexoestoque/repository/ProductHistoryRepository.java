package br.com.nexoestoque.repository;

import br.com.nexoestoque.model.Product;
import br.com.nexoestoque.model.ProductChangeHistory;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ProductHistoryRepository {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public ProductHistoryRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    public void record(long productId, String actionType, String actor, Product before, Product after)
            throws SQLException {
        String beforeJson = before == null ? null : objectMapper.writeValueAsString(before);
        String afterJson = after == null ? null : objectMapper.writeValueAsString(after);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO product_change_history(
                         product_id, action_type, actor_username, before_snapshot, after_snapshot
                     )
                     VALUES(?, ?, ?, CAST(? AS JSON), CAST(? AS JSON))
                     """)) {
            statement.setLong(1, productId);
            statement.setString(2, actionType);
            statement.setString(3, actor);
            if (beforeJson == null) statement.setNull(4, Types.VARCHAR);
            else statement.setString(4, beforeJson);
            if (afterJson == null) statement.setNull(5, Types.VARCHAR);
            else statement.setString(5, afterJson);
            statement.executeUpdate();
        }
    }

    public List<ProductChangeHistory> findByProduct(long productId, int limit) throws SQLException {
        List<ProductChangeHistory> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, product_id, action_type, actor_username,
                            CAST(before_snapshot AS CHAR) AS before_snapshot,
                            CAST(after_snapshot AS CHAR) AS after_snapshot,
                            created_at
                     FROM product_change_history
                     WHERE product_id = ?
                     ORDER BY created_at DESC, id DESC
                     LIMIT ?
                     """)) {
            statement.setLong(1, productId);
            statement.setInt(2, Math.max(1, Math.min(limit, 100)));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    items.add(new ProductChangeHistory(
                            rs.getLong("id"),
                            rs.getLong("product_id"),
                            rs.getString("action_type"),
                            rs.getString("actor_username"),
                            rs.getString("before_snapshot"),
                            rs.getString("after_snapshot"),
                            createdAt == null ? null : createdAt.toLocalDateTime()
                    ));
                }
            }
        }
        return items;
    }
}
