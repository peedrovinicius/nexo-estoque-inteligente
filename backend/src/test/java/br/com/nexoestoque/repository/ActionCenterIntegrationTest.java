package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.BatchReplenishmentRequest;
import br.com.nexoestoque.dto.StockReservationRequest;
import br.com.nexoestoque.model.ActionCenter.*;
import br.com.nexoestoque.model.PurchaseOrderDetails;
import br.com.nexoestoque.service.ActionCenterService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

class ActionCenterIntegrationTest {

    @Test
    void coversReplenishmentBatchApprovalReservationAndDailyActions() throws Exception {
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

        try (Connection connection = dataSource.getConnection()) {
            locationId = scalarLong(connection, """
                    SELECT l.id
                      FROM stock_locations l
                      JOIN warehouses w ON w.id = l.warehouse_id
                     WHERE w.code='MAIN'
                     ORDER BY l.id
                     LIMIT 1
                    """);

            supplierId = insertSupplier(connection);
            productId = insertProduct(connection);
            insertBatch(connection, productId, locationId);
            insertExitHistory(connection, productId);
            insertHistoricalPurchase(connection, supplierId, productId);
        }

        ActionCenterRepository actionRepository = new ActionCenterRepository(dataSource);
        PurchaseOrderRepository purchaseOrders = new PurchaseOrderRepository(dataSource);
        DecisionAuditRepository audit = mock(DecisionAuditRepository.class);
        ActionCenterService service = new ActionCenterService(actionRepository, purchaseOrders, audit);

        List<ReplenishmentSuggestion> suggestions = actionRepository.replenishmentSuggestions(
                30,
                BigDecimal.ZERO,
                100
        );

        ReplenishmentSuggestion initial = suggestions.stream()
                .filter(item -> item.productId() == productId)
                .findFirst()
                .orElseThrow();

        assertThat(initial.supplierId()).isEqualTo(supplierId);
        assertThat(initial.averageDailyDemand()).isEqualByComparingTo("1.000");
        assertThat(initial.recommendedQuantity()).isEqualByComparingTo("19.000");
        assertThat(initial.estimatedCost()).isEqualByComparingTo("85.50");
        assertThat(initial.supplierRequired()).isFalse();

        StockReservation reservation = actionRepository.createReservation(
                new StockReservationRequest(
                        productId,
                        new BigDecimal("1"),
                        "PEDIDO-CLIENTE-001",
                        "Reserva para teste",
                        LocalDateTime.now().plusHours(12)
                ),
                "operador-ci"
        );

        assertThat(reservation.status()).isEqualTo("ACTIVE");
        assertThat(reservation.availableToPromise()).isEqualByComparingTo("1.000");

        assertThatThrownBy(() -> actionRepository.createReservation(
                new StockReservationRequest(
                        productId,
                        new BigDecimal("1.5"),
                        "PEDIDO-CLIENTE-002",
                        null,
                        LocalDateTime.now().plusHours(24)
                ),
                "operador-ci"
        )).isInstanceOf(SQLException.class)
          .hasMessageContaining("Estoque disponível insuficiente");

        BatchDraftResult batch = service.createBatchDrafts(
                new BatchReplenishmentRequest(
                        List.of(productId),
                        30,
                        BigDecimal.ZERO
                ),
                "operador-ci"
        );

        assertThat(batch.createdOrders()).isEqualTo(1);
        assertThat(batch.requestedProducts()).isEqualTo(1);
        long orderId = batch.orders().getFirst().purchaseOrderId();

        assertThatThrownBy(() -> purchaseOrders.updateStatus(orderId, "SENT"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("precisa de aprovação");

        PurchaseApprovalItem pending = actionRepository.requestApproval(orderId, "operador-ci");
        assertThat(pending.approvalStatus()).isEqualTo("PENDING");

        DailyActionQueue queue = actionRepository.dailyActions();
        assertThat(queue.items()).anyMatch(item ->
                item.key().equals("approval-" + orderId)
                        && "PURCHASE_APPROVAL".equals(item.type()));
        assertThat(queue.items()).anyMatch(item ->
                item.key().equals("reservation-" + reservation.id())
                        && "RESERVATION".equals(item.type()));
        assertThat(queue.items()).noneMatch(item ->
                item.key().equals("replenishment-" + productId)
                        && "REPLENISHMENT".equals(item.type()));

        PurchaseApprovalItem approved = actionRepository.decideApproval(
                orderId,
                true,
                "admin-ci",
                "Compra necessária"
        );
        assertThat(approved.approvalStatus()).isEqualTo("APPROVED");
        assertThat(approved.decidedBy()).isEqualTo("admin-ci");

        PurchaseOrderDetails sent = purchaseOrders.updateStatus(orderId, "SENT");
        assertThat(sent.order().status()).isEqualTo("SENT");

        StockReservation cancelled = actionRepository.cancelReservation(reservation.id(), "operador-ci");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");

        List<StockReservation> active = actionRepository.reservations("ACTIVE");
        assertThat(active).noneMatch(item -> item.id() == reservation.id());
    }

    private long insertSupplier(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO suppliers(
                    name,tax_id,lead_time_days,active
                ) VALUES(
                    'Fornecedor Action Center','ACTION-SUP-001',5,TRUE
                )
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.executeUpdate();
            return generatedKey(statement);
        }
    }

    private long insertProduct(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO products(
                    sku,name,category,cost_price,sale_price,current_stock,minimum_stock,active
                ) VALUES(
                    'ACTION-001','Produto Action Center','Operação',5,8,2,8,TRUE
                )
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.executeUpdate();
            return generatedKey(statement);
        }
    }

    private void insertBatch(Connection connection, long productId, long locationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO stock_batches(
                    product_id,location_id,lot_code,quantity,unit_cost
                ) VALUES(?,?,'ACTION-LOT-001',2,5)
                """)) {
            statement.setLong(1, productId);
            statement.setLong(2, locationId);
            statement.executeUpdate();
        }
    }

    private void insertExitHistory(Connection connection, long productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO stock_movements(
                    product_id,movement_type,quantity,balance_before,balance_after,
                    reason,idempotency_key,performed_by,created_at
                ) VALUES(
                    ?,'EXIT',30,32,2,'histórico para ação',
                    'action-history-exit-001','ci',
                    DATE_SUB(NOW(), INTERVAL 5 DAY)
                )
                """)) {
            statement.setLong(1, productId);
            statement.executeUpdate();
        }
    }

    private void insertHistoricalPurchase(
            Connection connection,
            long supplierId,
            long productId
    ) throws SQLException {
        long orderId;
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO purchase_orders(
                    supplier_id,status,source,created_by,expected_at,
                    created_at,sent_at,received_at
                ) VALUES(
                    ?,'RECEIVED','MANUAL','ci',
                    DATE_SUB(CURDATE(), INTERVAL 6 DAY),
                    DATE_SUB(NOW(), INTERVAL 15 DAY),
                    DATE_SUB(NOW(), INTERVAL 10 DAY),
                    DATE_SUB(NOW(), INTERVAL 7 DAY)
                )
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, supplierId);
            statement.executeUpdate();
            orderId = generatedKey(statement);
        }

        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO purchase_order_items(
                    purchase_order_id,product_id,quantity,unit_cost,received_quantity
                ) VALUES(?,?,20,4.50,20)
                """)) {
            statement.setLong(1, orderId);
            statement.setLong(2, productId);
            statement.executeUpdate();
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
