package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.OperationalExceptionRequest;
import br.com.nexoestoque.dto.ReplenishmentPolicyRequest;
import br.com.nexoestoque.model.ActionCenter.ReplenishmentSuggestion;
import br.com.nexoestoque.model.OperationalGovernance.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OperationalGovernanceIntegrationTest {

    @Test
    void appliesPoliciesExceptionsCycleCountAcknowledgementAndSla() throws Exception {
        String url=System.getenv("NEXO_DB_INTEGRATION_URL");
        assumeTrue(url!=null&&!url.isBlank(),"MySQL integration URL not configured");

        DriverManagerDataSource dataSource=new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_USER","root"));
        dataSource.setPassword(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_PASSWORD","root"));

        long locationId;
        long historicalSupplierId;
        long preferredSupplierId;
        long productId;

        try(Connection connection=dataSource.getConnection()){
            locationId=scalarLong(connection,"""
                    SELECT l.id
                      FROM stock_locations l
                      JOIN warehouses w ON w.id=l.warehouse_id
                     WHERE w.code='MAIN'
                     ORDER BY l.id
                     LIMIT 1
                    """);
            historicalSupplierId=insertSupplier(connection,"GOV-HIST","Fornecedor histórico",7);
            preferredSupplierId=insertSupplier(connection,"GOV-PREF","Fornecedor preferencial",3);
            productId=insertProduct(connection);
            insertBatch(connection,productId,locationId);
            insertExitHistory(connection,productId);
            insertHistoricalPurchase(connection,historicalSupplierId,productId);
            insertOldDivergentCount(connection,productId);
        }

        ActionCenterRepository actionCenter=new ActionCenterRepository(dataSource);
        OperationalGovernanceRepository governance=new OperationalGovernanceRepository(dataSource,actionCenter);

        ReplenishmentPolicy policy=governance.upsertPolicy(
                productId,
                new ReplenishmentPolicyRequest(
                        true,5,BigDecimal.ZERO,
                        new BigDecimal("6"),new BigDecimal("4"),preferredSupplierId
                ),
                "admin-ci"
        );

        assertThat(policy.preferredSupplierId()).isEqualTo(preferredSupplierId);
        assertThat(policy.targetCoverageDays()).isEqualTo(5);
        assertThat(policy.orderMultiple()).isEqualByComparingTo("4");

        ReplenishmentSuggestion suggestion=actionCenter.replenishmentSuggestions(
                30,BigDecimal.ZERO,100
        ).stream().filter(item->item.productId()==productId).findFirst().orElseThrow();

        assertThat(suggestion.supplierId()).isEqualTo(preferredSupplierId);
        assertThat(suggestion.supplierLeadTimeDays()).isEqualTo(3);
        assertThat(suggestion.recommendedQuantity()).isEqualByComparingTo("8.000");
        assertThat(suggestion.estimatedCost()).isEqualByComparingTo("40.00");

        OperationalException pause=governance.createException(
                new OperationalExceptionRequest(
                        productId,"REPLENISHMENT_PAUSE",
                        "Compra suspensa para conferência",
                        LocalDateTime.now().plusHours(6)
                ),
                "operador-ci"
        );
        assertThat(pause.status()).isEqualTo("ACTIVE");
        assertThat(actionCenter.replenishmentSuggestions(30,BigDecimal.ZERO,100))
                .noneMatch(item->item.productId()==productId);

        OperationalException cancelled=governance.cancelException(pause.id(),"operador-ci");
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(actionCenter.replenishmentSuggestions(30,BigDecimal.ZERO,100))
                .anyMatch(item->item.productId()==productId);

        CycleCountSuggestion cycle=governance.cycleCounts(500).stream()
                .filter(item->item.productId()==productId)
                .findFirst().orElseThrow();
        assertThat(cycle.frequencyDays()).isEqualTo(14);
        assertThat(cycle.daysSinceLastCount()).isGreaterThanOrEqualTo(19);
        assertThat(cycle.daysOverdue()).isGreaterThanOrEqualTo(5);
        assertThat(cycle.priority()).isIn("HIGH","CRITICAL");

        OperationalException countPause=governance.createException(
                new OperationalExceptionRequest(
                        productId,"COUNTING_PAUSE",
                        "Inventário físico indisponível hoje",
                        LocalDateTime.now().plusHours(2)
                ),
                "operador-ci"
        );
        CycleCountSuggestion paused=governance.cycleCounts(500).stream()
                .filter(item->item.productId()==productId)
                .findFirst().orElseThrow();
        assertThat(paused.paused()).isTrue();
        assertThat(paused.priority()).isEqualTo("PAUSED");
        governance.cancelException(countPause.id(),"operador-ci");

        List<GovernedAction> actions=governance.governedActions(false);
        GovernedAction replenishment=actions.stream()
                .filter(item->item.key().equals("replenishment-"+productId))
                .findFirst().orElseThrow();
        assertThat(replenishment.status()).isEqualTo("OPEN");
        assertThat(replenishment.slaHours()).isEqualTo(4);

        try(Connection connection=dataSource.getConnection();
            PreparedStatement statement=connection.prepareStatement("""
                    UPDATE operational_action_states
                       SET first_seen_at=DATE_SUB(NOW(),INTERVAL 6 HOUR)
                     WHERE action_key=?
                    """)){
            statement.setString(1,replenishment.key());
            statement.executeUpdate();
        }

        GovernedAction breached=governance.governedActions(false).stream()
                .filter(item->item.key().equals(replenishment.key()))
                .findFirst().orElseThrow();
        assertThat(breached.slaBreached()).isTrue();
        assertThat(breached.ageHours()).isGreaterThanOrEqualTo(5);

        GovernedAction acknowledged=governance.acknowledgeAction(
                replenishment.key(),"operador-ci","Compra em análise"
        );
        assertThat(acknowledged.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(acknowledged.acknowledgedBy()).isEqualTo("operador-ci");
        assertThat(acknowledged.acknowledgementNote()).isEqualTo("Compra em análise");
        assertThat(acknowledged.slaBreached()).isTrue();

        GovernanceSummary summary=governance.summary();
        assertThat(summary.acknowledgedActions()).isGreaterThanOrEqualTo(1);
        assertThat(summary.slaBreaches()).isGreaterThanOrEqualTo(1);
        assertThat(summary.dueCycleCounts()).isGreaterThanOrEqualTo(1);
    }

    private long insertSupplier(Connection connection,String taxId,String name,int leadTime) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("""
                INSERT INTO suppliers(name,tax_id,lead_time_days,active)
                VALUES(?,?,?,TRUE)
                """,Statement.RETURN_GENERATED_KEYS)){
            statement.setString(1,name);
            statement.setString(2,taxId);
            statement.setInt(3,leadTime);
            statement.executeUpdate();
            return generatedKey(statement);
        }
    }

    private long insertProduct(Connection connection) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("""
                INSERT INTO products(
                    sku,name,category,cost_price,sale_price,current_stock,minimum_stock,active
                ) VALUES(
                    'GOV-001','Produto governado','Governança',5,8,2,2,TRUE
                )
                """,Statement.RETURN_GENERATED_KEYS)){
            statement.executeUpdate();
            return generatedKey(statement);
        }
    }

    private void insertBatch(Connection connection,long productId,long locationId) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("""
                INSERT INTO stock_batches(product_id,location_id,lot_code,quantity,unit_cost)
                VALUES(?,?,'GOV-LOT-001',2,5)
                """)){
            statement.setLong(1,productId);
            statement.setLong(2,locationId);
            statement.executeUpdate();
        }
    }

    private void insertExitHistory(Connection connection,long productId) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("""
                INSERT INTO stock_movements(
                    product_id,movement_type,quantity,balance_before,balance_after,
                    reason,idempotency_key,performed_by,created_at
                ) VALUES(
                    ?,'EXIT',30,32,2,'histórico governança',
                    'gov-history-exit-001','ci',
                    DATE_SUB(NOW(),INTERVAL 5 DAY)
                )
                """)){
            statement.setLong(1,productId);
            statement.executeUpdate();
        }
    }

    private void insertHistoricalPurchase(Connection connection,long supplierId,long productId) throws SQLException {
        long orderId;
        try(PreparedStatement statement=connection.prepareStatement("""
                INSERT INTO purchase_orders(
                    supplier_id,status,source,created_by,created_at,sent_at,received_at
                ) VALUES(
                    ?,'RECEIVED','MANUAL','ci',
                    DATE_SUB(NOW(),INTERVAL 20 DAY),
                    DATE_SUB(NOW(),INTERVAL 15 DAY),
                    DATE_SUB(NOW(),INTERVAL 8 DAY)
                )
                """,Statement.RETURN_GENERATED_KEYS)){
            statement.setLong(1,supplierId);
            statement.executeUpdate();
            orderId=generatedKey(statement);
        }
        try(PreparedStatement statement=connection.prepareStatement("""
                INSERT INTO purchase_order_items(
                    purchase_order_id,product_id,quantity,unit_cost,received_quantity
                ) VALUES(?,?,20,4.50,20)
                """)){
            statement.setLong(1,orderId);
            statement.setLong(2,productId);
            statement.executeUpdate();
        }
    }

    private void insertOldDivergentCount(Connection connection,long productId) throws SQLException {
        long sessionId;
        try(PreparedStatement statement=connection.prepareStatement("""
                INSERT INTO blind_inventory_sessions(name,status,started_at,closed_at)
                VALUES(
                    'Governança CI','CLOSED',
                    DATE_SUB(NOW(),INTERVAL 21 DAY),
                    DATE_SUB(NOW(),INTERVAL 20 DAY)
                )
                """,Statement.RETURN_GENERATED_KEYS)){
            statement.executeUpdate();
            sessionId=generatedKey(statement);
        }
        try(PreparedStatement statement=connection.prepareStatement("""
                INSERT INTO blind_inventory_counts(
                    session_id,product_id,counted_quantity,system_quantity_snapshot,
                    difference_quantity,counted_at
                ) VALUES(
                    ?,?,1,2,-1,DATE_SUB(NOW(),INTERVAL 20 DAY)
                )
                """)){
            statement.setLong(1,sessionId);
            statement.setLong(2,productId);
            statement.executeUpdate();
        }
    }

    private long scalarLong(Connection connection,String sql) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement(sql);
            ResultSet rs=statement.executeQuery()){
            rs.next();
            return rs.getLong(1);
        }
    }

    private long generatedKey(PreparedStatement statement) throws SQLException {
        try(ResultSet keys=statement.getGeneratedKeys()){
            keys.next();
            return keys.getLong(1);
        }
    }
}
