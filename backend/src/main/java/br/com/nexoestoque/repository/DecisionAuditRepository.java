package br.com.nexoestoque.repository;

import br.com.nexoestoque.model.DecisionAuditEntry;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DecisionAuditRepository {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public DecisionAuditRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    public void record(
            Long productId,
            String decisionType,
            Object input,
            Object output,
            String ruleVersion,
            String actorUsername
    ) throws SQLException {
        String inputJson = objectMapper.writeValueAsString(input);
        String outputJson = objectMapper.writeValueAsString(output);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO decision_audit(
                         product_id, decision_type, input_snapshot, output_snapshot,
                         rule_version, actor_username
                     )
                     VALUES(?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?)
                     """)) {
            if (productId == null) statement.setNull(1, Types.BIGINT);
            else statement.setLong(1, productId);
            statement.setString(2, decisionType);
            statement.setString(3, inputJson);
            statement.setString(4, outputJson);
            statement.setString(5, ruleVersion);
            statement.setString(6, actorUsername);
            statement.executeUpdate();
        }
    }

    public List<DecisionAuditEntry> findRecent(int limit) throws SQLException {
        List<DecisionAuditEntry> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id, product_id, decision_type,
                            CAST(input_snapshot AS CHAR) AS input_snapshot,
                            CAST(output_snapshot AS CHAR) AS output_snapshot,
                            rule_version, actor_username, created_at
                     FROM decision_audit
                     ORDER BY created_at DESC, id DESC
                     LIMIT ?
                     """)) {
            statement.setInt(1, Math.max(1, Math.min(limit, 100)));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    Long productId = (Long) rs.getObject("product_id");
                    items.add(new DecisionAuditEntry(
                            rs.getLong("id"),
                            productId,
                            rs.getString("decision_type"),
                            rs.getString("input_snapshot"),
                            rs.getString("output_snapshot"),
                            rs.getString("rule_version"),
                            rs.getString("actor_username"),
                            createdAt == null ? null : createdAt.toLocalDateTime()
                    ));
                }
            }
        }
        return items;
    }
}
