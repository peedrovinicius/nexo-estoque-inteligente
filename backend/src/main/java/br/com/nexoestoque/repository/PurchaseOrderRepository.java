package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.PurchaseOrderCreateRequest;
import br.com.nexoestoque.dto.PurchaseOrderItemRequest;
import br.com.nexoestoque.model.PurchaseOrderDetails;
import br.com.nexoestoque.model.PurchaseOrderItem;
import br.com.nexoestoque.model.PurchaseOrderSummary;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

@Repository
public class PurchaseOrderRepository {
    private final DataSource dataSource;

    public PurchaseOrderRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public PurchaseOrderDetails create(
            PurchaseOrderCreateRequest request,
            String source,
            String ruleVersion,
            String createdBy
    ) throws SQLException {
        Set<Long> productIds = new HashSet<>();
        for (PurchaseOrderItemRequest item : request.items()) {
            if (!productIds.add(item.productId())) {
                throw business("O mesmo produto não pode aparecer duas vezes no pedido");
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureActiveSupplier(connection, request.supplierId());
                ensureActiveProducts(connection, productIds);

                long orderId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO purchase_orders(
                            supplier_id, source, rule_version, created_by, expected_at, notes
                        )
                        VALUES(?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, request.supplierId());
                    statement.setString(2, source);
                    if (ruleVersion == null) statement.setNull(3, Types.VARCHAR);
                    else statement.setString(3, ruleVersion);
                    statement.setString(4, createdBy);
                    if (request.expectedAt() == null) statement.setNull(5, Types.DATE);
                    else statement.setDate(5, Date.valueOf(request.expectedAt()));
                    String notes = request.notes() == null ? "" : request.notes().trim();
                    if (notes.isEmpty()) statement.setNull(6, Types.VARCHAR);
                    else statement.setString(6, notes);
                    statement.executeUpdate();

                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Pedido criado sem identificador");
                        orderId = keys.getLong(1);
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO purchase_order_items(
                            purchase_order_id, product_id, quantity, unit_cost
                        )
                        VALUES(?,?,?,?)
                        """)) {
                    for (PurchaseOrderItemRequest item : request.items()) {
                        statement.setLong(1, orderId);
                        statement.setLong(2, item.productId());
                        statement.setBigDecimal(3, item.quantity());
                        statement.setBigDecimal(4, item.unitCost() == null ? BigDecimal.ZERO : item.unitCost());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }

                connection.commit();
                return findById(orderId);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<PurchaseOrderSummary> findAll(String status) throws SQLException {
        List<PurchaseOrderSummary> items = new ArrayList<>();
        String sql = """
                SELECT po.id, po.supplier_id, s.name AS supplier_name, po.status,
                       po.source, po.rule_version, po.created_by, po.expected_at,
                       po.notes, po.created_at, po.updated_at,
                       COUNT(poi.id) AS item_count,
                       COALESCE(SUM(poi.quantity * poi.unit_cost),0) AS total_amount
                  FROM purchase_orders po
                  JOIN suppliers s ON s.id = po.supplier_id
                  LEFT JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
                 WHERE (? IS NULL OR po.status = ?)
                 GROUP BY po.id, po.supplier_id, s.name, po.status, po.source,
                          po.rule_version, po.created_by, po.expected_at, po.notes,
                          po.created_at, po.updated_at
                 ORDER BY po.created_at DESC, po.id DESC
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
            if (normalized == null) {
                statement.setNull(1, Types.VARCHAR);
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(1, normalized);
                statement.setString(2, normalized);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) items.add(mapSummary(rs));
            }
        }
        return items;
    }

    public PurchaseOrderDetails findById(long id) throws SQLException {
        PurchaseOrderSummary summary;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT po.id, po.supplier_id, s.name AS supplier_name, po.status,
                            po.source, po.rule_version, po.created_by, po.expected_at,
                            po.notes, po.created_at, po.updated_at,
                            COUNT(poi.id) AS item_count,
                            COALESCE(SUM(poi.quantity * poi.unit_cost),0) AS total_amount
                       FROM purchase_orders po
                       JOIN suppliers s ON s.id = po.supplier_id
                       LEFT JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
                      WHERE po.id = ?
                      GROUP BY po.id, po.supplier_id, s.name, po.status, po.source,
                               po.rule_version, po.created_by, po.expected_at, po.notes,
                               po.created_at, po.updated_at
                     """)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                summary = mapSummary(rs);
            }
        }

        List<PurchaseOrderItem> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT poi.id, poi.purchase_order_id, poi.product_id,
                            p.sku, p.name AS product_name, poi.quantity,
                            poi.unit_cost, poi.received_quantity
                       FROM purchase_order_items poi
                       JOIN products p ON p.id = poi.product_id
                      WHERE poi.purchase_order_id = ?
                      ORDER BY poi.id
                     """)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new PurchaseOrderItem(
                            rs.getLong("id"),
                            rs.getLong("purchase_order_id"),
                            rs.getLong("product_id"),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            rs.getBigDecimal("quantity"),
                            rs.getBigDecimal("unit_cost"),
                            rs.getBigDecimal("received_quantity")
                    ));
                }
            }
        }

        return new PurchaseOrderDetails(summary, items);
    }

    public PurchaseOrderDetails updateStatus(long id, String nextStatus) throws SQLException {
        String next = nextStatus.toUpperCase(Locale.ROOT);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String current;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT status FROM purchase_orders WHERE id = ? FOR UPDATE
                        """)) {
                    statement.setLong(1, id);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) return null;
                        current = rs.getString("status");
                    }
                }

                if (!allowedTransition(current, next)) {
                    throw business("Transição de status inválida: " + current + " -> " + next);
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE purchase_orders
                           SET status = ?,
                               sent_at = CASE WHEN ? = 'SENT' AND sent_at IS NULL THEN CURRENT_TIMESTAMP ELSE sent_at END,
                               received_at = CASE WHEN ? = 'RECEIVED' THEN CURRENT_TIMESTAMP ELSE received_at END
                         WHERE id = ?
                        """)) {
                    statement.setString(1, next);
                    statement.setString(2, next);
                    statement.setString(3, next);
                    statement.setLong(4, id);
                    statement.executeUpdate();
                }

                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
        return findById(id);
    }

    private boolean allowedTransition(String current, String next) {
        if (Objects.equals(current, next)) return true;
        return switch (current) {
            case "DRAFT" -> Set.of("SENT", "CANCELLED").contains(next);
            case "SENT" -> Set.of("PARTIALLY_RECEIVED", "RECEIVED").contains(next);
            case "PARTIALLY_RECEIVED" -> "RECEIVED".equals(next);
            default -> false;
        };
    }

    private void ensureActiveSupplier(Connection connection, long supplierId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT active FROM suppliers WHERE id = ? FOR SHARE
                """)) {
            statement.setLong(1, supplierId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw business("Fornecedor não encontrado");
                if (!rs.getBoolean("active")) throw business("Fornecedor inativo");
            }
        }
    }

    private void ensureActiveProducts(Connection connection, Set<Long> productIds) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT active FROM products WHERE id = ?
                """)) {
            for (Long productId : productIds) {
                statement.setLong(1, productId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) throw business("Produto não encontrado: " + productId);
                    if (!rs.getBoolean("active")) throw business("Produto inativo: " + productId);
                }
            }
        }
    }

    private SQLException business(String message) {
        return new SQLException(message, "45000");
    }

    private PurchaseOrderSummary mapSummary(ResultSet rs) throws SQLException {
        Date expected = rs.getDate("expected_at");
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return new PurchaseOrderSummary(
                rs.getLong("id"),
                rs.getLong("supplier_id"),
                rs.getString("supplier_name"),
                rs.getString("status"),
                rs.getString("source"),
                rs.getString("rule_version"),
                rs.getString("created_by"),
                expected == null ? null : expected.toLocalDate(),
                rs.getString("notes"),
                rs.getInt("item_count"),
                rs.getBigDecimal("total_amount"),
                created == null ? null : created.toLocalDateTime(),
                updated == null ? null : updated.toLocalDateTime()
        );
    }
}
