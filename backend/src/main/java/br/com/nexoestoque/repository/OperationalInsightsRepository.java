package br.com.nexoestoque.repository;

import br.com.nexoestoque.model.OperationalDashboard;
import br.com.nexoestoque.model.OperationalDashboard.CriticalStockItem;
import br.com.nexoestoque.model.OperationalDashboard.ExpiryRiskItem;
import br.com.nexoestoque.model.OperationalDashboard.StockPositionItem;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OperationalInsightsRepository {
    private final DataSource dataSource;

    public OperationalInsightsRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public OperationalDashboard dashboard() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            StockTotals totals = stockTotals(connection);
            ProductCounts products = productCounts(connection);
            ExpiryCounts expiry = expiryCounts(connection);
            InventoryMetrics inventory = latestClosedInventory(connection);
            OpenInventory open = openInventory(connection);
            CriticalStockItem topCritical = criticalStock(connection, 1).stream().findFirst().orElse(null);
            ExpiryRiskItem topExpiry = expiryRisk(connection, 30, 1).stream().findFirst().orElse(null);

            return new OperationalDashboard(
                    totals.totalStock(),
                    totals.stockValue(),
                    products.active(),
                    products.critical(),
                    products.outOfStock(),
                    expiry.risk30(),
                    expiry.expired(),
                    expiry.warning90(),
                    expiry.riskValue30(),
                    inventory.accuracy(),
                    inventory.divergences(),
                    topCritical,
                    topExpiry,
                    open.id(),
                    open.name(),
                    open.countedItems()
            );
        }
    }

    public List<CriticalStockItem> criticalStock(int limit) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return criticalStock(connection, normalizeLimit(limit, 200));
        }
    }

    public List<ExpiryRiskItem> expiryRisk(int days, int limit) throws SQLException {
        int safeDays = Math.max(1, Math.min(days, 365));
        try (Connection connection = dataSource.getConnection()) {
            return expiryRisk(connection, safeDays, normalizeLimit(limit, 1000));
        }
    }

    public List<StockPositionItem> stockPosition(Long warehouseId, int limit) throws SQLException {
        int safeLimit = normalizeLimit(limit, 10000);
        List<StockPositionItem> items = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT b.id AS batch_id,
                            p.id AS product_id,
                            p.sku,
                            p.barcode,
                            p.name AS product_name,
                            p.category,
                            w.id AS warehouse_id,
                            w.code AS warehouse_code,
                            w.name AS warehouse_name,
                            l.id AS location_id,
                            l.code AS location_code,
                            l.aisle,
                            l.shelf,
                            l.bin_code,
                            b.lot_code,
                            b.expires_at,
                            CASE WHEN b.expires_at IS NULL THEN NULL
                                 ELSE DATEDIFF(b.expires_at, CURDATE()) END AS days_to_expiry,
                            b.quantity,
                            b.unit_cost,
                            (b.quantity * b.unit_cost) AS stock_value
                       FROM stock_batches b
                       JOIN products p ON p.id = b.product_id
                       JOIN stock_locations l ON l.id = b.location_id
                       JOIN warehouses w ON w.id = l.warehouse_id
                      WHERE b.quantity > 0
                        AND (? IS NULL OR w.id = ?)
                      ORDER BY w.name, l.code, p.name, b.expires_at IS NULL, b.expires_at, b.id
                      LIMIT ?
                     """)) {
            if (warehouseId == null) {
                statement.setNull(1, Types.BIGINT);
                statement.setNull(2, Types.BIGINT);
            } else {
                statement.setLong(1, warehouseId);
                statement.setLong(2, warehouseId);
            }
            statement.setInt(3, safeLimit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Date expires = rs.getDate("expires_at");
                    Object daysObject = rs.getObject("days_to_expiry");
                    Integer days = daysObject == null ? null : ((Number) daysObject).intValue();
                    items.add(new StockPositionItem(
                            rs.getLong("batch_id"),
                            rs.getLong("product_id"),
                            rs.getString("sku"),
                            rs.getString("barcode"),
                            rs.getString("product_name"),
                            rs.getString("category"),
                            rs.getLong("warehouse_id"),
                            rs.getString("warehouse_code"),
                            rs.getString("warehouse_name"),
                            rs.getLong("location_id"),
                            rs.getString("location_code"),
                            rs.getString("aisle"),
                            rs.getString("shelf"),
                            rs.getString("bin_code"),
                            expires == null ? null : expires.toLocalDate(),
                            days,
                            rs.getBigDecimal("quantity"),
                            rs.getBigDecimal("unit_cost"),
                            rs.getBigDecimal("stock_value")
                    ));
                }
            }
        }

        return items;
    }

    private StockTotals stockTotals(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(SUM(quantity), 0) AS total_stock,
                       COALESCE(SUM(quantity * unit_cost), 0) AS stock_value
                  FROM stock_batches
                 WHERE quantity > 0
                """);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return new StockTotals(rs.getBigDecimal("total_stock"), rs.getBigDecimal("stock_value"));
        }
    }

    private ProductCounts productCounts(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) AS active_products,
                       SUM(CASE WHEN current_stock < minimum_stock THEN 1 ELSE 0 END) AS critical_products,
                       SUM(CASE WHEN current_stock <= 0 THEN 1 ELSE 0 END) AS out_of_stock_products
                  FROM products
                 WHERE active = TRUE
                """);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return new ProductCounts(
                    rs.getInt("active_products"),
                    rs.getInt("critical_products"),
                    rs.getInt("out_of_stock_products")
            );
        }
    }

    private ExpiryCounts expiryCounts(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT SUM(CASE WHEN expires_at IS NOT NULL
                                      AND DATEDIFF(expires_at, CURDATE()) <= 30
                                      THEN 1 ELSE 0 END) AS risk_30,
                       SUM(CASE WHEN expires_at IS NOT NULL
                                      AND expires_at < CURDATE()
                                      THEN 1 ELSE 0 END) AS expired,
                       SUM(CASE WHEN expires_at IS NOT NULL
                                      AND DATEDIFF(expires_at, CURDATE()) BETWEEN 31 AND 90
                                      THEN 1 ELSE 0 END) AS warning_90,
                       COALESCE(SUM(CASE WHEN expires_at IS NOT NULL
                                              AND DATEDIFF(expires_at, CURDATE()) <= 30
                                              THEN quantity * unit_cost ELSE 0 END), 0) AS risk_value_30
                  FROM stock_batches
                 WHERE quantity > 0
                """);
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return new ExpiryCounts(
                    rs.getInt("risk_30"),
                    rs.getInt("expired"),
                    rs.getInt("warning_90"),
                    rs.getBigDecimal("risk_value_30")
            );
        }
    }

    private InventoryMetrics latestClosedInventory(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.id,
                       COUNT(c.id) AS counted_items,
                       SUM(CASE WHEN c.difference_quantity <> 0 THEN 1 ELSE 0 END) AS divergences
                  FROM blind_inventory_sessions s
                  LEFT JOIN blind_inventory_counts c ON c.session_id = s.id
                 WHERE s.status = 'CLOSED'
                 GROUP BY s.id, s.closed_at, s.started_at
                 ORDER BY COALESCE(s.closed_at, s.started_at) DESC, s.id DESC
                 LIMIT 1
                """);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) return new InventoryMetrics(null, null);
            int counted = rs.getInt("counted_items");
            int divergences = rs.getInt("divergences");
            if (counted <= 0) return new InventoryMetrics(null, divergences);
            BigDecimal accuracy = BigDecimal.valueOf(counted - divergences)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(counted), 1, RoundingMode.HALF_UP)
                    .max(BigDecimal.ZERO);
            return new InventoryMetrics(accuracy, divergences);
        }
    }

    private OpenInventory openInventory(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT s.id, s.name, COUNT(c.id) AS counted_items
                  FROM blind_inventory_sessions s
                  LEFT JOIN blind_inventory_counts c ON c.session_id = s.id
                 WHERE s.status = 'OPEN'
                 GROUP BY s.id, s.name, s.started_at
                 ORDER BY s.started_at, s.id
                 LIMIT 1
                """);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) return new OpenInventory(null, null, 0);
            return new OpenInventory(rs.getLong("id"), rs.getString("name"), rs.getInt("counted_items"));
        }
    }

    private List<CriticalStockItem> criticalStock(Connection connection, int limit) throws SQLException {
        List<CriticalStockItem> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, sku, name, current_stock, minimum_stock,
                       GREATEST(minimum_stock - current_stock, 0) AS deficit,
                       GREATEST(minimum_stock - current_stock, 0) * cost_price AS estimated_replenishment_cost
                  FROM products
                 WHERE active = TRUE
                   AND current_stock < minimum_stock
                 ORDER BY (current_stock <= 0) DESC,
                          (minimum_stock - current_stock) DESC,
                          name,
                          id
                 LIMIT ?
                """)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    items.add(new CriticalStockItem(
                            rs.getLong("id"),
                            rs.getString("sku"),
                            rs.getString("name"),
                            rs.getBigDecimal("current_stock"),
                            rs.getBigDecimal("minimum_stock"),
                            rs.getBigDecimal("deficit"),
                            rs.getBigDecimal("estimated_replenishment_cost")
                    ));
                }
            }
        }
        return items;
    }

    private List<ExpiryRiskItem> expiryRisk(Connection connection, int days, int limit) throws SQLException {
        List<ExpiryRiskItem> items = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT b.id AS batch_id,
                       p.id AS product_id,
                       p.sku,
                       p.name AS product_name,
                       b.lot_code,
                       b.expires_at,
                       DATEDIFF(b.expires_at, CURDATE()) AS days_to_expiry,
                       b.quantity,
                       b.unit_cost,
                       (b.quantity * b.unit_cost) AS exposure_value,
                       w.id AS warehouse_id,
                       w.name AS warehouse_name,
                       l.id AS location_id,
                       l.code AS location_code
                  FROM stock_batches b
                  JOIN products p ON p.id = b.product_id
                  JOIN stock_locations l ON l.id = b.location_id
                  JOIN warehouses w ON w.id = l.warehouse_id
                 WHERE b.quantity > 0
                   AND b.expires_at IS NOT NULL
                   AND DATEDIFF(b.expires_at, CURDATE()) <= ?
                 ORDER BY b.expires_at, b.id
                 LIMIT ?
                """)) {
            statement.setInt(1, days);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int daysToExpiry = rs.getInt("days_to_expiry");
                    Date expires = rs.getDate("expires_at");
                    items.add(new ExpiryRiskItem(
                            rs.getLong("batch_id"),
                            rs.getLong("product_id"),
                            rs.getString("sku"),
                            rs.getString("product_name"),
                            rs.getString("lot_code"),
                            expires.toLocalDate(),
                            daysToExpiry,
                            expiryLevel(daysToExpiry),
                            rs.getBigDecimal("quantity"),
                            rs.getBigDecimal("unit_cost"),
                            rs.getBigDecimal("exposure_value"),
                            rs.getLong("warehouse_id"),
                            rs.getString("warehouse_name"),
                            rs.getLong("location_id"),
                            rs.getString("location_code")
                    ));
                }
            }
        }
        return items;
    }

    private String expiryLevel(int days) {
        if (days < 0) return "EXPIRED";
        if (days == 0) return "TODAY";
        if (days <= 30) return "CRITICAL";
        return "WARNING";
    }

    private int normalizeLimit(int limit, int max) {
        if (limit <= 0) return Math.min(100, max);
        return Math.min(limit, max);
    }

    private record StockTotals(BigDecimal totalStock, BigDecimal stockValue) {}
    private record ProductCounts(int active, int critical, int outOfStock) {}
    private record ExpiryCounts(int risk30, int expired, int warning90, BigDecimal riskValue30) {}
    private record InventoryMetrics(BigDecimal accuracy, Integer divergences) {}
    private record OpenInventory(Long id, String name, int countedItems) {}
}
