package br.com.nexoestoque.repository;

import br.com.nexoestoque.model.TraceabilityIntelligence.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.*;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TraceabilityIntelligenceIntegrationTest {

    @Test
    void buildsSupplierFlowLedgerLotTraceAndIntegrityReport() throws Exception {
        String url = System.getenv("NEXO_DB_INTEGRATION_URL");
        assumeTrue(url != null && !url.isBlank(), "MySQL integration URL not configured");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_USER", "root"));
        dataSource.setPassword(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_PASSWORD", "root"));

        long locationId;
        long supplierId;
        long productId;
        long mismatchProductId;
        long batchId;
        long entryMovementId;
        long exitMovementId;
        long orderId;
        long orderItemId;

        try (Connection connection = dataSource.getConnection()) {
            locationId = scalarLong(connection, """
                    SELECT l.id
                      FROM stock_locations l
                      JOIN warehouses w ON w.id = l.warehouse_id
                     WHERE w.code='MAIN'
                     ORDER BY l.id LIMIT 1
                    """);

            supplierId = insertSupplier(connection);
            productId = insertProduct(connection, "TRC-001", "Produto rastreável", 10);
            mismatchProductId = insertProduct(connection, "TRC-MISMATCH", "Produto divergente", 5);
            batchId = insertBatch(connection, productId, locationId, "TRACE-LOT-001", 10, 7);

            entryMovementId = insertMovement(connection, productId, batchId, "ENTRY", 10, 0, 10, "ci-entry", "trace-ci");
            exitMovementId = insertMovement(connection, productId, null, "EXIT", 4, 10, 6, "ci-exit", "trace-ci");

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO stock_movement_allocations(movement_id,batch_id,quantity)
                    VALUES(?,?,4)
                    """)) {
                statement.setLong(1, exitMovementId);
                statement.setLong(2, batchId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_orders(
                        supplier_id,status,source,created_by,expected_at,sent_at,received_at
                    ) VALUES(
                        ?,'RECEIVED','MANUAL','trace-ci',
                        DATE_SUB(CURDATE(), INTERVAL 1 DAY),
                        DATE_SUB(NOW(), INTERVAL 4 DAY),
                        DATE_SUB(NOW(), INTERVAL 2 DAY)
                    )
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, supplierId);
                statement.executeUpdate();
                orderId = generatedKey(statement);
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_order_items(
                        purchase_order_id,product_id,quantity,unit_cost,received_quantity
                    ) VALUES(?,?,10,7,10)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, orderId);
                statement.setLong(2, productId);
                statement.executeUpdate();
                orderItemId = generatedKey(statement);
            }

            long receiptId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_receipts(
                        purchase_order_id,location_id,idempotency_key,received_by,created_at
                    ) VALUES(?,?,'trace-receipt-001','trace-ci',DATE_SUB(NOW(), INTERVAL 2 DAY))
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, orderId);
                statement.setLong(2, locationId);
                statement.executeUpdate();
                receiptId = generatedKey(statement);
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_receipt_items(
                        purchase_receipt_id,purchase_order_item_id,batch_id,stock_movement_id,
                        quantity,lot_code,unit_cost,created_at
                    ) VALUES(?,?,?,?,10,'TRACE-LOT-001',7,DATE_SUB(NOW(), INTERVAL 2 DAY))
                    """)) {
                statement.setLong(1, receiptId);
                statement.setLong(2, orderItemId);
                statement.setLong(3, batchId);
                statement.setLong(4, entryMovementId);
                statement.executeUpdate();
            }
        }

        TraceabilityIntelligenceRepository repository = new TraceabilityIntelligenceRepository(dataSource);

        List<SupplierPerformanceItem> suppliers = repository.supplierPerformance(100);
        assertThat(suppliers).anyMatch(item ->
                item.supplierId() == supplierId
                        && item.receivedOrderCount() == 1
                        && item.fillRatePercent().compareTo(new java.math.BigDecimal("100.0")) == 0
                        && item.purchasedValue().compareTo(new java.math.BigDecimal("70.00")) == 0);

        List<StockFlowItem> flow = repository.stockFlow(30, "Rastreabilidade", "TRC-001", 100);
        assertThat(flow).anyMatch(item ->
                item.productId() == productId
                        && item.entryQuantity().compareTo(new java.math.BigDecimal("10.000")) == 0
                        && item.exitQuantity().compareTo(new java.math.BigDecimal("4.000")) == 0
                        && item.movementCount() == 2);

        List<MovementLedgerItem> ledger = repository.movementLedger(
                "TRACE-LOT-001", null, "trace-ci",
                LocalDate.now().minusDays(30), LocalDate.now(), 100
        );
        assertThat(ledger).anyMatch(item ->
                item.movementId() == exitMovementId
                        && "EXIT".equals(item.movementType())
                        && item.lots() != null
                        && item.lots().contains("TRACE-LOT-001"));

        List<LotTraceEvent> timeline = repository.lotTrace("TRACE-LOT-001", 100);
        assertThat(timeline).anyMatch(item -> "RECEIPT".equals(item.eventType()) && item.productId() == productId);
        assertThat(timeline).anyMatch(item -> "MOVEMENT".equals(item.eventType()) && item.productId() == productId);

        InventoryIntegrityReport integrity = repository.integrityReport(500);
        assertThat(integrity.stockMismatchCount()).isGreaterThanOrEqualTo(1);
        assertThat(integrity.issues()).anyMatch(item ->
                item.key().equals("stock-" + mismatchProductId)
                        && "STOCK_RECONCILIATION".equals(item.type()));
    }

    private long insertSupplier(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO suppliers(name,tax_id,lead_time_days,active)
                VALUES('Fornecedor rastreável','TRACE-SUP-001',4,TRUE)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.executeUpdate();
            return generatedKey(statement);
        }
    }

    private long insertProduct(Connection connection, String sku, String name, int currentStock) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO products(
                    sku,name,category,cost_price,sale_price,current_stock,minimum_stock,active
                ) VALUES(?,?,'Rastreabilidade',7,10,?,2,TRUE)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, sku);
            statement.setString(2, name);
            statement.setBigDecimal(3, java.math.BigDecimal.valueOf(currentStock));
            statement.executeUpdate();
            return generatedKey(statement);
        }
    }

    private long insertBatch(
            Connection connection,
            long productId,
            long locationId,
            String lotCode,
            int quantity,
            int unitCost
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO stock_batches(product_id,location_id,lot_code,quantity,unit_cost)
                VALUES(?,?,?,?,?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, productId);
            statement.setLong(2, locationId);
            statement.setString(3, lotCode);
            statement.setBigDecimal(4, java.math.BigDecimal.valueOf(quantity));
            statement.setBigDecimal(5, java.math.BigDecimal.valueOf(unitCost));
            statement.executeUpdate();
            return generatedKey(statement);
        }
    }

    private long insertMovement(
            Connection connection,
            long productId,
            Long batchId,
            String type,
            int quantity,
            int before,
            int after,
            String reason,
            String actor
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO stock_movements(
                    product_id,batch_id,movement_type,quantity,balance_before,balance_after,
                    reason,idempotency_key,performed_by,created_at
                ) VALUES(?,?,?,?,?,?,?,?,?,DATE_SUB(NOW(), INTERVAL 2 DAY))
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, productId);
            if (batchId == null) statement.setNull(2, Types.BIGINT);
            else statement.setLong(2, batchId);
            statement.setString(3, type);
            statement.setBigDecimal(4, java.math.BigDecimal.valueOf(quantity));
            statement.setBigDecimal(5, java.math.BigDecimal.valueOf(before));
            statement.setBigDecimal(6, java.math.BigDecimal.valueOf(after));
            statement.setString(7, reason);
            statement.setString(8, "trace-" + type.toLowerCase() + "-" + productId);
            statement.setString(9, actor);
            statement.executeUpdate();
            return generatedKey(statement);
        }
    }

    private long scalarLong(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long generatedKey(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }
}
