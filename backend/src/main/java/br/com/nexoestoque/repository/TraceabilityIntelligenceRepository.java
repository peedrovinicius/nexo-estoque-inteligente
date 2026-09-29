package br.com.nexoestoque.repository;

import br.com.nexoestoque.model.TraceabilityIntelligence.*;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class TraceabilityIntelligenceRepository {
    private final DataSource dataSource;

    public TraceabilityIntelligenceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<SupplierPerformanceItem> supplierPerformance(int limit) throws SQLException {
        int safeLimit = normalizeLimit(limit, 500);
        List<SupplierPerformanceItem> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT s.id AS supplier_id,
                            s.name AS supplier_name,
                            COUNT(ops.order_id) AS order_count,
                            SUM(CASE WHEN ops.status = 'RECEIVED' THEN 1 ELSE 0 END) AS received_order_count,
                            SUM(CASE WHEN ops.status IN ('SENT','PARTIALLY_RECEIVED') THEN 1 ELSE 0 END) AS open_order_count,
                            SUM(CASE
                                WHEN ops.status IN ('SENT','PARTIALLY_RECEIVED')
                                 AND ops.expected_at IS NOT NULL
                                 AND ops.expected_at < CURDATE()
                                THEN 1 ELSE 0 END) AS delayed_open_order_count,
                            COALESCE(SUM(CASE WHEN ops.status <> 'CANCELLED' THEN ops.ordered_quantity ELSE 0 END),0) AS ordered_quantity,
                            COALESCE(SUM(CASE WHEN ops.status <> 'CANCELLED' THEN ops.received_quantity ELSE 0 END),0) AS received_quantity,
                            COALESCE(SUM(CASE WHEN ops.status <> 'CANCELLED' THEN ops.purchased_value ELSE 0 END),0) AS purchased_value,
                            AVG(CASE
                                WHEN ops.status = 'RECEIVED' AND ops.sent_at IS NOT NULL AND ops.received_at IS NOT NULL
                                THEN TIMESTAMPDIFF(HOUR, ops.sent_at, ops.received_at) / 24.0
                            END) AS average_lead_days,
                            SUM(CASE
                                WHEN ops.status = 'RECEIVED' AND ops.expected_at IS NOT NULL
                                 AND DATE(ops.received_at) <= ops.expected_at THEN 1 ELSE 0 END) AS on_time_orders,
                            SUM(CASE
                                WHEN ops.status = 'RECEIVED' AND ops.expected_at IS NOT NULL THEN 1 ELSE 0 END) AS dated_received_orders
                       FROM suppliers s
                       LEFT JOIN (
                           SELECT po.id AS order_id,
                                  po.supplier_id,
                                  po.status,
                                  po.expected_at,
                                  po.sent_at,
                                  po.received_at,
                                  COALESCE(SUM(poi.quantity),0) AS ordered_quantity,
                                  COALESCE(SUM(poi.received_quantity),0) AS received_quantity,
                                  COALESCE(SUM(poi.quantity * poi.unit_cost),0) AS purchased_value
                             FROM purchase_orders po
                             LEFT JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
                            GROUP BY po.id, po.supplier_id, po.status, po.expected_at, po.sent_at, po.received_at
                       ) ops ON ops.supplier_id = s.id
                      GROUP BY s.id, s.name
                      ORDER BY delayed_open_order_count DESC, purchased_value DESC, s.name
                      LIMIT ?
                     """)) {
            statement.setInt(1, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    BigDecimal ordered = rs.getBigDecimal("ordered_quantity");
                    BigDecimal received = rs.getBigDecimal("received_quantity");
                    int datedReceived = rs.getInt("dated_received_orders");
                    int onTime = rs.getInt("on_time_orders");
                    BigDecimal averageLead = rs.getBigDecimal("average_lead_days");
                    items.add(new SupplierPerformanceItem(
                            rs.getLong("supplier_id"),
                            rs.getString("supplier_name"),
                            rs.getInt("order_count"),
                            rs.getInt("received_order_count"),
                            rs.getInt("open_order_count"),
                            rs.getInt("delayed_open_order_count"),
                            ordered,
                            received,
                            percent(received, ordered),
                            averageLead == null ? null : averageLead.setScale(1, RoundingMode.HALF_UP),
                            datedReceived == 0 ? null : percent(BigDecimal.valueOf(onTime), BigDecimal.valueOf(datedReceived)),
                            rs.getBigDecimal("purchased_value")
                    ));
                }
            }
        }
        return items;
    }

    public List<StockFlowItem> stockFlow(int days, String category, String query, int limit) throws SQLException {
        int safeDays = Math.max(1, Math.min(days, 3650));
        int safeLimit = normalizeLimit(limit, 1000);
        String normalizedCategory = normalizeText(category);
        String normalizedQuery = normalizeText(query);
        String queryPattern = normalizedQuery == null ? null : "%" + normalizedQuery + "%";
        List<StockFlowItem> items = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.id,
                            p.sku,
                            p.name,
                            p.category,
                            p.current_stock,
                            COALESCE(SUM(CASE WHEN h.movement_type = 'ENTRY' THEN h.quantity ELSE 0 END),0) AS entry_quantity,
                            COALESCE(SUM(CASE WHEN h.movement_type = 'EXIT' THEN h.quantity ELSE 0 END),0) AS exit_quantity,
                            COALESCE(SUM(CASE WHEN h.movement_type = 'RETURN' THEN h.quantity ELSE 0 END),0) AS return_quantity,
                            COALESCE(SUM(CASE WHEN h.movement_type = 'ADJUSTMENT' THEN h.quantity ELSE 0 END),0) AS adjustment_quantity,
                            COUNT(h.movement_id) AS movement_count,
                            MAX(h.created_at) AS last_movement_at
                       FROM products p
                       LEFT JOIN (
                           SELECT id AS movement_id, product_id, movement_type, quantity, created_at
                             FROM stock_movements
                            WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                           UNION ALL
                           SELECT original_movement_id AS movement_id, product_id, movement_type, quantity, created_at
                             FROM stock_movements_archive
                            WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                       ) h ON h.product_id = p.id
                      WHERE p.active = TRUE
                        AND (? IS NULL OR p.category = ?)
                        AND (? IS NULL OR p.name LIKE ? OR p.sku LIKE ?)
                      GROUP BY p.id, p.sku, p.name, p.category, p.current_stock
                      ORDER BY exit_quantity DESC, movement_count DESC, p.name
                      LIMIT ?
                     """)) {
            statement.setInt(1, safeDays);
            statement.setInt(2, safeDays);
            setNullableText(statement, 3, normalizedCategory);
            setNullableText(statement, 4, normalizedCategory);
            setNullableText(statement, 5, normalizedQuery);
            setNullableText(statement, 6, queryPattern);
            setNullableText(statement, 7, queryPattern);
            statement.setInt(8, safeLimit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    BigDecimal entry = rs.getBigDecimal("entry_quantity");
                    BigDecimal exit = rs.getBigDecimal("exit_quantity");
                    BigDecimal returned = rs.getBigDecimal("return_quantity");
                    BigDecimal adjustment = rs.getBigDecimal("adjustment_quantity");
                    Timestamp last = rs.getTimestamp("last_movement_at");
                    items.add(new StockFlowItem(
                            rs.getLong("id"),
                            rs.getString("sku"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getBigDecimal("current_stock"),
                            entry,
                            exit,
                            returned,
                            adjustment,
                            entry.add(returned).add(adjustment).subtract(exit),
                            rs.getInt("movement_count"),
                            last == null ? null : last.toLocalDateTime()
                    ));
                }
            }
        }
        return items;
    }

    public List<MovementLedgerItem> movementLedger(
            String query,
            String movementType,
            String actor,
            LocalDate from,
            LocalDate to,
            int limit
    ) throws SQLException {
        int safeLimit = normalizeLimit(limit, 5000);
        String normalizedQuery = normalizeText(query);
        String queryPattern = normalizedQuery == null ? null : "%" + normalizedQuery + "%";
        String normalizedType = normalizeText(movementType);
        if (normalizedType != null) normalizedType = normalizedType.toUpperCase();
        String normalizedActor = normalizeText(actor);
        String actorPattern = normalizedActor == null ? null : "%" + normalizedActor + "%";

        List<MovementLedgerItem> items = new ArrayList<>();
        String sql = """
                SELECT *
                  FROM (
                    SELECT sm.id AS movement_id,
                           'LIVE' AS source,
                           sm.product_id,
                           p.sku,
                           p.name AS product_name,
                           COALESCE(
                               NULLIF(GROUP_CONCAT(DISTINCT alloc_batch.lot_code ORDER BY alloc_batch.lot_code SEPARATOR ', '),''),
                               direct_batch.lot_code
                           ) AS lots,
                           sm.movement_type,
                           sm.quantity,
                           sm.balance_before,
                           sm.balance_after,
                           sm.reason,
                           sm.performed_by,
                           sm.created_at
                      FROM stock_movements sm
                      JOIN products p ON p.id = sm.product_id
                      LEFT JOIN stock_batches direct_batch ON direct_batch.id = sm.batch_id
                      LEFT JOIN stock_movement_allocations a ON a.movement_id = sm.id
                      LEFT JOIN stock_batches alloc_batch ON alloc_batch.id = a.batch_id
                     GROUP BY sm.id, sm.product_id, p.sku, p.name, direct_batch.lot_code,
                              sm.movement_type, sm.quantity, sm.balance_before, sm.balance_after,
                              sm.reason, sm.performed_by, sm.created_at
                    UNION ALL
                    SELECT sma.original_movement_id AS movement_id,
                           'ARCHIVE' AS source,
                           sma.product_id,
                           sma.product_sku AS sku,
                           sma.product_name,
                           COALESCE(
                               NULLIF(GROUP_CONCAT(DISTINCT saa.lot_code ORDER BY saa.lot_code SEPARATOR ', '),''),
                               sma.lot_code
                           ) AS lots,
                           sma.movement_type,
                           sma.quantity,
                           sma.balance_before,
                           sma.balance_after,
                           sma.reason,
                           sma.performed_by,
                           sma.created_at
                      FROM stock_movements_archive sma
                      LEFT JOIN stock_movement_allocations_archive saa
                        ON saa.original_movement_id = sma.original_movement_id
                     GROUP BY sma.original_movement_id, sma.product_id, sma.product_sku, sma.product_name,
                              sma.lot_code, sma.movement_type, sma.quantity, sma.balance_before,
                              sma.balance_after, sma.reason, sma.performed_by, sma.created_at
                  ) ledger
                 WHERE (? IS NULL OR ledger.product_name LIKE ? OR ledger.sku LIKE ? OR ledger.lots LIKE ?)
                   AND (? IS NULL OR ledger.movement_type = ?)
                   AND (? IS NULL OR ledger.performed_by LIKE ?)
                   AND (? IS NULL OR DATE(ledger.created_at) >= ?)
                   AND (? IS NULL OR DATE(ledger.created_at) <= ?)
                 ORDER BY ledger.created_at DESC, ledger.movement_id DESC
                 LIMIT ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableText(statement, 1, normalizedQuery);
            setNullableText(statement, 2, queryPattern);
            setNullableText(statement, 3, queryPattern);
            setNullableText(statement, 4, queryPattern);
            setNullableText(statement, 5, normalizedType);
            setNullableText(statement, 6, normalizedType);
            setNullableText(statement, 7, normalizedActor);
            setNullableText(statement, 8, actorPattern);
            setNullableDate(statement, 9, from);
            setNullableDate(statement, 10, from);
            setNullableDate(statement, 11, to);
            setNullableDate(statement, 12, to);
            statement.setInt(13, safeLimit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Timestamp created = rs.getTimestamp("created_at");
                    items.add(new MovementLedgerItem(
                            rs.getLong("movement_id"),
                            rs.getString("source"),
                            rs.getLong("product_id"),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            rs.getString("lots"),
                            rs.getString("movement_type"),
                            rs.getBigDecimal("quantity"),
                            rs.getBigDecimal("balance_before"),
                            rs.getBigDecimal("balance_after"),
                            rs.getString("reason"),
                            rs.getString("performed_by"),
                            created == null ? null : created.toLocalDateTime()
                    ));
                }
            }
        }
        return items;
    }

    public List<LotTraceEvent> lotTrace(String lotCode, int limit) throws SQLException {
        String normalizedLot = normalizeText(lotCode);
        if (normalizedLot == null) return List.of();
        int safeLimit = normalizeLimit(limit, 1000);
        List<LotTraceEvent> items = new ArrayList<>();

        String sql = """
                SELECT *
                  FROM (
                    SELECT 'RECEIPT' AS event_type,
                           p.id AS product_id,
                           p.sku,
                           p.name AS product_name,
                           pri.lot_code,
                           pri.quantity,
                           CONCAT('Pedido #', pr.purchase_order_id, ' · ', s.name) AS origin,
                           CONCAT(w.name, ' / ', l.code) AS destination,
                           CONCAT('Recebimento #', pr.id) AS reference_text,
                           pr.received_by AS actor,
                           pr.created_at
                      FROM purchase_receipt_items pri
                      JOIN purchase_receipts pr ON pr.id = pri.purchase_receipt_id
                      JOIN purchase_order_items poi ON poi.id = pri.purchase_order_item_id
                      JOIN purchase_orders po ON po.id = pr.purchase_order_id
                      JOIN suppliers s ON s.id = po.supplier_id
                      JOIN products p ON p.id = poi.product_id
                      JOIN stock_locations l ON l.id = pr.location_id
                      JOIN warehouses w ON w.id = l.warehouse_id
                     WHERE pri.lot_code = ?
                    UNION ALL
                    SELECT 'TRANSFER' AS event_type,
                           p.id,
                           p.sku,
                           p.name,
                           source_batch.lot_code,
                           st.quantity,
                           CONCAT(source_w.name, ' / ', source_l.code),
                           CONCAT(dest_w.name, ' / ', dest_l.code),
                           CONCAT('Transferência #', st.id),
                           st.performed_by,
                           st.created_at
                      FROM stock_transfers st
                      JOIN products p ON p.id = st.product_id
                      JOIN stock_batches source_batch ON source_batch.id = st.source_batch_id
                      JOIN stock_locations source_l ON source_l.id = st.source_location_id
                      JOIN warehouses source_w ON source_w.id = source_l.warehouse_id
                      JOIN stock_locations dest_l ON dest_l.id = st.destination_location_id
                      JOIN warehouses dest_w ON dest_w.id = dest_l.warehouse_id
                     WHERE source_batch.lot_code = ?
                    UNION ALL
                    SELECT 'MOVEMENT' AS event_type,
                           p.id,
                           p.sku,
                           p.name,
                           b.lot_code,
                           a.quantity,
                           NULL,
                           NULL,
                           CONCAT('Movimentação #', sm.id, ' · ', sm.movement_type),
                           sm.performed_by,
                           sm.created_at
                      FROM stock_movement_allocations a
                      JOIN stock_movements sm ON sm.id = a.movement_id
                      JOIN stock_batches b ON b.id = a.batch_id
                      JOIN products p ON p.id = sm.product_id
                     WHERE b.lot_code = ?
                    UNION ALL
                    SELECT 'ARCHIVED_MOVEMENT' AS event_type,
                           sma.product_id,
                           sma.product_sku,
                           sma.product_name,
                           saa.lot_code,
                           saa.quantity,
                           NULL,
                           NULL,
                           CONCAT('Movimentação arquivada #', sma.original_movement_id, ' · ', sma.movement_type),
                           sma.performed_by,
                           sma.created_at
                      FROM stock_movement_allocations_archive saa
                      JOIN stock_movements_archive sma
                        ON sma.original_movement_id = saa.original_movement_id
                     WHERE saa.lot_code = ?
                  ) timeline
                 ORDER BY created_at DESC
                 LIMIT ?
                """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedLot);
            statement.setString(2, normalizedLot);
            statement.setString(3, normalizedLot);
            statement.setString(4, normalizedLot);
            statement.setInt(5, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Timestamp created = rs.getTimestamp("created_at");
                    items.add(new LotTraceEvent(
                            rs.getString("event_type"),
                            rs.getLong("product_id"),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            rs.getString("lot_code"),
                            rs.getBigDecimal("quantity"),
                            rs.getString("origin"),
                            rs.getString("destination"),
                            rs.getString("reference_text"),
                            rs.getString("actor"),
                            created == null ? null : created.toLocalDateTime()
                    ));
                }
            }
        }
        return items;
    }

    public InventoryIntegrityReport integrityReport(int issueLimit) throws SQLException {
        int safeLimit = normalizeLimit(issueLimit, 500);
        List<IntegrityIssue> issues = new ArrayList<>();
        int mismatch = 0;
        int negativeProducts = 0;
        int negativeBatches = 0;
        int inactiveWithStock = 0;
        int receiptMismatch = 0;

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT p.id, p.sku, p.name, p.current_stock,
                           COALESCE(SUM(b.quantity),0) AS batch_stock
                      FROM products p
                      LEFT JOIN stock_batches b ON b.product_id = p.id
                     GROUP BY p.id, p.sku, p.name, p.current_stock
                    HAVING ABS(p.current_stock - COALESCE(SUM(b.quantity),0)) > 0.0005
                     ORDER BY ABS(p.current_stock - COALESCE(SUM(b.quantity),0)) DESC
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    mismatch++;
                    addIssue(issues, safeLimit, new IntegrityIssue(
                            "stock-" + rs.getLong("id"),
                            "CRITICAL",
                            "STOCK_RECONCILIATION",
                            rs.getString("name"),
                            "Saldo do produto " + rs.getBigDecimal("current_stock")
                                    + " difere da soma dos lotes " + rs.getBigDecimal("batch_stock")
                    ));
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, sku, name, current_stock
                      FROM products
                     WHERE current_stock < 0
                     ORDER BY current_stock
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    negativeProducts++;
                    addIssue(issues, safeLimit, new IntegrityIssue(
                            "negative-product-" + rs.getLong("id"),
                            "CRITICAL",
                            "NEGATIVE_PRODUCT_BALANCE",
                            rs.getString("name"),
                            "Saldo negativo: " + rs.getBigDecimal("current_stock")
                    ));
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT b.id, p.name, b.lot_code, b.quantity
                      FROM stock_batches b
                      JOIN products p ON p.id = b.product_id
                     WHERE b.quantity < 0
                     ORDER BY b.quantity
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    negativeBatches++;
                    addIssue(issues, safeLimit, new IntegrityIssue(
                            "negative-batch-" + rs.getLong("id"),
                            "CRITICAL",
                            "NEGATIVE_BATCH_BALANCE",
                            rs.getString("name") + " · lote " + rs.getString("lot_code"),
                            "Saldo negativo no lote: " + rs.getBigDecimal("quantity")
                    ));
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, sku, name, current_stock
                      FROM products
                     WHERE active = FALSE AND current_stock <> 0
                     ORDER BY ABS(current_stock) DESC
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    inactiveWithStock++;
                    addIssue(issues, safeLimit, new IntegrityIssue(
                            "inactive-stock-" + rs.getLong("id"),
                            "WARNING",
                            "INACTIVE_WITH_STOCK",
                            rs.getString("name"),
                            "Produto inativo mantém saldo " + rs.getBigDecimal("current_stock")
                    ));
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT poi.id,
                           p.name,
                           poi.received_quantity,
                           COALESCE(SUM(pri.quantity),0) AS receipt_quantity
                      FROM purchase_order_items poi
                      JOIN products p ON p.id = poi.product_id
                      LEFT JOIN purchase_receipt_items pri ON pri.purchase_order_item_id = poi.id
                     GROUP BY poi.id, p.name, poi.received_quantity
                    HAVING ABS(poi.received_quantity - COALESCE(SUM(pri.quantity),0)) > 0.0005
                     ORDER BY ABS(poi.received_quantity - COALESCE(SUM(pri.quantity),0)) DESC
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    receiptMismatch++;
                    addIssue(issues, safeLimit, new IntegrityIssue(
                            "receipt-" + rs.getLong("id"),
                            "WARNING",
                            "RECEIPT_RECONCILIATION",
                            rs.getString("name"),
                            "Recebido no pedido " + rs.getBigDecimal("received_quantity")
                                    + " difere dos comprovantes " + rs.getBigDecimal("receipt_quantity")
                    ));
                }
            }
        }

        return new InventoryIntegrityReport(
                LocalDateTime.now(),
                mismatch,
                negativeProducts,
                negativeBatches,
                inactiveWithStock,
                receiptMismatch,
                issues
        );
    }

    private void addIssue(List<IntegrityIssue> issues, int limit, IntegrityIssue issue) {
        if (issues.size() < limit) issues.add(issue);
    }

    private BigDecimal percent(BigDecimal value, BigDecimal total) {
        if (total == null || total.signum() == 0) return BigDecimal.ZERO;
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private int normalizeLimit(int limit, int max) {
        if (limit <= 0) return Math.min(100, max);
        return Math.min(limit, max);
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void setNullableText(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value);
    }

    private void setNullableDate(PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) statement.setNull(index, Types.DATE);
        else statement.setDate(index, Date.valueOf(value));
    }
}
