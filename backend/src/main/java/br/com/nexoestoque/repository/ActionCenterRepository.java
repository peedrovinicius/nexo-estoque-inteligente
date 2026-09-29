package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.StockReservationRequest;
import br.com.nexoestoque.model.ActionCenter.*;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Repository
public class ActionCenterRepository {
    public static final String RULE_VERSION = "replenishment-v2.0.0";

    private final DataSource dataSource;

    public ActionCenterRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<ReplenishmentSuggestion> replenishmentSuggestions(
            int windowDays,
            BigDecimal demandVariationPercent,
            int limit
    ) throws SQLException {
        int safeWindow = Math.max(7, Math.min(windowDays, 365));
        int safeLimit = normalizeLimit(limit, 1000);
        BigDecimal variation = demandVariationPercent == null
                ? BigDecimal.ZERO
                : demandVariationPercent.max(BigDecimal.ZERO);
        BigDecimal factor = BigDecimal.ONE.add(
                variation.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
        );

        List<ReplenishmentSuggestion> suggestions = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.id,
                            p.sku,
                            p.name,
                            p.category,
                            p.current_stock,
                            p.minimum_stock,
                            p.cost_price,
                            COALESCE(m.exit_quantity,0) AS exit_quantity,
                            COALESCE(r.reserved_quantity,0) AS reserved_quantity,
                            COALESCE(i.incoming_quantity,0) AS incoming_quantity,
                            supplier_history.supplier_id,
                            supplier_history.supplier_name,
                            COALESCE(supplier_history.lead_time_days,0) AS lead_time_days,
                            supplier_history.last_unit_cost
                       FROM products p
                       LEFT JOIN (
                           SELECT product_id, SUM(quantity) AS exit_quantity
                             FROM (
                                   SELECT product_id, quantity, created_at
                                     FROM stock_movements
                                    WHERE movement_type='EXIT'
                                   UNION ALL
                                   SELECT product_id, quantity, created_at
                                     FROM stock_movements_archive
                                    WHERE movement_type='EXIT'
                             ) history
                            WHERE created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)
                            GROUP BY product_id
                       ) m ON m.product_id = p.id
                       LEFT JOIN (
                           SELECT product_id, SUM(quantity) AS reserved_quantity
                             FROM stock_reservations
                            WHERE status='ACTIVE'
                              AND (expires_at IS NULL OR expires_at > NOW())
                            GROUP BY product_id
                       ) r ON r.product_id = p.id
                       LEFT JOIN (
                           SELECT poi.product_id,
                                  SUM(GREATEST(poi.quantity - poi.received_quantity,0)) AS incoming_quantity
                             FROM purchase_order_items poi
                             JOIN purchase_orders po ON po.id = poi.purchase_order_id
                            WHERE po.status IN ('DRAFT','SENT','PARTIALLY_RECEIVED')
                            GROUP BY poi.product_id
                       ) i ON i.product_id = p.id
                       LEFT JOIN (
                           SELECT product_id, supplier_id, supplier_name, lead_time_days, last_unit_cost
                             FROM (
                                   SELECT poi.product_id,
                                          s.id AS supplier_id,
                                          s.name AS supplier_name,
                                          s.lead_time_days,
                                          poi.unit_cost AS last_unit_cost,
                                          ROW_NUMBER() OVER(
                                              PARTITION BY poi.product_id
                                              ORDER BY po.created_at DESC, po.id DESC
                                          ) AS rn
                                     FROM purchase_order_items poi
                                     JOIN purchase_orders po ON po.id = poi.purchase_order_id
                                     JOIN suppliers s ON s.id = po.supplier_id
                                    WHERE po.status <> 'CANCELLED'
                                      AND s.active = TRUE
                             ) ranked
                            WHERE rn = 1
                       ) supplier_history ON supplier_history.product_id = p.id
                      WHERE p.active = TRUE
                      ORDER BY p.name, p.id
                     """)) {
            statement.setInt(1, safeWindow);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    BigDecimal current = rs.getBigDecimal("current_stock");
                    BigDecimal minimum = rs.getBigDecimal("minimum_stock");
                    BigDecimal reserved = rs.getBigDecimal("reserved_quantity");
                    BigDecimal incoming = rs.getBigDecimal("incoming_quantity");
                    BigDecimal exitQuantity = rs.getBigDecimal("exit_quantity");

                    BigDecimal dailyDemand = exitQuantity.signum() == 0
                            ? BigDecimal.ZERO
                            : exitQuantity
                            .divide(BigDecimal.valueOf(safeWindow), 6, RoundingMode.HALF_UP)
                            .multiply(factor)
                            .setScale(3, RoundingMode.HALF_UP);

                    BigDecimal availableToPromise = current.subtract(reserved);
                    BigDecimal projectedAvailable = availableToPromise.add(incoming);

                    Long supplierId = (Long) rs.getObject("supplier_id");
                    int leadTime = rs.getInt("lead_time_days");
                    int safetyDays = Math.max(2, (int) Math.ceil(leadTime * 0.35d));

                    BigDecimal demandTarget = dailyDemand.multiply(
                            BigDecimal.valueOf((long) leadTime + safetyDays)
                    );
                    BigDecimal target = demandTarget.max(minimum);
                    BigDecimal recommended = target.subtract(projectedAvailable)
                            .max(BigDecimal.ZERO)
                            .setScale(0, RoundingMode.CEILING);

                    if (recommended.signum() <= 0) continue;

                    BigDecimal unitCost = rs.getBigDecimal("last_unit_cost");
                    if (unitCost == null || unitCost.signum() <= 0) {
                        unitCost = rs.getBigDecimal("cost_price");
                    }
                    if (unitCost == null) unitCost = BigDecimal.ZERO;

                    String risk = riskLevel(
                            availableToPromise,
                            minimum,
                            dailyDemand,
                            Math.max(leadTime, 1)
                    );

                    suggestions.add(new ReplenishmentSuggestion(
                            rs.getLong("id"),
                            rs.getString("sku"),
                            rs.getString("name"),
                            rs.getString("category"),
                            current,
                            minimum,
                            reserved,
                            incoming,
                            availableToPromise,
                            dailyDemand,
                            supplierId,
                            rs.getString("supplier_name"),
                            leadTime,
                            unitCost,
                            recommended,
                            recommended.multiply(unitCost).setScale(2, RoundingMode.HALF_UP),
                            risk,
                            supplierId == null
                    ));
                }
            }
        }

        suggestions.sort(
                Comparator.comparingInt((ReplenishmentSuggestion item) -> riskRank(item.riskLevel()))
                        .thenComparing(ReplenishmentSuggestion::recommendedQuantity, Comparator.reverseOrder())
                        .thenComparing(ReplenishmentSuggestion::productName, String.CASE_INSENSITIVE_ORDER)
        );

        return suggestions.size() <= safeLimit
                ? suggestions
                : new ArrayList<>(suggestions.subList(0, safeLimit));
    }

    public List<PurchaseApprovalItem> approvalQueue() throws SQLException {
        List<PurchaseApprovalItem> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT po.id AS purchase_order_id,
                            s.name AS supplier_name,
                            COALESCE(SUM(poi.quantity * poi.unit_cost),0) AS total_amount,
                            po.created_by,
                            po.created_at,
                            COALESCE(a.status,'NOT_REQUESTED') AS approval_status,
                            a.requested_by,
                            a.requested_at,
                            a.decided_by,
                            a.decided_at,
                            a.decision_reason
                       FROM purchase_orders po
                       JOIN suppliers s ON s.id = po.supplier_id
                       LEFT JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
                       LEFT JOIN purchase_order_approvals a ON a.purchase_order_id = po.id
                      WHERE po.source = 'REPLENISHMENT_RECOMMENDATION'
                        AND po.status = 'DRAFT'
                      GROUP BY po.id, s.name, po.created_by, po.created_at,
                               a.status, a.requested_by, a.requested_at,
                               a.decided_by, a.decided_at, a.decision_reason
                      ORDER BY
                        CASE COALESCE(a.status,'NOT_REQUESTED')
                          WHEN 'PENDING' THEN 0
                          WHEN 'NOT_REQUESTED' THEN 1
                          WHEN 'REJECTED' THEN 2
                          ELSE 3
                        END,
                        po.created_at,
                        po.id
                     """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                items.add(mapApproval(rs));
            }
        }
        return items;
    }

    public PurchaseApprovalItem requestApproval(long orderId, String actor) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureRecommendationDraft(connection, orderId);

                String existingStatus = null;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT status
                          FROM purchase_order_approvals
                         WHERE purchase_order_id = ?
                         FOR UPDATE
                        """)) {
                    statement.setLong(1, orderId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) existingStatus = rs.getString("status");
                    }
                }

                if ("APPROVED".equals(existingStatus)) {
                    connection.commit();
                    return approvalByOrder(connection, orderId);
                }

                if (existingStatus == null) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO purchase_order_approvals(
                                purchase_order_id,status,requested_by
                            ) VALUES(?, 'PENDING', ?)
                            """)) {
                        statement.setLong(1, orderId);
                        statement.setString(2, safeActor(actor));
                        statement.executeUpdate();
                    }
                } else if ("REJECTED".equals(existingStatus)) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            UPDATE purchase_order_approvals
                               SET status='PENDING',
                                   requested_by=?,
                                   requested_at=CURRENT_TIMESTAMP,
                                   decided_by=NULL,
                                   decided_at=NULL,
                                   decision_reason=NULL
                             WHERE purchase_order_id=?
                            """)) {
                        statement.setString(1, safeActor(actor));
                        statement.setLong(2, orderId);
                        statement.executeUpdate();
                    }
                }

                connection.commit();
                return approvalByOrder(connection, orderId);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public PurchaseApprovalItem decideApproval(
            long orderId,
            boolean approve,
            String actor,
            String reason
    ) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureRecommendationDraft(connection, orderId);

                String status;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT status
                          FROM purchase_order_approvals
                         WHERE purchase_order_id = ?
                         FOR UPDATE
                        """)) {
                    statement.setLong(1, orderId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw business("Pedido ainda não foi enviado para aprovação");
                        status = rs.getString("status");
                    }
                }

                if (!"PENDING".equals(status)) {
                    throw business("Aprovação não está pendente");
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE purchase_order_approvals
                           SET status=?,
                               decided_by=?,
                               decided_at=CURRENT_TIMESTAMP,
                               decision_reason=?
                         WHERE purchase_order_id=?
                        """)) {
                    statement.setString(1, approve ? "APPROVED" : "REJECTED");
                    statement.setString(2, safeActor(actor));
                    String normalizedReason = reason == null ? "" : reason.trim();
                    if (normalizedReason.isEmpty()) statement.setNull(3, Types.VARCHAR);
                    else statement.setString(3, normalizedReason);
                    statement.setLong(4, orderId);
                    statement.executeUpdate();
                }

                connection.commit();
                return approvalByOrder(connection, orderId);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean isRecommendationApproved(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT EXISTS(
                    SELECT 1
                      FROM purchase_order_approvals
                     WHERE purchase_order_id = ?
                       AND status = 'APPROVED'
                )
                """)) {
            statement.setLong(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    public StockReservation createReservation(
            StockReservationRequest request,
            String actor
    ) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                expireReservations(connection);

                BigDecimal currentStock;
                String productName;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT current_stock, name
                          FROM products
                         WHERE id = ?
                           AND active = TRUE
                         FOR UPDATE
                        """)) {
                    statement.setLong(1, request.productId());
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw business("Produto inexistente ou inativo");
                        currentStock = rs.getBigDecimal("current_stock");
                        productName = rs.getString("name");
                    }
                }

                BigDecimal reserved = activeReserved(connection, request.productId());
                BigDecimal available = currentStock.subtract(reserved);
                if (available.compareTo(request.quantity()) < 0) {
                    throw business("Estoque disponível insuficiente para reservar " + productName);
                }

                long id;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO stock_reservations(
                            product_id,quantity,reference_code,notes,reserved_by,expires_at
                        ) VALUES(?,?,?,?,?,?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, request.productId());
                    statement.setBigDecimal(2, request.quantity());
                    statement.setString(3, request.referenceCode().trim());
                    String notes = request.notes() == null ? "" : request.notes().trim();
                    if (notes.isEmpty()) statement.setNull(4, Types.VARCHAR);
                    else statement.setString(4, notes);
                    statement.setString(5, safeActor(actor));
                    if (request.expiresAt() == null) statement.setNull(6, Types.TIMESTAMP);
                    else statement.setTimestamp(6, Timestamp.valueOf(request.expiresAt()));
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }

                connection.commit();
                return reservationById(id);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<StockReservation> reservations(String status) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            expireReservations(connection);
        }

        List<StockReservation> items = new ArrayList<>();
        String normalizedStatus = normalizeText(status);
        if (normalizedStatus != null) normalizedStatus = normalizedStatus.toUpperCase();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT r.id,
                            r.product_id,
                            p.sku,
                            p.name AS product_name,
                            r.quantity,
                            r.reference_code,
                            r.notes,
                            r.status,
                            r.reserved_by,
                            r.expires_at,
                            r.created_at,
                            p.current_stock,
                            COALESCE(active.total_reserved,0) AS active_reserved
                       FROM stock_reservations r
                       JOIN products p ON p.id = r.product_id
                       LEFT JOIN (
                           SELECT product_id, SUM(quantity) AS total_reserved
                             FROM stock_reservations
                            WHERE status='ACTIVE'
                              AND (expires_at IS NULL OR expires_at > NOW())
                            GROUP BY product_id
                       ) active ON active.product_id = r.product_id
                      WHERE (? IS NULL OR r.status = ?)
                      ORDER BY
                        CASE r.status WHEN 'ACTIVE' THEN 0 ELSE 1 END,
                        r.expires_at IS NULL,
                        r.expires_at,
                        r.created_at DESC,
                        r.id DESC
                     """)) {
            setNullableText(statement, 1, normalizedStatus);
            setNullableText(statement, 2, normalizedStatus);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) items.add(mapReservation(rs));
            }
        }
        return items;
    }

    public StockReservation cancelReservation(long id, String actor) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                expireReservations(connection);
                String status;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT status
                          FROM stock_reservations
                         WHERE id = ?
                         FOR UPDATE
                        """)) {
                    statement.setLong(1, id);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) throw business("Reserva não encontrada");
                        status = rs.getString("status");
                    }
                }

                if (!"ACTIVE".equals(status)) {
                    throw business("Somente reserva ativa pode ser cancelada");
                }

                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE stock_reservations
                           SET status='CANCELLED',
                               cancelled_by=?,
                               cancelled_at=CURRENT_TIMESTAMP
                         WHERE id=?
                        """)) {
                    statement.setString(1, safeActor(actor));
                    statement.setLong(2, id);
                    statement.executeUpdate();
                }

                connection.commit();
                return reservationById(id);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public DailyActionQueue dailyActions() throws SQLException {
        List<DailyActionItem> actions = new ArrayList<>();

        for (ReplenishmentSuggestion item : replenishmentSuggestions(30, BigDecimal.ZERO, 100)) {
            actions.add(new DailyActionItem(
                    "replenishment-" + item.productId(),
                    "REPLENISHMENT",
                    "CRITICAL".equals(item.riskLevel()) || "HIGH".equals(item.riskLevel()) ? "CRITICAL" : "WARNING",
                    item.productName(),
                    item.supplierRequired()
                            ? "Reposição necessária, mas ainda sem fornecedor histórico"
                            : "Reposição sugerida com " + item.supplierName(),
                    item.recommendedQuantity() + " un.",
                    "replenishment"
            ));
        }

        for (PurchaseApprovalItem item : approvalQueue()) {
            if ("PENDING".equals(item.approvalStatus())) {
                actions.add(new DailyActionItem(
                        "approval-" + item.purchaseOrderId(),
                        "PURCHASE_APPROVAL",
                        "WARNING",
                        "Pedido #" + item.purchaseOrderId() + " aguarda aprovação",
                        item.supplierName(),
                        item.totalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                        "approvals"
                ));
            }
        }

        for (StockReservation item : reservations("ACTIVE")) {
            if (item.expiresAt() != null && item.expiresAt().isBefore(LocalDateTime.now().plusHours(24))) {
                actions.add(new DailyActionItem(
                        "reservation-" + item.id(),
                        "RESERVATION",
                        "INFO",
                        item.productName(),
                        "Reserva " + item.referenceCode() + " expira em menos de 24h",
                        item.quantity() + " un.",
                        "reservations"
                ));
            }
        }

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT po.id, s.name,
                            DATEDIFF(CURDATE(), po.expected_at) AS overdue_days
                       FROM purchase_orders po
                       JOIN suppliers s ON s.id = po.supplier_id
                      WHERE po.status IN ('SENT','PARTIALLY_RECEIVED')
                        AND po.expected_at IS NOT NULL
                        AND po.expected_at < CURDATE()
                      ORDER BY overdue_days DESC, po.id
                      LIMIT 100
                     """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                actions.add(new DailyActionItem(
                        "overdue-order-" + rs.getLong("id"),
                        "OVERDUE_PURCHASE",
                        "WARNING",
                        "Pedido #" + rs.getLong("id") + " em atraso",
                        rs.getString("name"),
                        rs.getInt("overdue_days") + " d",
                        "approvals"
                ));
            }
        }

        actions.sort(
                Comparator.comparingInt((DailyActionItem item) -> severityRank(item.severity()))
                        .thenComparing(DailyActionItem::title, String.CASE_INSENSITIVE_ORDER)
        );

        int critical = (int) actions.stream().filter(item -> "CRITICAL".equals(item.severity())).count();
        int warning = (int) actions.stream().filter(item -> "WARNING".equals(item.severity())).count();
        int info = (int) actions.stream().filter(item -> "INFO".equals(item.severity())).count();

        return new DailyActionQueue(LocalDateTime.now(), critical, warning, info, actions);
    }

    private PurchaseApprovalItem approvalByOrder(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT po.id AS purchase_order_id,
                       s.name AS supplier_name,
                       COALESCE(SUM(poi.quantity * poi.unit_cost),0) AS total_amount,
                       po.created_by,
                       po.created_at,
                       COALESCE(a.status,'NOT_REQUESTED') AS approval_status,
                       a.requested_by,
                       a.requested_at,
                       a.decided_by,
                       a.decided_at,
                       a.decision_reason
                  FROM purchase_orders po
                  JOIN suppliers s ON s.id = po.supplier_id
                  LEFT JOIN purchase_order_items poi ON poi.purchase_order_id = po.id
                  LEFT JOIN purchase_order_approvals a ON a.purchase_order_id = po.id
                 WHERE po.id = ?
                 GROUP BY po.id, s.name, po.created_by, po.created_at,
                          a.status, a.requested_by, a.requested_at,
                          a.decided_by, a.decided_at, a.decision_reason
                """)) {
            statement.setLong(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw business("Pedido não encontrado");
                return mapApproval(rs);
            }
        }
    }

    private PurchaseApprovalItem mapApproval(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp requested = rs.getTimestamp("requested_at");
        Timestamp decided = rs.getTimestamp("decided_at");
        return new PurchaseApprovalItem(
                rs.getLong("purchase_order_id"),
                rs.getString("supplier_name"),
                rs.getBigDecimal("total_amount"),
                rs.getString("created_by"),
                created == null ? null : created.toLocalDateTime(),
                rs.getString("approval_status"),
                rs.getString("requested_by"),
                requested == null ? null : requested.toLocalDateTime(),
                rs.getString("decided_by"),
                decided == null ? null : decided.toLocalDateTime(),
                rs.getString("decision_reason")
        );
    }

    private void ensureRecommendationDraft(Connection connection, long orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT source, status
                  FROM purchase_orders
                 WHERE id=?
                 FOR UPDATE
                """)) {
            statement.setLong(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw business("Pedido de compra não encontrado");
                if (!"REPLENISHMENT_RECOMMENDATION".equals(rs.getString("source"))) {
                    throw business("Somente pedidos de reposição assistida usam este fluxo de aprovação");
                }
                if (!"DRAFT".equals(rs.getString("status"))) {
                    throw business("Somente pedido em rascunho pode passar por aprovação");
                }
            }
        }
    }

    private void expireReservations(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE stock_reservations
                   SET status='EXPIRED'
                 WHERE status='ACTIVE'
                   AND expires_at IS NOT NULL
                   AND expires_at <= NOW()
                """)) {
            statement.executeUpdate();
        }
    }

    private BigDecimal activeReserved(Connection connection, long productId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COALESCE(SUM(quantity),0)
                  FROM stock_reservations
                 WHERE product_id=?
                   AND status='ACTIVE'
                   AND (expires_at IS NULL OR expires_at > NOW())
                """)) {
            statement.setLong(1, productId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private StockReservation reservationById(long id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT r.id,
                            r.product_id,
                            p.sku,
                            p.name AS product_name,
                            r.quantity,
                            r.reference_code,
                            r.notes,
                            r.status,
                            r.reserved_by,
                            r.expires_at,
                            r.created_at,
                            p.current_stock,
                            COALESCE(active.total_reserved,0) AS active_reserved
                       FROM stock_reservations r
                       JOIN products p ON p.id = r.product_id
                       LEFT JOIN (
                           SELECT product_id, SUM(quantity) AS total_reserved
                             FROM stock_reservations
                            WHERE status='ACTIVE'
                              AND (expires_at IS NULL OR expires_at > NOW())
                            GROUP BY product_id
                       ) active ON active.product_id = r.product_id
                      WHERE r.id=?
                     """)) {
            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw business("Reserva não encontrada");
                return mapReservation(rs);
            }
        }
    }

    private StockReservation mapReservation(ResultSet rs) throws SQLException {
        Timestamp expires = rs.getTimestamp("expires_at");
        Timestamp created = rs.getTimestamp("created_at");
        BigDecimal stock = rs.getBigDecimal("current_stock");
        BigDecimal activeReserved = rs.getBigDecimal("active_reserved");
        return new StockReservation(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getString("sku"),
                rs.getString("product_name"),
                rs.getBigDecimal("quantity"),
                rs.getString("reference_code"),
                rs.getString("notes"),
                rs.getString("status"),
                rs.getString("reserved_by"),
                expires == null ? null : expires.toLocalDateTime(),
                created == null ? null : created.toLocalDateTime(),
                stock,
                activeReserved,
                stock.subtract(activeReserved)
        );
    }

    private String riskLevel(
            BigDecimal available,
            BigDecimal minimum,
            BigDecimal dailyDemand,
            int leadTimeDays
    ) {
        if (available.signum() <= 0) return "CRITICAL";
        if (dailyDemand.signum() > 0) {
            BigDecimal coverage = available.divide(dailyDemand, 2, RoundingMode.HALF_UP);
            if (coverage.compareTo(BigDecimal.valueOf(leadTimeDays)) < 0) return "HIGH";
        }
        if (available.compareTo(minimum) < 0) return "MODERATE";
        return "ATTENTION";
    }

    private int riskRank(String risk) {
        return switch (risk) {
            case "CRITICAL" -> 0;
            case "HIGH" -> 1;
            case "MODERATE" -> 2;
            default -> 3;
        };
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }

    private int normalizeLimit(int limit, int max) {
        if (limit <= 0) return Math.min(100, max);
        return Math.min(limit, max);
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

    private String safeActor(String actor) {
        return actor == null || actor.isBlank() ? "system" : actor;
    }

    private SQLException business(String message) {
        return new SQLException(message, "45000");
    }
}
