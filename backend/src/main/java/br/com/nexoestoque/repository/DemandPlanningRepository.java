package br.com.nexoestoque.repository;

import br.com.nexoestoque.model.DemandPlanning.PlanningItem;
import br.com.nexoestoque.model.DemandPlanning.PlanningSummary;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;

@Repository
public class DemandPlanningRepository {
    public static final String RULE_VERSION = "planning-v1.0.0";
    private final DataSource dataSource;

    public DemandPlanningRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<PlanningItem> planning(int limit) throws SQLException {
        int safeLimit = limit <= 0 ? 200 : Math.min(limit, 1000);
        Map<Long, ProductBase> products = new LinkedHashMap<>();
        Map<Long, DemandHistory> demand = new HashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT p.id, p.sku, p.name, p.category, p.current_stock,
                           COALESCE(r.reserved_quantity,0) AS reserved_quantity,
                           COALESCE(i.incoming_quantity,0) AS incoming_quantity,
                           COALESCE(s.lead_time_days,0) AS supplier_lead_time_days
                      FROM products p
                      LEFT JOIN (
                          SELECT product_id, SUM(quantity) AS reserved_quantity
                            FROM stock_reservations
                           WHERE status='ACTIVE'
                             AND (expires_at IS NULL OR expires_at > NOW())
                           GROUP BY product_id
                      ) r ON r.product_id=p.id
                      LEFT JOIN (
                          SELECT poi.product_id,
                                 SUM(GREATEST(poi.quantity-poi.received_quantity,0)) AS incoming_quantity
                            FROM purchase_order_items poi
                            JOIN purchase_orders po ON po.id=poi.purchase_order_id
                           WHERE po.status IN ('DRAFT','SENT','PARTIALLY_RECEIVED')
                           GROUP BY poi.product_id
                      ) i ON i.product_id=p.id
                      LEFT JOIN (
                          SELECT product_id, lead_time_days
                            FROM (
                                SELECT poi.product_id,
                                       COALESCE(
                                           ROUND(AVG(CASE
                                               WHEN po.received_at IS NOT NULL AND po.sent_at IS NOT NULL
                                               THEN GREATEST(DATEDIFF(po.received_at, po.sent_at),0)
                                           END) OVER(PARTITION BY poi.product_id)),
                                           s.lead_time_days,
                                           0
                                       ) AS lead_time_days,
                                       ROW_NUMBER() OVER(
                                           PARTITION BY poi.product_id
                                           ORDER BY po.created_at DESC, po.id DESC
                                       ) AS rn
                                  FROM purchase_order_items poi
                                  JOIN purchase_orders po ON po.id=poi.purchase_order_id
                                  JOIN suppliers s ON s.id=po.supplier_id
                                 WHERE po.status<>'CANCELLED'
                            ) ranked
                           WHERE rn=1
                      ) s ON s.product_id=p.id
                     WHERE p.active=TRUE
                     ORDER BY p.name,p.id
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    products.put(rs.getLong("id"), new ProductBase(
                            rs.getLong("id"),
                            rs.getString("sku"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getBigDecimal("current_stock"),
                            rs.getBigDecimal("reserved_quantity"),
                            rs.getBigDecimal("incoming_quantity"),
                            rs.getInt("supplier_lead_time_days")
                    ));
                }
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT product_id, DATE(created_at) AS movement_day, SUM(quantity) AS qty
                      FROM (
                          SELECT product_id, quantity, created_at
                            FROM stock_movements
                           WHERE movement_type='EXIT'
                             AND created_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
                          UNION ALL
                          SELECT product_id, quantity, created_at
                            FROM stock_movements_archive
                           WHERE movement_type='EXIT'
                             AND created_at >= DATE_SUB(NOW(), INTERVAL 90 DAY)
                      ) movement_history
                     GROUP BY product_id, DATE(created_at)
                     ORDER BY product_id, movement_day
                    """);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long productId = rs.getLong("product_id");
                    DemandHistory history = demand.computeIfAbsent(productId, key -> new DemandHistory());
                    history.add(rs.getDate("movement_day"), rs.getBigDecimal("qty"));
                }
            }
        }

        List<PlanningItem> items = new ArrayList<>();
        for (ProductBase product : products.values()) {
            DemandHistory history = demand.getOrDefault(product.id(), new DemandHistory());

            BigDecimal d7 = history.sumLastDays(7);
            BigDecimal d30 = history.sumLastDays(30);
            BigDecimal d90 = history.sumLastDays(90);

            BigDecimal avg7 = divide(d7, 7);
            BigDecimal avg30 = divide(d30, 30);
            BigDecimal avg90 = divide(d90, 90);

            BigDecimal forecastDaily = avg7.multiply(new BigDecimal("0.50"))
                    .add(avg30.multiply(new BigDecimal("0.30")))
                    .add(avg90.multiply(new BigDecimal("0.20")))
                    .setScale(3, RoundingMode.HALF_UP);

            BigDecimal stddev = history.stdDevLastDays(30);
            int leadTime = Math.max(product.leadTimeDays(), 1);

            BigDecimal safetyStock = BigDecimal.valueOf(
                    stddev.doubleValue() * Math.sqrt(leadTime)
            ).setScale(3, RoundingMode.HALF_UP);

            BigDecimal reorderPoint = forecastDaily.multiply(BigDecimal.valueOf(leadTime))
                    .add(safetyStock)
                    .setScale(3, RoundingMode.HALF_UP);

            BigDecimal targetStock = forecastDaily.multiply(BigDecimal.valueOf(leadTime + 14L))
                    .add(safetyStock)
                    .setScale(3, RoundingMode.HALF_UP);

            BigDecimal availableToPromise = product.currentStock().subtract(product.reservedQuantity());
            BigDecimal projectedAtLeadTime = availableToPromise
                    .add(product.incomingQuantity())
                    .subtract(forecastDaily.multiply(BigDecimal.valueOf(leadTime)))
                    .setScale(3, RoundingMode.HALF_UP);

            BigDecimal recommendedQuantity = targetStock
                    .subtract(availableToPromise.add(product.incomingQuantity()))
                    .max(BigDecimal.ZERO)
                    .setScale(0, RoundingMode.CEILING);

            String risk = riskLevel(projectedAtLeadTime, availableToPromise, reorderPoint, targetStock);
            String confidence = confidenceLevel(history.activeDaysLastDays(90), d90);

            items.add(new PlanningItem(
                    product.id(),
                    product.sku(),
                    product.name(),
                    product.category(),
                    product.currentStock(),
                    product.reservedQuantity(),
                    product.incomingQuantity(),
                    availableToPromise,
                    d7,
                    d30,
                    d90,
                    forecastDaily,
                    stddev,
                    leadTime,
                    safetyStock,
                    reorderPoint,
                    targetStock,
                    projectedAtLeadTime,
                    recommendedQuantity,
                    risk,
                    confidence
            ));
        }

        items.sort(
                Comparator.comparingInt((PlanningItem item) -> riskRank(item.riskLevel()))
                        .thenComparing(PlanningItem::recommendedQuantity, Comparator.reverseOrder())
                        .thenComparing(PlanningItem::productName, String.CASE_INSENSITIVE_ORDER)
        );

        return items.size() <= safeLimit ? items : new ArrayList<>(items.subList(0, safeLimit));
    }

    public PlanningSummary summary() throws SQLException {
        List<PlanningItem> items = planning(1000);
        int critical = 0;
        int high = 0;
        BigDecimal recommended = BigDecimal.ZERO;
        BigDecimal shortage = BigDecimal.ZERO;

        for (PlanningItem item : items) {
            if ("CRITICAL".equals(item.riskLevel())) critical++;
            if ("HIGH".equals(item.riskLevel())) high++;
            recommended = recommended.add(item.recommendedQuantity());
            if (item.projectedAtLeadTime().signum() < 0) {
                shortage = shortage.add(item.projectedAtLeadTime().abs());
            }
        }

        return new PlanningSummary(
                items.size(),
                critical,
                high,
                recommended,
                shortage.setScale(3, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal divide(BigDecimal value, int divisor) {
        return value.divide(BigDecimal.valueOf(divisor), 6, RoundingMode.HALF_UP);
    }

    private String riskLevel(
            BigDecimal projected,
            BigDecimal available,
            BigDecimal reorderPoint,
            BigDecimal targetStock
    ) {
        if (projected.signum() < 0) return "CRITICAL";
        if (available.compareTo(reorderPoint) <= 0) return "HIGH";
        if (available.compareTo(targetStock) < 0) return "ATTENTION";
        return "LOW";
    }

    private int riskRank(String risk) {
        return switch (risk) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "ATTENTION" -> 2;
            default -> 3;
        };
    }

    private String confidenceLevel(int activeDays, BigDecimal demand90) {
        if (demand90.signum() == 0) return "LOW";
        if (activeDays >= 30) return "HIGH";
        if (activeDays >= 10) return "MEDIUM";
        return "LOW";
    }

    private record ProductBase(
            long id,
            String sku,
            String name,
            String category,
            BigDecimal currentStock,
            BigDecimal reservedQuantity,
            BigDecimal incomingQuantity,
            int leadTimeDays
    ) {}

    private static final class DemandHistory {
        private final Map<java.time.LocalDate, BigDecimal> byDay = new HashMap<>();

        void add(Date date, BigDecimal quantity) {
            byDay.merge(date.toLocalDate(), quantity, BigDecimal::add);
        }

        BigDecimal sumLastDays(int days) {
            java.time.LocalDate cutoff = java.time.LocalDate.now().minusDays(days - 1L);
            return byDay.entrySet().stream()
                    .filter(entry -> !entry.getKey().isBefore(cutoff))
                    .map(Map.Entry::getValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        int activeDaysLastDays(int days) {
            java.time.LocalDate cutoff = java.time.LocalDate.now().minusDays(days - 1L);
            return (int) byDay.keySet().stream()
                    .filter(day -> !day.isBefore(cutoff))
                    .count();
        }

        BigDecimal stdDevLastDays(int days) {
            java.time.LocalDate start = java.time.LocalDate.now().minusDays(days - 1L);
            List<Double> values = new ArrayList<>();
            for (int i = 0; i < days; i++) {
                java.time.LocalDate day = start.plusDays(i);
                values.add(byDay.getOrDefault(day, BigDecimal.ZERO).doubleValue());
            }
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
            double variance = values.stream()
                    .mapToDouble(value -> Math.pow(value - mean, 2))
                    .average()
                    .orElse(0d);
            return BigDecimal.valueOf(Math.sqrt(variance)).setScale(3, RoundingMode.HALF_UP);
        }
    }
}
