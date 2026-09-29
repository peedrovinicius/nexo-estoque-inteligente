package br.com.nexoestoque.repository;

import br.com.nexoestoque.model.DemandPlanning.PlanningItem;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DemandPlanningIntegrationTest {

    @Test
    void derivesForecastSafetyStockReorderPointAndProjectedRisk() throws Exception {
        String url = System.getenv("NEXO_DB_INTEGRATION_URL");
        assumeTrue(url != null && !url.isBlank(), "MySQL integration URL not configured");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_USER", "root"));
        dataSource.setPassword(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_PASSWORD", "root"));

        long productId;
        long supplierId;
        long orderId;

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO products(
                        sku,name,category,cost_price,sale_price,current_stock,minimum_stock,active
                    ) VALUES('PLAN-001','Produto planejamento','Planejamento',10,20,12,4,TRUE)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    productId = keys.getLong(1);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO suppliers(name,tax_id,lead_time_days,active)
                    VALUES('Fornecedor planejamento','PLAN-SUP',5,TRUE)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    supplierId = keys.getLong(1);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_orders(
                        supplier_id,status,source,created_by,sent_at,received_at
                    ) VALUES(?, 'RECEIVED','MANUAL','ci',
                             DATE_SUB(NOW(),INTERVAL 6 DAY),NOW())
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
                    ) VALUES(?,?,20,10,20)
                    """)) {
                statement.setLong(1, orderId);
                statement.setLong(2, productId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO stock_movements(
                        product_id,movement_type,quantity,balance_before,balance_after,reason,created_at
                    ) VALUES
                    (?, 'EXIT', 4, 20, 16, 'plan', DATE_SUB(NOW(),INTERVAL 2 DAY)),
                    (?, 'EXIT', 2, 16, 14, 'plan', DATE_SUB(NOW(),INTERVAL 5 DAY)),
                    (?, 'EXIT', 2, 14, 12, 'plan', DATE_SUB(NOW(),INTERVAL 8 DAY)),
                    (?, 'EXIT', 2, 12, 10, 'plan', DATE_SUB(NOW(),INTERVAL 20 DAY))
                    """)) {
                statement.setLong(1, productId);
                statement.setLong(2, productId);
                statement.setLong(3, productId);
                statement.setLong(4, productId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO stock_reservations(
                        product_id,quantity,reference_code,status,reserved_by,expires_at
                    ) VALUES(?,3,'PLAN-RES','ACTIVE','ci',DATE_ADD(NOW(),INTERVAL 2 DAY))
                    """)) {
                statement.setLong(1, productId);
                statement.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_orders(
                        supplier_id,status,source,created_by,expected_at,sent_at
                    ) VALUES(?, 'SENT','MANUAL','ci',
                             DATE_ADD(CURDATE(),INTERVAL 4 DAY),NOW())
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, supplierId);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    long incomingOrderId = keys.getLong(1);
                    try (PreparedStatement item = connection.prepareStatement("""
                            INSERT INTO purchase_order_items(
                                purchase_order_id,product_id,quantity,unit_cost,received_quantity
                            ) VALUES(?,?,5,10,0)
                            """)) {
                        item.setLong(1, incomingOrderId);
                        item.setLong(2, productId);
                        item.executeUpdate();
                    }
                }
            }
        }

        DemandPlanningRepository repository = new DemandPlanningRepository(dataSource);
        List<PlanningItem> items = repository.planning(500);

        PlanningItem item = items.stream()
                .filter(value -> value.productId() == productId)
                .findFirst()
                .orElseThrow();

        assertThat(item.demand7Days()).isEqualByComparingTo(new BigDecimal("6.000"));
        assertThat(item.demand30Days()).isEqualByComparingTo(new BigDecimal("10.000"));
        assertThat(item.forecastDailyDemand()).isGreaterThan(BigDecimal.ZERO);
        assertThat(item.dailyDemandStdDev()).isGreaterThan(BigDecimal.ZERO);
        assertThat(item.supplierLeadTimeDays()).isEqualTo(6);
        assertThat(item.availableToPromise()).isEqualByComparingTo(new BigDecimal("9.000"));
        assertThat(item.incomingQuantity()).isEqualByComparingTo(new BigDecimal("5.000"));
        assertThat(item.safetyStock()).isGreaterThan(BigDecimal.ZERO);
        assertThat(item.reorderPoint()).isGreaterThan(item.safetyStock());
        assertThat(item.targetStock()).isGreaterThan(item.reorderPoint());
        assertThat(item.confidenceLevel()).isIn("LOW", "MEDIUM", "HIGH");

        assertThat(repository.summary().totalProducts()).isGreaterThanOrEqualTo(1);
    }
}
