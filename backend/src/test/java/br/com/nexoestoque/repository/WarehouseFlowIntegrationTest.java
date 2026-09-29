package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.PurchaseReceiptRequest;
import br.com.nexoestoque.dto.StockLocationRequest;
import br.com.nexoestoque.dto.StockTransferRequest;
import br.com.nexoestoque.dto.WarehouseRequest;
import br.com.nexoestoque.model.Product;
import br.com.nexoestoque.model.PurchaseReceiptResult;
import br.com.nexoestoque.model.StockLocation;
import br.com.nexoestoque.model.StockTransferResult;
import br.com.nexoestoque.model.Warehouse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WarehouseFlowIntegrationTest {

    @Test
    void receivesPurchaseTransfersBetweenLocationsAndPreservesGlobalStock() throws Exception {
        String url = System.getenv("NEXO_DB_INTEGRATION_URL");
        assumeTrue(url != null && !url.isBlank(), "MySQL integration URL not configured");

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(url);
        dataSource.setUsername(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_USER", "root"));
        dataSource.setPassword(System.getenv().getOrDefault("NEXO_DB_INTEGRATION_PASSWORD", "root"));

        WarehouseRepository warehouses = new WarehouseRepository(dataSource);
        PurchaseReceiptRepository receipts = new PurchaseReceiptRepository(dataSource);
        StockTransferRepository transfers = new StockTransferRepository(dataSource);
        ProductProcedureRepository products = new ProductProcedureRepository(dataSource);

        Warehouse originWarehouse = warehouses.createWarehouse(new WarehouseRequest(
                "IT5_ORIGIN", "Depósito integração origem", "Filial A", "Rua A"
        ));
        Warehouse destinationWarehouse = warehouses.createWarehouse(new WarehouseRequest(
                "IT5_DEST", "Depósito integração destino", "Filial B", "Rua B"
        ));

        StockLocation origin = warehouses.createLocation(new StockLocationRequest(
                originWarehouse.id(), "A-01-01", "A", "01", "01"
        ));
        StockLocation destination = warehouses.createLocation(new StockLocationRequest(
                destinationWarehouse.id(), "B-02-03", "B", "02", "03"
        ));

        long productId;
        long orderId;
        long itemId;

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO products(
                        sku, barcode, name, category, cost_price, sale_price,
                        current_stock, minimum_stock, active
                    )
                    VALUES('IT5-001','7891234567890','Produto integração','Integração',5,8,0,2,TRUE)
                    """, PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    productId = keys.getLong(1);
                }
            }

            long supplierId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO suppliers(name,tax_id,lead_time_days,active)
                    VALUES('Fornecedor IT5','IT5-SUPPLIER',3,TRUE)
                    """, PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    supplierId = keys.getLong(1);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_orders(
                        supplier_id,status,source,created_by
                    )
                    VALUES(?,'SENT','MANUAL','integration-test')
                    """, PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, supplierId);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    orderId = keys.getLong(1);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO purchase_order_items(
                        purchase_order_id,product_id,quantity,unit_cost
                    )
                    VALUES(?,?,10,5)
                    """, PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, orderId);
                statement.setLong(2, productId);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    itemId = keys.getLong(1);
                }
            }
        }

        Product barcodeProduct = products.findByBarcode("7891234567890");
        assertThat(barcodeProduct).isNotNull();
        assertThat(barcodeProduct.id()).isEqualTo(productId);

        PurchaseReceiptResult partial = receipts.receive(
                orderId,
                new PurchaseReceiptRequest(
                        itemId,
                        origin.id(),
                        "LOT-IT5",
                        LocalDate.now().plusMonths(6),
                        new BigDecimal("6"),
                        "it5-receipt-partial-001"
                ),
                "integration-test"
        );

        assertThat(partial.orderStatus()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(partial.remainingQuantity()).isEqualByComparingTo("4");
        assertThat(partial.balanceAfter()).isEqualByComparingTo("6");

        PurchaseReceiptResult complete = receipts.receive(
                orderId,
                new PurchaseReceiptRequest(
                        itemId,
                        origin.id(),
                        "LOT-IT5",
                        LocalDate.now().plusMonths(6),
                        new BigDecimal("4"),
                        "it5-receipt-final-001"
                ),
                "integration-test"
        );

        assertThat(complete.orderStatus()).isEqualTo("RECEIVED");
        assertThat(complete.remainingQuantity()).isEqualByComparingTo("0");
        assertThat(complete.balanceAfter()).isEqualByComparingTo("10");

        StockTransferRequest transferRequest = new StockTransferRequest(
                complete.batchId(),
                destination.id(),
                new BigDecimal("3"),
                "Reposicionamento entre filiais",
                "it5-transfer-001"
        );

        StockTransferResult transfer = transfers.transfer(transferRequest, "integration-test");
        StockTransferResult retry = transfers.transfer(transferRequest, "integration-test");

        assertThat(retry.id()).isEqualTo(transfer.id());
        assertThat(transfer.sourceLocationId()).isEqualTo(origin.id());
        assertThat(transfer.destinationLocationId()).isEqualTo(destination.id());

        try (Connection connection = dataSource.getConnection()) {
            assertThat(singleDecimal(connection,
                    "SELECT current_stock FROM products WHERE id = ?", productId))
                    .isEqualByComparingTo("10");

            assertThat(singleDecimal(connection,
                    "SELECT quantity FROM stock_batches WHERE id = ?", transfer.sourceBatchId()))
                    .isEqualByComparingTo("7");

            assertThat(singleDecimal(connection,
                    "SELECT quantity FROM stock_batches WHERE id = ?", transfer.destinationBatchId()))
                    .isEqualByComparingTo("3");

            assertThat(singleLong(connection,
                    "SELECT COUNT(*) FROM stock_transfers WHERE idempotency_key = 'it5-transfer-001'"))
                    .isEqualTo(1L);
        }
    }

    private BigDecimal singleDecimal(Connection connection, String sql, long id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private long singleLong(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
