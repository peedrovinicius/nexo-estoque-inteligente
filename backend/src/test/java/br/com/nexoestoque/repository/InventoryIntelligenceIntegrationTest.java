package br.com.nexoestoque.repository;

import br.com.nexoestoque.model.InventoryIntelligence.AbcItem;
import br.com.nexoestoque.model.InventoryIntelligence.CoverageItem;
import br.com.nexoestoque.model.InventoryIntelligence.OpenPurchaseAgingItem;
import br.com.nexoestoque.model.InventoryIntelligence.SlowMovingItem;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class InventoryIntelligenceIntegrationTest {

    @Test
    void calculatesAbcSlowMovingCoverageAndOpenPurchaseAging() throws Exception {
        String url = System.getenv("NEXO_DB_INTEGRATION_URL");
        assumeTrue(url != null && !url.isBlank(), "MySQL integration URL not configured");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_USER", "root"));
        dataSource.setPassword(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_PASSWORD", "root"));

        long locationId;
        long fastProductId;
        long idleProductId;
        long supplierId;
        long orderId;

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT l.id
                      FROM stock_locations l
                      JOIN warehouses w ON w.id = l.warehouse_id
                     WHERE w.code = 'MAIN'
                     ORDER BY l.id LIMIT 1
                    """);
                 ResultSet rs = statement.executeQuery()) {
                rs.next();
                locationId = rs.getLong(1);
            }

            fastProductId = insertProduct(connection, "INT-FAST", "Produto giro", 20, 5, 10);
            idleProductId = insertProduct(connection, "INT-IDLE", "Produto sem giro", 12, 2, 40);

            insertBatch(connection, fastProductId, locationId, "INT-FAST-LOT", 20, 10);
            insertBatch(connection, idleProductId, locationId, "INT-IDLE-LOT", 12, 40);

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO stock_movements(
                        product_id,movement_type,quantity,balance_before,balance_after,reason,created_at
                    ) VALUES(?, 'EXIT', 15, 35, 20, 'intelligence test', DATE_SUB(NOW(), INTERVAL 10 DAY))
                    """)) {
                statement.setLong(1, fastProductId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO suppliers(name,tax_id,lead_time_days,active)
                    VALUES('Fornecedor inteligência','INT-SUP',5,TRUE)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    supplierId = keys.getLong(1);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_orders(
                        supplier_id,status,source,created_by,expected_at,sent_at
                    ) VALUES(?, 'SENT', 'MANUAL', 'ci',
                             DATE_SUB(CURDATE(), INTERVAL 3 DAY),
                             DATE_SUB(NOW(), INTERVAL 8 DAY))
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, supplierId);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    orderId = keys.getLong(1);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_order_items(
                        purchase_order_id,product_id,quantity,unit_cost,received_quantity
                    ) VALUES(?,?,10,9,2)
                    """)) {
                statement.setLong(1, orderId);
                statement.setLong(2, fastProductId);
                statement.executeUpdate();
            }
        }

        OperationalInsightsRepository repository = new OperationalInsightsRepository(dataSource);

        List<AbcItem> abc = repository.abcAnalysis(500);
        assertThat(abc).anyMatch(item ->
                item.productId() == idleProductId
                        && item.stockValue().compareTo(new java.math.BigDecimal("480")) == 0);
        assertThat(abc.stream().map(AbcItem::abcClass))
                .allMatch(value -> List.of("A", "B", "C").contains(value));

        List<SlowMovingItem> slow = repository.slowMoving(90, 500);
        assertThat(slow).anyMatch(item ->
                item.productId() == idleProductId && item.lastExitAt() == null);
        assertThat(slow).noneMatch(item -> item.productId() == fastProductId);

        List<CoverageItem> coverage = repository.coverage(30, 500);
        assertThat(coverage).anyMatch(item ->
                item.productId() == fastProductId
                        && item.averageDailyConsumption().compareTo(new java.math.BigDecimal("0.500")) == 0
                        && item.coverageDays().compareTo(new java.math.BigDecimal("40.0")) == 0);
        assertThat(coverage).anyMatch(item ->
                item.productId() == idleProductId && item.coverageDays() == null);

        List<OpenPurchaseAgingItem> purchases = repository.openPurchaseAging(500);
        assertThat(purchases).anyMatch(item ->
                item.orderId() == orderId
                        && item.overdueDays() >= 3
                        && item.pendingQuantity().compareTo(new java.math.BigDecimal("8.000")) == 0);
    }

    private long insertProduct(
            Connection connection,
            String sku,
            String name,
            int currentStock,
            int minimumStock,
            int costPrice
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO products(
                    sku,name,category,cost_price,sale_price,current_stock,minimum_stock,active
                ) VALUES(?,?,'Inteligência',?,0,?,?,TRUE)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, sku);
            statement.setString(2, name);
            statement.setBigDecimal(3, java.math.BigDecimal.valueOf(costPrice));
            statement.setBigDecimal(4, java.math.BigDecimal.valueOf(currentStock));
            statement.setBigDecimal(5, java.math.BigDecimal.valueOf(minimumStock));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void insertBatch(
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
                """)) {
            statement.setLong(1, productId);
            statement.setLong(2, locationId);
            statement.setString(3, lotCode);
            statement.setBigDecimal(4, java.math.BigDecimal.valueOf(quantity));
            statement.setBigDecimal(5, java.math.BigDecimal.valueOf(unitCost));
            statement.executeUpdate();
        }
    }
}
