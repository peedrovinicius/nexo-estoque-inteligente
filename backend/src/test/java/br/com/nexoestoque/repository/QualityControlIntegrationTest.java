package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.*;
import br.com.nexoestoque.model.PurchaseReceiptResult;
import br.com.nexoestoque.model.QualityControl.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class QualityControlIntegrationTest {

    @Test
    void quarantineRecallAndVarianceEnforceOperationalHolds() throws Exception {
        String url=System.getenv("NEXO_DB_INTEGRATION_URL");
        assumeTrue(url!=null&&!url.isBlank(),"MySQL integration URL not configured");

        DriverManagerDataSource dataSource=new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_USER","root"));
        dataSource.setPassword(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_PASSWORD","root"));

        long locationId;
        long destinationLocationId;
        long supplierId;
        long productId;
        long orderId;
        long orderItemId;

        try(Connection connection=dataSource.getConnection()){
            locationId=scalarLong(connection,"""
                    SELECT l.id
                      FROM stock_locations l
                      JOIN warehouses w ON w.id=l.warehouse_id
                     WHERE w.code='MAIN'
                     ORDER BY l.id
                     LIMIT 1
                    """);

            long warehouseId=scalarLong(connection,"SELECT id FROM warehouses WHERE code='MAIN' LIMIT 1");

            try(PreparedStatement statement=connection.prepareStatement("""
                    INSERT INTO stock_locations(warehouse_id,code,aisle,shelf,bin_code,active)
                    VALUES(?,'QUALITY-CI','Q','1','A',TRUE)
                    """,Statement.RETURN_GENERATED_KEYS)){
                statement.setLong(1,warehouseId);
                statement.executeUpdate();
                destinationLocationId=generatedKey(statement);
            }

            try(PreparedStatement statement=connection.prepareStatement("""
                    INSERT INTO suppliers(name,tax_id,lead_time_days,active)
                    VALUES('Fornecedor qualidade','QUALITY-SUP',4,TRUE)
                    """,Statement.RETURN_GENERATED_KEYS)){
                statement.executeUpdate();
                supplierId=generatedKey(statement);
            }

            try(PreparedStatement statement=connection.prepareStatement("""
                    INSERT INTO products(
                        sku,name,category,cost_price,sale_price,current_stock,minimum_stock,active
                    ) VALUES('QUALITY-001','Produto qualidade','Qualidade',8,14,0,2,TRUE)
                    """,Statement.RETURN_GENERATED_KEYS)){
                statement.executeUpdate();
                productId=generatedKey(statement);
            }

            try(PreparedStatement statement=connection.prepareStatement("""
                    INSERT INTO purchase_orders(
                        supplier_id,status,source,created_by,expected_at,sent_at
                    ) VALUES(?,'SENT','MANUAL','ci',DATE_ADD(CURDATE(),INTERVAL 5 DAY),NOW())
                    """,Statement.RETURN_GENERATED_KEYS)){
                statement.setLong(1,supplierId);
                statement.executeUpdate();
                orderId=generatedKey(statement);
            }

            try(PreparedStatement statement=connection.prepareStatement("""
                    INSERT INTO purchase_order_items(
                        purchase_order_id,product_id,quantity,unit_cost,received_quantity
                    ) VALUES(?,?,20,8,0)
                    """,Statement.RETURN_GENERATED_KEYS)){
                statement.setLong(1,orderId);
                statement.setLong(2,productId);
                statement.executeUpdate();
                orderItemId=generatedKey(statement);
            }
        }

        PurchaseReceiptRepository receiptRepository=new PurchaseReceiptRepository(dataSource);
        PurchaseReceiptResult receipt=receiptRepository.receive(
                orderId,
                new PurchaseReceiptRequest(
                        orderItemId,
                        locationId,
                        "QUALITY-LOT-01",
                        LocalDate.now().plusMonths(8),
                        new BigDecimal("10"),
                        "quality-receipt-001"
                ),
                "operator-ci"
        );

        long batchId=receipt.batchId();
        QualityControlRepository quality=new QualityControlRepository(dataSource);
        StockBatchProcedureRepository batches=new StockBatchProcedureRepository(dataSource);
        StockTransferRepository transfers=new StockTransferRepository(dataSource);

        BatchQualityState quarantined=quality.quarantineBatch(
                batchId,"Em inspeção por avaria de embalagem","operator-ci"
        );
        assertThat(quarantined.qualityStatus()).isEqualTo("QUARANTINED");
        assertThat(batches.previewExit(productId,new BigDecimal("1")).availableQuantity())
                .isEqualByComparingTo("0.000");

        assertThatThrownBy(() -> transfers.transfer(
                new StockTransferRequest(
                        batchId,destinationLocationId,new BigDecimal("1"),
                        "tentativa bloqueada","quality-transfer-001"
                ),
                "operator-ci"
        )).isInstanceOf(SQLException.class)
          .hasMessageContaining("quarentena");

        BatchQualityState released=quality.releaseBatch(
                batchId,"Inspeção concluída sem inconformidade","admin-ci"
        );
        assertThat(released.qualityStatus()).isEqualTo("AVAILABLE");
        assertThat(batches.previewExit(productId,new BigDecimal("1")).availableQuantity())
                .isEqualByComparingTo("10.000");

        LotRecall recall=quality.createRecall(
                new LotRecallRequest(
                        productId,"QUALITY-LOT-01","Recall preventivo do fornecedor"
                ),
                "operator-ci"
        );
        assertThat(recall.status()).isEqualTo("OPEN");
        assertThat(quality.heldBatches())
                .anyMatch(item -> item.batchId()==batchId && "BLOCKED".equals(item.qualityStatus()));
        assertThat(batches.previewExit(productId,new BigDecimal("1")).availableQuantity())
                .isEqualByComparingTo("0.000");

        assertThatThrownBy(() -> receiptRepository.receive(
                orderId,
                new PurchaseReceiptRequest(
                        orderItemId,
                        locationId,
                        "QUALITY-LOT-01",
                        LocalDate.now().plusMonths(8),
                        new BigDecimal("1"),
                        "quality-receipt-002"
                ),
                "operator-ci"
        )).isInstanceOf(SQLException.class)
          .hasMessageContaining("recall aberto");

        RecallImpact impact=quality.recallImpact(recall.id());
        assertThat(impact.currentQuantity()).isEqualByComparingTo("10.000");
        assertThat(impact.receiptQuantity()).isEqualByComparingTo("10.000");
        assertThat(impact.exitedQuantity()).isEqualByComparingTo("0.000");
        assertThat(impact.affectedLocations()).isEqualTo(1);
        assertThat(impact.batches()).hasSize(1);

        ReceiptVariance variance=quality.createVariance(
                new ReceiptVarianceRequest(
                        orderId,orderItemId,receipt.receiptId(),
                        "DAMAGED",new BigDecimal("2"),
                        "Duas unidades com embalagem danificada"
                ),
                "operator-ci"
        );
        assertThat(variance.varianceType()).isEqualTo("DAMAGED");
        assertThat(quality.summary().receiptVariances30Days()).isGreaterThanOrEqualTo(1);
        assertThat(quality.summary().openRecalls()).isGreaterThanOrEqualTo(1);

        LotRecall closed=quality.closeRecall(
                recall.id(),"Fornecedor confirmou encerramento e liberação","admin-ci"
        );
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(batches.previewExit(productId,new BigDecimal("1")).availableQuantity())
                .isEqualByComparingTo("10.000");
        assertThat(quality.heldBatches())
                .noneMatch(item -> item.batchId()==batchId);
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
