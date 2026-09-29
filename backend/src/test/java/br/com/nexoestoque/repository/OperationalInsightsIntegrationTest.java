package br.com.nexoestoque.repository;

import br.com.nexoestoque.controller.OperationalInsightsController;
import br.com.nexoestoque.model.OperationalDashboard;
import br.com.nexoestoque.model.OperationalDashboard.ExpiryRiskItem;
import br.com.nexoestoque.model.OperationalDashboard.StockPositionItem;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OperationalInsightsIntegrationTest {

    @Test
    void aggregatesOperationalRisksAndExportsTraceableStockPosition() throws Exception {
        String url = System.getenv("NEXO_DB_INTEGRATION_URL");
        assumeTrue(url != null && !url.isBlank(), "MySQL integration URL not configured");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_USER", "root"));
        dataSource.setPassword(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_PASSWORD", "root"));

        long productId;
        long locationId;

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement location = connection.prepareStatement("""
                    SELECT l.id
                      FROM stock_locations l
                      JOIN warehouses w ON w.id = l.warehouse_id
                     WHERE w.code = 'MAIN'
                     ORDER BY l.id LIMIT 1
                    """);
                 ResultSet rs = location.executeQuery()) {
                rs.next();
                locationId = rs.getLong(1);
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO products(
                        sku, barcode, name, category, cost_price, sale_price,
                        current_stock, minimum_stock, active
                    ) VALUES('OPS-001','7890000000001','Produto operações','Operações',2.50,4.00,6,10,TRUE)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    productId = keys.getLong(1);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO stock_batches(product_id,location_id,lot_code,expires_at,quantity,unit_cost)
                    VALUES
                    (?,?,'OPS-EXPIRED',DATE_SUB(CURDATE(), INTERVAL 1 DAY),2,2.50),
                    (?,?,'OPS-SOON',DATE_ADD(CURDATE(), INTERVAL 7 DAY),4,2.50)
                    """)) {
                statement.setLong(1, productId);
                statement.setLong(2, locationId);
                statement.setLong(3, productId);
                statement.setLong(4, locationId);
                statement.executeUpdate();
            }

            long sessionId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO blind_inventory_sessions(name,status,started_at,closed_at)
                    VALUES('Operações CI','CLOSED',NOW(),NOW())
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    sessionId = keys.getLong(1);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO blind_inventory_counts(
                        session_id,product_id,counted_quantity,system_quantity_snapshot,difference_quantity
                    ) VALUES(?,?,5,6,-1)
                    """)) {
                statement.setLong(1, sessionId);
                statement.setLong(2, productId);
                statement.executeUpdate();
            }
        }

        OperationalInsightsRepository repository = new OperationalInsightsRepository(dataSource);
        OperationalDashboard dashboard = repository.dashboard();

        assertThat(dashboard.totalStock()).isGreaterThanOrEqualTo(new java.math.BigDecimal("6"));
        assertThat(dashboard.stockValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("15"));
        assertThat(dashboard.criticalProducts()).isGreaterThanOrEqualTo(1);
        assertThat(dashboard.expiryRiskBatches()).isGreaterThanOrEqualTo(2);
        assertThat(dashboard.expiredBatches()).isGreaterThanOrEqualTo(1);
        assertThat(dashboard.inventoryAccuracy()).isNotNull();
        assertThat(dashboard.topCritical()).isNotNull();
        assertThat(dashboard.topExpiry()).isNotNull();

        assertThat(repository.criticalStock(20))
                .anyMatch(item -> item.productId() == productId && item.deficit().compareTo(new java.math.BigDecimal("4")) == 0);

        List<ExpiryRiskItem> expiry = repository.expiryRisk(90, 100);
        assertThat(expiry).anyMatch(item -> item.productId() == productId && "EXPIRED".equals(item.riskLevel()));
        assertThat(expiry).anyMatch(item -> item.productId() == productId && "CRITICAL".equals(item.riskLevel()));

        List<StockPositionItem> position = repository.stockPosition(null, 1000);
        assertThat(position).filteredOn(item -> item.productId() == productId).hasSize(2);

        OperationalInsightsController controller = new OperationalInsightsController(repository);
        ResponseEntity<String> csv = controller.stockPositionCsv(null);
        assertThat(csv.getBody()).contains("Produto operações").contains("OPS-EXPIRED").contains("Depósito principal");
        assertThat(csv.getHeaders().getFirst("Content-Disposition")).contains("nexo-posicao-estoque.csv");
    }
}
