package br.com.nexoestoque.repository;

import br.com.nexoestoque.model.OperationalDashboard;
import br.com.nexoestoque.model.OperationalDashboard.CriticalStockItem;
import br.com.nexoestoque.model.OperationalDashboard.ExpiryRiskItem;
import br.com.nexoestoque.model.OperationalDashboard.StockPositionItem;
import br.com.nexoestoque.model.InventoryIntelligence.*;
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
                            rs.getString("lot_code"),
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


    public List<AbcItem> abcAnalysis(int limit) throws SQLException {
        return abcAnalysis(null, null, limit);
    }

    public List<AbcItem> abcAnalysis(String category, String query, int limit) throws SQLException {
        int safeLimit = normalizeLimit(limit, 1000);
        String normalizedCategory = normalizeText(category);
        String normalizedQuery = normalizeText(query);
        String queryPattern = normalizedQuery == null ? null : "%" + normalizedQuery + "%";
        List<AbcRow> rows = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.id, p.sku, p.name, p.category, p.current_stock,
                            COALESCE(SUM(CASE WHEN b.quantity > 0 THEN b.quantity * b.unit_cost ELSE 0 END), 0) AS stock_value
                       FROM products p
                       LEFT JOIN stock_batches b ON b.product_id = p.id
                      WHERE p.active = TRUE
                        AND (? IS NULL OR p.category = ?)
                        AND (? IS NULL OR p.name LIKE ? OR p.sku LIKE ?)
                      GROUP BY p.id, p.sku, p.name, p.category, p.current_stock
                      ORDER BY stock_value DESC, p.name, p.id
                     """)) {
            setNullableText(statement, 1, normalizedCategory);
            setNullableText(statement, 2, normalizedCategory);
            setNullableText(statement, 3, normalizedQuery);
            setNullableText(statement, 4, queryPattern);
            setNullableText(statement, 5, queryPattern);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AbcRow(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                            rs.getString("category"), rs.getBigDecimal("current_stock"), rs.getBigDecimal("stock_value")));
                }
            }
        }
        BigDecimal total = rows.stream().map(AbcRow::stockValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AbcItem> items = new ArrayList<>();
        BigDecimal cumulativeValue = BigDecimal.ZERO;
        for (AbcRow row : rows) {
            cumulativeValue = cumulativeValue.add(row.stockValue());
            BigDecimal participation = percent(row.stockValue(), total);
            BigDecimal cumulative = percent(cumulativeValue, total);
            String abcClass = cumulative.compareTo(new BigDecimal("80")) <= 0 ? "A"
                    : cumulative.compareTo(new BigDecimal("95")) <= 0 ? "B" : "C";
            if (total.signum() == 0) abcClass = "C";
            items.add(new AbcItem(row.productId(), row.sku(), row.productName(), row.category(),
                    row.stockQuantity(), row.stockValue(), participation, cumulative, abcClass));
            if (items.size() >= safeLimit) break;
        }
        return items;
    }

    public List<SlowMovingItem> slowMoving(int days, int limit) throws SQLException {
        return slowMoving(days, null, null, limit);
    }

    public List<SlowMovingItem> slowMoving(int days, String category, String query, int limit) throws SQLException {
        int safeDays = Math.max(1, Math.min(days, 3650));
        int safeLimit = normalizeLimit(limit, 1000);
        String normalizedCategory = normalizeText(category);
        String normalizedQuery = normalizeText(query);
        String queryPattern = normalizedQuery == null ? null : "%" + normalizedQuery + "%";
        List<SlowMovingItem> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.id, p.sku, p.name, p.current_stock,
                            COALESCE(bv.stock_value, p.current_stock * p.cost_price, 0) AS stock_value,
                            mv.last_exit_at,
                            CASE WHEN mv.last_exit_at IS NULL THEN NULL
                                 ELSE DATEDIFF(CURDATE(), DATE(mv.last_exit_at)) END AS days_since_last_exit
                       FROM products p
                       LEFT JOIN (
                           SELECT product_id, SUM(quantity * unit_cost) AS stock_value
                             FROM stock_batches WHERE quantity > 0 GROUP BY product_id
                       ) bv ON bv.product_id = p.id
                       LEFT JOIN (
                           SELECT product_id, MAX(created_at) AS last_exit_at
                             FROM (
                                   SELECT product_id, created_at FROM stock_movements WHERE movement_type = 'EXIT'
                                   UNION ALL
                                   SELECT product_id, created_at FROM stock_movements_archive WHERE movement_type = 'EXIT'
                             ) movement_history
                            GROUP BY product_id
                       ) mv ON mv.product_id = p.id
                      WHERE p.active = TRUE AND p.current_stock > 0
                        AND (mv.last_exit_at IS NULL OR DATEDIFF(CURDATE(), DATE(mv.last_exit_at)) >= ?)
                        AND (? IS NULL OR p.category = ?)
                        AND (? IS NULL OR p.name LIKE ? OR p.sku LIKE ?)
                      ORDER BY (mv.last_exit_at IS NULL) DESC, days_since_last_exit DESC, stock_value DESC, p.name
                      LIMIT ?
                     """)) {
            statement.setInt(1, safeDays);
            setNullableText(statement, 2, normalizedCategory);
            setNullableText(statement, 3, normalizedCategory);
            setNullableText(statement, 4, normalizedQuery);
            setNullableText(statement, 5, queryPattern);
            setNullableText(statement, 6, queryPattern);
            statement.setInt(7, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Timestamp lastExit = rs.getTimestamp("last_exit_at");
                    Object daysObject = rs.getObject("days_since_last_exit");
                    items.add(new SlowMovingItem(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                            rs.getBigDecimal("current_stock"), rs.getBigDecimal("stock_value"),
                            lastExit == null ? null : lastExit.toLocalDateTime(),
                            daysObject == null ? null : ((Number) daysObject).intValue()));
                }
            }
        }
        return items;
    }

    public List<CoverageItem> coverage(int windowDays, int limit) throws SQLException {
        return coverage(windowDays, null, null, limit);
    }

    public List<CoverageItem> coverage(int windowDays, String category, String query, int limit) throws SQLException {
        int safeWindow = Math.max(7, Math.min(windowDays, 365));
        int safeLimit = normalizeLimit(limit, 1000);
        String normalizedCategory = normalizeText(category);
        String normalizedQuery = normalizeText(query);
        String queryPattern = normalizedQuery == null ? null : "%" + normalizedQuery + "%";
        List<CoverageItem> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.id, p.sku, p.name, p.current_stock, COALESCE(m.exit_quantity, 0) AS exit_quantity
                       FROM products p
                       LEFT JOIN (
                           SELECT product_id, SUM(quantity) AS exit_quantity
                             FROM (
                                   SELECT product_id, quantity, created_at FROM stock_movements WHERE movement_type = 'EXIT'
                                   UNION ALL
                                   SELECT product_id, quantity, created_at FROM stock_movements_archive WHERE movement_type = 'EXIT'
                             ) movement_history
                            WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                            GROUP BY product_id
                       ) m ON m.product_id = p.id
                      WHERE p.active = TRUE
                        AND (? IS NULL OR p.category = ?)
                        AND (? IS NULL OR p.name LIKE ? OR p.sku LIKE ?)
                      ORDER BY p.name, p.id
                     """)) {
            statement.setInt(1, safeWindow);
            setNullableText(statement, 2, normalizedCategory);
            setNullableText(statement, 3, normalizedCategory);
            setNullableText(statement, 4, normalizedQuery);
            setNullableText(statement, 5, queryPattern);
            setNullableText(statement, 6, queryPattern);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    BigDecimal currentStock = rs.getBigDecimal("current_stock");
                    BigDecimal exitQuantity = rs.getBigDecimal("exit_quantity");
                    BigDecimal average = exitQuantity.signum() == 0 ? BigDecimal.ZERO
                            : exitQuantity.divide(BigDecimal.valueOf(safeWindow), 3, RoundingMode.HALF_UP);
                    BigDecimal coverageDays = average.signum() == 0 ? null
                            : currentStock.divide(average, 1, RoundingMode.HALF_UP);
                    items.add(new CoverageItem(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                            currentStock, exitQuantity, average, coverageDays, coverageLevel(coverageDays)));
                }
            }
        }
        items.sort((a, b) -> {
            if (a.coverageDays() == null && b.coverageDays() == null) return a.productName().compareToIgnoreCase(b.productName());
            if (a.coverageDays() == null) return 1;
            if (b.coverageDays() == null) return -1;
            int comparison = a.coverageDays().compareTo(b.coverageDays());
            return comparison != 0 ? comparison : a.productName().compareToIgnoreCase(b.productName());
        });
        return items.size() <= safeLimit ? items : new ArrayList<>(items.subList(0, safeLimit));
    }

    public List<OpenPurchaseAgingItem> openPurchaseAging(int limit) throws SQLException {
        int safeLimit = normalizeLimit(limit, 1000);
        List<OpenPurchaseAgingItem> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT po.id AS order_id, s.id AS supplier_id, s.name AS supplier_name, po.status, po.expected_at,
                            DATEDIFF(CURDATE(), DATE(COALESCE(po.sent_at, po.created_at))) AS days_open,
                            CASE WHEN po.expected_at IS NOT NULL AND po.expected_at < CURDATE()
                                 THEN DATEDIFF(CURDATE(), po.expected_at) ELSE 0 END AS overdue_days,
                            COALESCE(SUM(GREATEST(poi.quantity - poi.received_quantity, 0)), 0) AS pending_quantity,
                            COALESCE(SUM(GREATEST(poi.quantity - poi.received_quantity, 0) * poi.unit_cost), 0) AS pending_value
                       FROM purchase_orders po
                       JOIN suppliers s ON s.id = po.supplier_id
                       JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
                      WHERE po.status IN ('SENT', 'PARTIALLY_RECEIVED')
                      GROUP BY po.id, s.id, s.name, po.status, po.expected_at, po.sent_at, po.created_at
                      ORDER BY overdue_days DESC, days_open DESC, po.id
                      LIMIT ?
                     """)) {
            statement.setInt(1, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Date expected = rs.getDate("expected_at");
                    items.add(new OpenPurchaseAgingItem(rs.getLong("order_id"), rs.getLong("supplier_id"),
                            rs.getString("supplier_name"), rs.getString("status"),
                            expected == null ? null : expected.toLocalDate(), rs.getInt("days_open"),
                            rs.getInt("overdue_days"), rs.getBigDecimal("pending_quantity"), rs.getBigDecimal("pending_value")));
                }
            }
        }
        return items;
    }

    public ExpiryExposureSummary expiryExposure(int horizonDays, String category, String query) throws SQLException {
        int safeHorizon = Math.max(30, Math.min(horizonDays, 365));
        String normalizedCategory = normalizeText(category);
        String normalizedQuery = normalizeText(query);
        String queryPattern = normalizedQuery == null ? null : "%" + normalizedQuery + "%";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT
                       SUM(CASE WHEN b.expires_at < CURDATE() THEN 1 ELSE 0 END) AS expired_batches,
                       COALESCE(SUM(CASE WHEN b.expires_at < CURDATE() THEN b.quantity ELSE 0 END), 0) AS expired_quantity,
                       COALESCE(SUM(CASE WHEN b.expires_at < CURDATE() THEN b.quantity * b.unit_cost ELSE 0 END), 0) AS expired_value,
                       SUM(CASE WHEN DATEDIFF(b.expires_at, CURDATE()) BETWEEN 0 AND 30 THEN 1 ELSE 0 END) AS critical_batches,
                       COALESCE(SUM(CASE WHEN DATEDIFF(b.expires_at, CURDATE()) BETWEEN 0 AND 30 THEN b.quantity ELSE 0 END), 0) AS critical_quantity,
                       COALESCE(SUM(CASE WHEN DATEDIFF(b.expires_at, CURDATE()) BETWEEN 0 AND 30 THEN b.quantity * b.unit_cost ELSE 0 END), 0) AS critical_value,
                       SUM(CASE WHEN DATEDIFF(b.expires_at, CURDATE()) BETWEEN 31 AND ? THEN 1 ELSE 0 END) AS warning_batches,
                       COALESCE(SUM(CASE WHEN DATEDIFF(b.expires_at, CURDATE()) BETWEEN 31 AND ? THEN b.quantity ELSE 0 END), 0) AS warning_quantity,
                       COALESCE(SUM(CASE WHEN DATEDIFF(b.expires_at, CURDATE()) BETWEEN 31 AND ? THEN b.quantity * b.unit_cost ELSE 0 END), 0) AS warning_value
                      FROM stock_batches b
                      JOIN products p ON p.id = b.product_id
                     WHERE b.quantity > 0
                       AND b.expires_at IS NOT NULL
                       AND p.active = TRUE
                       AND (? IS NULL OR p.category = ?)
                       AND (? IS NULL OR p.name LIKE ? OR p.sku LIKE ?)
                     """)) {
            statement.setInt(1, safeHorizon);
            statement.setInt(2, safeHorizon);
            statement.setInt(3, safeHorizon);
            setNullableText(statement, 4, normalizedCategory);
            setNullableText(statement, 5, normalizedCategory);
            setNullableText(statement, 6, normalizedQuery);
            setNullableText(statement, 7, queryPattern);
            setNullableText(statement, 8, queryPattern);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new ExpiryExposureSummary(
                        safeHorizon,
                        rs.getInt("expired_batches"),
                        rs.getBigDecimal("expired_quantity"),
                        rs.getBigDecimal("expired_value"),
                        rs.getInt("critical_batches"),
                        rs.getBigDecimal("critical_quantity"),
                        rs.getBigDecimal("critical_value"),
                        rs.getInt("warning_batches"),
                        rs.getBigDecimal("warning_quantity"),
                        rs.getBigDecimal("warning_value")
                );
            }
        }
    }

    public List<CapitalBreakdownItem> capitalBreakdown(
            String dimension, String category, String query, int limit
    ) throws SQLException {
        String normalizedDimension = dimension == null ? "category" : dimension.trim().toLowerCase();
        if (!normalizedDimension.equals("category") && !normalizedDimension.equals("warehouse")) {
            throw new IllegalArgumentException("Dimensão deve ser category ou warehouse");
        }
        int safeLimit = normalizeLimit(limit, 500);
        String normalizedCategory = normalizeText(category);
        String normalizedQuery = normalizeText(query);
        String queryPattern = normalizedQuery == null ? null : "%" + normalizedQuery + "%";
        String selectDimension = normalizedDimension.equals("warehouse")
                ? "CAST(w.id AS CHAR) AS item_key, w.name AS item_label"
                : "p.category AS item_key, p.category AS item_label";
        String joins = normalizedDimension.equals("warehouse")
                ? " JOIN stock_locations l ON l.id = b.location_id JOIN warehouses w ON w.id = l.warehouse_id "
                : " ";
        String groupBy = normalizedDimension.equals("warehouse") ? "w.id, w.name" : "p.category";
        String sql = """
                SELECT %s,
                       SUM(b.quantity) AS stock_quantity,
                       SUM(b.quantity * b.unit_cost) AS stock_value
                  FROM stock_batches b
                  JOIN products p ON p.id = b.product_id
                  %s
                 WHERE b.quantity > 0
                   AND p.active = TRUE
                   AND (? IS NULL OR p.category = ?)
                   AND (? IS NULL OR p.name LIKE ? OR p.sku LIKE ?)
                 GROUP BY %s
                 ORDER BY stock_value DESC, item_label
                 LIMIT ?
                """.formatted(selectDimension, joins, groupBy);

        List<CapitalBreakdownItem> raw = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableText(statement, 1, normalizedCategory);
            setNullableText(statement, 2, normalizedCategory);
            setNullableText(statement, 3, normalizedQuery);
            setNullableText(statement, 4, queryPattern);
            setNullableText(statement, 5, queryPattern);
            statement.setInt(6, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    raw.add(new CapitalBreakdownItem(
                            normalizedDimension,
                            rs.getString("item_key"),
                            rs.getString("item_label"),
                            rs.getBigDecimal("stock_quantity"),
                            rs.getBigDecimal("stock_value"),
                            BigDecimal.ZERO
                    ));
                }
            }
        }
        BigDecimal total = raw.stream().map(CapitalBreakdownItem::stockValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CapitalBreakdownItem> result = new ArrayList<>();
        for (CapitalBreakdownItem item : raw) {
            result.add(new CapitalBreakdownItem(
                    item.dimension(), item.key(), item.label(), item.stockQuantity(),
                    item.stockValue(), percent(item.stockValue(), total)
            ));
        }
        return result;
    }

    public AlertSettings alertSettings() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT expiry_warning_days, low_coverage_days, slow_moving_days,
                            purchase_overdue_days, coverage_window_days
                       FROM inventory_alert_settings
                      WHERE id = 1
                     """);
             ResultSet rs = statement.executeQuery()) {
            if (!rs.next()) return new AlertSettings(30, 7, 90, 1, 30);
            return new AlertSettings(
                    rs.getInt("expiry_warning_days"),
                    rs.getInt("low_coverage_days"),
                    rs.getInt("slow_moving_days"),
                    rs.getInt("purchase_overdue_days"),
                    rs.getInt("coverage_window_days")
            );
        }
    }

    public AlertSettings updateAlertSettings(AlertSettings settings, String actor) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE inventory_alert_settings
                        SET expiry_warning_days = ?,
                            low_coverage_days = ?,
                            slow_moving_days = ?,
                            purchase_overdue_days = ?,
                            coverage_window_days = ?,
                            updated_by = ?,
                            updated_at = CURRENT_TIMESTAMP
                      WHERE id = 1
                     """)) {
            statement.setInt(1, settings.expiryWarningDays());
            statement.setInt(2, settings.lowCoverageDays());
            statement.setInt(3, settings.slowMovingDays());
            statement.setInt(4, settings.purchaseOverdueDays());
            statement.setInt(5, settings.coverageWindowDays());
            statement.setString(6, actor == null || actor.isBlank() ? "system" : actor);
            statement.executeUpdate();
        }
        return alertSettings();
    }

    public List<OperationalAlert> operationalAlerts() throws SQLException {
        AlertSettings settings = alertSettings();
        List<OperationalAlert> alerts = new ArrayList<>();

        for (CriticalStockItem item : criticalStock(100)) {
            alerts.add(new OperationalAlert(
                    "stock-" + item.productId(),
                    "STOCK",
                    item.currentStock().signum() <= 0 ? "CRITICAL" : "WARNING",
                    item.name(),
                    "Saldo " + item.currentStock() + " · mínimo " + item.minimumStock(),
                    item.deficit() + " abaixo"
            ));
        }

        for (ExpiryRiskItem item : expiryRisk(settings.expiryWarningDays(), 200)) {
            alerts.add(new OperationalAlert(
                    "expiry-" + item.batchId(),
                    "EXPIRY",
                    item.daysToExpiry() <= 7 ? "CRITICAL" : "WARNING",
                    item.productName(),
                    "Lote " + item.lotCode() + " · " + item.warehouseName() + "/" + item.locationCode(),
                    item.daysToExpiry() < 0 ? Math.abs(item.daysToExpiry()) + " d vencido" : item.daysToExpiry() + " d"
            ));
        }

        for (CoverageItem item : coverage(settings.coverageWindowDays(), 300)) {
            if (item.coverageDays() != null
                    && item.coverageDays().compareTo(BigDecimal.valueOf(settings.lowCoverageDays())) < 0) {
                alerts.add(new OperationalAlert(
                        "coverage-" + item.productId(),
                        "COVERAGE",
                        "CRITICAL",
                        item.productName(),
                        "Cobertura abaixo do limiar configurado",
                        item.coverageDays() + " d"
                ));
            }
        }

        for (SlowMovingItem item : slowMoving(settings.slowMovingDays(), 200)) {
            alerts.add(new OperationalAlert(
                    "slow-" + item.productId(),
                    "SLOW_MOVING",
                    "INFO",
                    item.productName(),
                    item.daysSinceLastExit() == null ? "Sem saída registrada" : "Sem saída recente",
                    item.daysSinceLastExit() == null ? "sem histórico" : item.daysSinceLastExit() + " d"
            ));
        }

        for (OpenPurchaseAgingItem item : openPurchaseAging(200)) {
            if (item.overdueDays() >= settings.purchaseOverdueDays() && item.overdueDays() > 0) {
                alerts.add(new OperationalAlert(
                        "purchase-" + item.orderId(),
                        "PURCHASE",
                        "WARNING",
                        "Pedido #" + item.orderId() + " · " + item.supplierName(),
                        "Pedido aberto com recebimento pendente",
                        item.overdueDays() + " d atraso"
                ));
            }
        }

        alerts.sort((a, b) -> {
            int severity = Integer.compare(severityRank(a.severity()), severityRank(b.severity()));
            return severity != 0 ? severity : a.title().compareToIgnoreCase(b.title());
        });
        return alerts.size() <= 300 ? alerts : new ArrayList<>(alerts.subList(0, 300));
    }

    private int severityRank(String severity) {
        if ("CRITICAL".equals(severity)) return 0;
        if ("WARNING".equals(severity)) return 1;
        return 2;
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

    private BigDecimal percent(BigDecimal value, BigDecimal total) {
        if (total == null || total.signum() == 0) return BigDecimal.ZERO;
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private String coverageLevel(BigDecimal coverageDays) {
        if (coverageDays == null) return "NO_CONSUMPTION";
        if (coverageDays.compareTo(BigDecimal.valueOf(7)) < 0) return "CRITICAL";
        if (coverageDays.compareTo(BigDecimal.valueOf(30)) < 0) return "ATTENTION";
        return "HEALTHY";
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
    private record AbcRow(long productId, String sku, String productName, String category,
                          BigDecimal stockQuantity, BigDecimal stockValue) {}
}
