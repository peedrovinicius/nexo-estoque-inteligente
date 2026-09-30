package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.OperationalExceptionRequest;
import br.com.nexoestoque.dto.ReplenishmentPolicyRequest;
import br.com.nexoestoque.model.ActionCenter.DailyActionItem;
import br.com.nexoestoque.model.ActionCenter.DailyActionQueue;
import br.com.nexoestoque.model.OperationalGovernance.*;

import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Repository
public class OperationalGovernanceRepository {
    private final DataSource dataSource;
    private final ActionCenterRepository actionCenterRepository;

    public OperationalGovernanceRepository(
            DataSource dataSource,
            ActionCenterRepository actionCenterRepository
    ) {
        this.dataSource = dataSource;
        this.actionCenterRepository = actionCenterRepository;
    }

    public List<ReplenishmentPolicy> policies() throws SQLException {
        List<ReplenishmentPolicy> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.id AS product_id,
                            p.sku,
                            p.name AS product_name,
                            COALESCE(pol.enabled,TRUE) AS enabled,
                            COALESCE(pol.target_coverage_days,14) AS target_coverage_days,
                            COALESCE(pol.safety_stock_multiplier,1.000) AS safety_stock_multiplier,
                            COALESCE(pol.minimum_order_quantity,1.000) AS minimum_order_quantity,
                            COALESCE(pol.order_multiple,1.000) AS order_multiple,
                            pol.preferred_supplier_id,
                            s.name AS preferred_supplier_name,
                            COALESCE(pol.updated_by,'default') AS updated_by,
                            pol.updated_at
                       FROM products p
                       LEFT JOIN product_replenishment_policies pol ON pol.product_id=p.id
                       LEFT JOIN suppliers s ON s.id=pol.preferred_supplier_id
                      WHERE p.active=TRUE
                      ORDER BY p.name,p.id
                     """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) items.add(mapPolicy(rs));
        }
        return items;
    }

    public ReplenishmentPolicy upsertPolicy(
            long productId,
            ReplenishmentPolicyRequest request,
            String actor
    ) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            ensureProduct(connection, productId);
            if (request.preferredSupplierId() != null) {
                ensureSupplier(connection, request.preferredSupplierId());
            }

            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO product_replenishment_policies(
                        product_id,enabled,target_coverage_days,safety_stock_multiplier,
                        minimum_order_quantity,order_multiple,preferred_supplier_id,updated_by
                    ) VALUES(?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        enabled=VALUES(enabled),
                        target_coverage_days=VALUES(target_coverage_days),
                        safety_stock_multiplier=VALUES(safety_stock_multiplier),
                        minimum_order_quantity=VALUES(minimum_order_quantity),
                        order_multiple=VALUES(order_multiple),
                        preferred_supplier_id=VALUES(preferred_supplier_id),
                        updated_by=VALUES(updated_by),
                        updated_at=CURRENT_TIMESTAMP
                    """)) {
                statement.setLong(1, productId);
                statement.setBoolean(2, request.enabled());
                statement.setInt(3, request.targetCoverageDays());
                statement.setBigDecimal(4, request.safetyStockMultiplier());
                statement.setBigDecimal(5, request.minimumOrderQuantity());
                statement.setBigDecimal(6, request.orderMultiple());
                if (request.preferredSupplierId() == null) statement.setNull(7, Types.BIGINT);
                else statement.setLong(7, request.preferredSupplierId());
                statement.setString(8, safeActor(actor));
                statement.executeUpdate();
            }
        }
        return policy(productId);
    }

    public ReplenishmentPolicy policy(long productId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT p.id AS product_id,
                            p.sku,
                            p.name AS product_name,
                            COALESCE(pol.enabled,TRUE) AS enabled,
                            COALESCE(pol.target_coverage_days,14) AS target_coverage_days,
                            COALESCE(pol.safety_stock_multiplier,1.000) AS safety_stock_multiplier,
                            COALESCE(pol.minimum_order_quantity,1.000) AS minimum_order_quantity,
                            COALESCE(pol.order_multiple,1.000) AS order_multiple,
                            pol.preferred_supplier_id,
                            s.name AS preferred_supplier_name,
                            COALESCE(pol.updated_by,'default') AS updated_by,
                            pol.updated_at
                       FROM products p
                       LEFT JOIN product_replenishment_policies pol ON pol.product_id=p.id
                       LEFT JOIN suppliers s ON s.id=pol.preferred_supplier_id
                      WHERE p.id=?
                     """)) {
            statement.setLong(1, productId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw business("Produto não encontrado");
                return mapPolicy(rs);
            }
        }
    }

    public List<OperationalException> exceptions(String status) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            expireExceptions(connection);
        }
        List<OperationalException> items = new ArrayList<>();
        String normalized = normalizeText(status);
        if (normalized != null) normalized = normalized.toUpperCase();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT e.id,e.product_id,p.sku,p.name AS product_name,
                            e.exception_type,e.reason,e.status,e.starts_at,e.expires_at,
                            e.created_by,e.cancelled_by,e.cancelled_at,e.created_at
                       FROM operational_exceptions e
                       JOIN products p ON p.id=e.product_id
                      WHERE (? IS NULL OR e.status=?)
                      ORDER BY
                        CASE e.status WHEN 'ACTIVE' THEN 0 WHEN 'EXPIRED' THEN 1 ELSE 2 END,
                        e.expires_at,e.id DESC
                     """)) {
            setNullableText(statement,1,normalized);
            setNullableText(statement,2,normalized);
            try (ResultSet rs=statement.executeQuery()) {
                while(rs.next()) items.add(mapException(rs));
            }
        }
        return items;
    }

    public OperationalException createException(
            OperationalExceptionRequest request,
            String actor
    ) throws SQLException {
        String type=request.exceptionType()==null?"":request.exceptionType().trim().toUpperCase();
        if(!Set.of("REPLENISHMENT_PAUSE","COUNTING_PAUSE").contains(type)){
            throw business("Tipo de exceção inválido");
        }

        long id;
        try(Connection connection=dataSource.getConnection()){
            connection.setAutoCommit(false);
            try{
                expireExceptions(connection);
                ensureProduct(connection,request.productId());

                try(PreparedStatement statement=connection.prepareStatement("""
                        SELECT COUNT(*)
                          FROM operational_exceptions
                         WHERE product_id=?
                           AND exception_type=?
                           AND status='ACTIVE'
                           AND expires_at>NOW()
                        """)){
                    statement.setLong(1,request.productId());
                    statement.setString(2,type);
                    try(ResultSet rs=statement.executeQuery()){
                        rs.next();
                        if(rs.getInt(1)>0) throw business("Já existe exceção ativa desse tipo para o produto");
                    }
                }

                try(PreparedStatement statement=connection.prepareStatement("""
                        INSERT INTO operational_exceptions(
                            product_id,exception_type,reason,expires_at,created_by
                        ) VALUES(?,?,?,?,?)
                        """,Statement.RETURN_GENERATED_KEYS)){
                    statement.setLong(1,request.productId());
                    statement.setString(2,type);
                    statement.setString(3,request.reason().trim());
                    statement.setTimestamp(4,Timestamp.valueOf(request.expiresAt()));
                    statement.setString(5,safeActor(actor));
                    statement.executeUpdate();
                    try(ResultSet keys=statement.getGeneratedKeys()){
                        keys.next();
                        id=keys.getLong(1);
                    }
                }
                connection.commit();
            }catch(SQLException|RuntimeException exception){
                connection.rollback();
                throw exception;
            }finally{
                connection.setAutoCommit(true);
            }
        }
        return exceptionById(id);
    }

    public OperationalException cancelException(long id,String actor) throws SQLException {
        try(Connection connection=dataSource.getConnection()){
            connection.setAutoCommit(false);
            try{
                expireExceptions(connection);
                String status;
                try(PreparedStatement statement=connection.prepareStatement("""
                        SELECT status FROM operational_exceptions WHERE id=? FOR UPDATE
                        """)){
                    statement.setLong(1,id);
                    try(ResultSet rs=statement.executeQuery()){
                        if(!rs.next()) throw business("Exceção não encontrada");
                        status=rs.getString("status");
                    }
                }
                if(!"ACTIVE".equals(status)) throw business("Somente exceção ativa pode ser cancelada");

                try(PreparedStatement statement=connection.prepareStatement("""
                        UPDATE operational_exceptions
                           SET status='CANCELLED',cancelled_by=?,cancelled_at=CURRENT_TIMESTAMP
                         WHERE id=?
                        """)){
                    statement.setString(1,safeActor(actor));
                    statement.setLong(2,id);
                    statement.executeUpdate();
                }
                connection.commit();
            }catch(SQLException|RuntimeException exception){
                connection.rollback();
                throw exception;
            }finally{
                connection.setAutoCommit(true);
            }
        }
        return exceptionById(id);
    }

    public List<CycleCountSuggestion> cycleCounts(int limit) throws SQLException {
        int safeLimit=limit<=0?200:Math.min(limit,1000);
        List<CycleBase> rows=new ArrayList<>();

        try(Connection connection=dataSource.getConnection()){
            expireExceptions(connection);
            try(PreparedStatement statement=connection.prepareStatement("""
                    SELECT p.id,p.sku,p.name,p.category,
                           COALESCE(SUM(CASE WHEN b.quantity>0 THEN b.quantity*b.unit_cost ELSE 0 END),0) AS stock_value,
                           last_count.counted_at,
                           last_count.difference_quantity,
                           EXISTS(
                               SELECT 1
                                 FROM operational_exceptions e
                                WHERE e.product_id=p.id
                                  AND e.exception_type='COUNTING_PAUSE'
                                  AND e.status='ACTIVE'
                                  AND e.expires_at>NOW()
                           ) AS paused
                      FROM products p
                      LEFT JOIN stock_batches b ON b.product_id=p.id
                      LEFT JOIN (
                          SELECT product_id,counted_at,difference_quantity
                            FROM (
                                SELECT c.product_id,c.counted_at,c.difference_quantity,
                                       ROW_NUMBER() OVER(
                                           PARTITION BY c.product_id
                                           ORDER BY c.counted_at DESC,c.id DESC
                                       ) AS rn
                                  FROM blind_inventory_counts c
                            ) ranked
                           WHERE rn=1
                      ) last_count ON last_count.product_id=p.id
                     WHERE p.active=TRUE
                     GROUP BY p.id,p.sku,p.name,p.category,
                              last_count.counted_at,last_count.difference_quantity
                     ORDER BY stock_value DESC,p.name,p.id
                    """);
                ResultSet rs=statement.executeQuery()){
                while(rs.next()){
                    Timestamp counted=rs.getTimestamp("counted_at");
                    rows.add(new CycleBase(
                            rs.getLong("id"),
                            rs.getString("sku"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getBigDecimal("stock_value"),
                            counted==null?null:counted.toLocalDateTime(),
                            rs.getBigDecimal("difference_quantity"),
                            rs.getBoolean("paused")
                    ));
                }
            }
        }

        BigDecimal total=rows.stream().map(CycleBase::stockValue).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal cumulative=BigDecimal.ZERO;
        List<CycleCountSuggestion> result=new ArrayList<>();
        LocalDateTime now=LocalDateTime.now();

        for(CycleBase row:rows){
            cumulative=cumulative.add(row.stockValue());
            BigDecimal percent=total.signum()==0?BigDecimal.ZERO:
                    cumulative.multiply(BigDecimal.valueOf(100)).divide(total,2,RoundingMode.HALF_UP);
            String abc=percent.compareTo(new BigDecimal("80"))<=0?"A":
                    percent.compareTo(new BigDecimal("95"))<=0?"B":"C";
            if(total.signum()==0) abc="C";

            int frequency=switch(abc){case "A"->30;case "B"->60;default->90;};
            BigDecimal diff=row.lastDifference()==null?BigDecimal.ZERO:row.lastDifference().abs();
            if(diff.signum()>0) frequency=Math.min(frequency,14);

            Integer daysSince=row.lastCountedAt()==null?null:
                    (int)Math.max(0,ChronoUnit.DAYS.between(row.lastCountedAt(),now));
            Integer overdue=daysSince==null?frequency:Math.max(0,daysSince-frequency);

            String priority;
            if(row.paused()) priority="PAUSED";
            else if(daysSince==null) priority="A".equals(abc)?"CRITICAL":"HIGH";
            else if(overdue>=30) priority="CRITICAL";
            else if(overdue>0) priority="HIGH";
            else if(frequency-daysSince<=7) priority="ATTENTION";
            else priority="LOW";

            result.add(new CycleCountSuggestion(
                    row.productId(),row.sku(),row.productName(),row.category(),abc,row.stockValue(),
                    row.lastCountedAt(),row.lastDifference(),frequency,daysSince,overdue,priority,row.paused()
            ));
        }

        result.sort(Comparator
                .comparingInt((CycleCountSuggestion item)->priorityRank(item.priority()))
                .thenComparing(CycleCountSuggestion::daysOverdue,Comparator.nullsFirst(Comparator.reverseOrder()))
                .thenComparing(CycleCountSuggestion::stockValue,Comparator.reverseOrder()));

        return result.size()<=safeLimit?result:new ArrayList<>(result.subList(0,safeLimit));
    }

    public List<GovernedAction> governedActions(boolean includeResolved) throws SQLException {
        syncActionStates();
        List<GovernedAction> items=new ArrayList<>();

        try(Connection connection=dataSource.getConnection();
            PreparedStatement statement=connection.prepareStatement("""
                    SELECT action_key,action_type,severity,title_snapshot,description_snapshot,
                           value_snapshot,action_target,status,first_seen_at,last_seen_at,
                           acknowledged_at,acknowledged_by,acknowledgement_note,resolved_at
                      FROM operational_action_states
                     WHERE (?=TRUE OR status<>'RESOLVED')
                     ORDER BY
                       CASE status WHEN 'OPEN' THEN 0 WHEN 'ACKNOWLEDGED' THEN 1 ELSE 2 END,
                       CASE severity WHEN 'CRITICAL' THEN 0 WHEN 'WARNING' THEN 1 ELSE 2 END,
                       first_seen_at,action_key
                    """)){
            statement.setBoolean(1,includeResolved);
            try(ResultSet rs=statement.executeQuery()){
                while(rs.next()) items.add(mapGovernedAction(rs));
            }
        }
        return items;
    }

    public GovernedAction acknowledgeAction(
            String key,
            String actor,
            String note
    ) throws SQLException {
        syncActionStates();
        try(Connection connection=dataSource.getConnection();
            PreparedStatement statement=connection.prepareStatement("""
                    UPDATE operational_action_states
                       SET status='ACKNOWLEDGED',
                           acknowledged_at=COALESCE(acknowledged_at,CURRENT_TIMESTAMP),
                           acknowledged_by=COALESCE(acknowledged_by,?),
                           acknowledgement_note=?
                     WHERE action_key=?
                       AND status<>'RESOLVED'
                    """)){
            statement.setString(1,safeActor(actor));
            String normalized=normalizeText(note);
            if(normalized==null) statement.setNull(2,Types.VARCHAR);
            else statement.setString(2,normalized);
            statement.setString(3,key);
            if(statement.executeUpdate()==0) throw business("Ação não encontrada ou já resolvida");
        }
        return governedActionByKey(key);
    }

    public GovernanceSummary summary() throws SQLException {
        List<GovernedAction> actions=governedActions(false);
        List<CycleCountSuggestion> cycles=cycleCounts(1000);
        int activeExceptions=exceptions("ACTIVE").size();
        int due=(int)cycles.stream().filter(item->
                !item.paused()&&Set.of("CRITICAL","HIGH","ATTENTION").contains(item.priority())
        ).count();
        int open=(int)actions.stream().filter(item->"OPEN".equals(item.status())).count();
        int acknowledged=(int)actions.stream().filter(item->"ACKNOWLEDGED".equals(item.status())).count();
        int breaches=(int)actions.stream().filter(GovernedAction::slaBreached).count();
        return new GovernanceSummary(open,acknowledged,breaches,activeExceptions,due,actions);
    }

    public boolean replenishmentPaused(long productId) throws SQLException {
        try(Connection connection=dataSource.getConnection()){
            expireExceptions(connection);
            try(PreparedStatement statement=connection.prepareStatement("""
                    SELECT EXISTS(
                        SELECT 1
                          FROM operational_exceptions
                         WHERE product_id=?
                           AND exception_type='REPLENISHMENT_PAUSE'
                           AND status='ACTIVE'
                           AND expires_at>NOW()
                    )
                    """)){
                statement.setLong(1,productId);
                try(ResultSet rs=statement.executeQuery()){
                    rs.next();
                    return rs.getBoolean(1);
                }
            }
        }
    }

    private void syncActionStates() throws SQLException {
        DailyActionQueue queue=actionCenterRepository.dailyActions();
        Map<String,DailyActionItem> current=new LinkedHashMap<>();
        for(DailyActionItem item:queue.items()) current.put(item.key(),item);

        try(Connection connection=dataSource.getConnection()){
            connection.setAutoCommit(false);
            try{
                for(DailyActionItem item:current.values()){
                    String status=null;
                    try(PreparedStatement statement=connection.prepareStatement("""
                            SELECT status
                              FROM operational_action_states
                             WHERE action_key=?
                             FOR UPDATE
                            """)){
                        statement.setString(1,item.key());
                        try(ResultSet rs=statement.executeQuery()){
                            if(rs.next()) status=rs.getString("status");
                        }
                    }

                    if(status==null){
                        try(PreparedStatement statement=connection.prepareStatement("""
                                INSERT INTO operational_action_states(
                                    action_key,action_type,severity,title_snapshot,description_snapshot,
                                    value_snapshot,action_target
                                ) VALUES(?,?,?,?,?,?,?)
                                """)){
                            bindAction(statement,item);
                            statement.executeUpdate();
                        }
                    }else if("RESOLVED".equals(status)){
                        try(PreparedStatement statement=connection.prepareStatement("""
                                UPDATE operational_action_states
                                   SET action_type=?,severity=?,title_snapshot=?,description_snapshot=?,
                                       value_snapshot=?,action_target=?,status='OPEN',
                                       first_seen_at=CURRENT_TIMESTAMP,last_seen_at=CURRENT_TIMESTAMP,
                                       acknowledged_at=NULL,acknowledged_by=NULL,acknowledgement_note=NULL,
                                       resolved_at=NULL
                                 WHERE action_key=?
                                """)){
                            statement.setString(1,item.type());
                            statement.setString(2,item.severity());
                            statement.setString(3,item.title());
                            statement.setString(4,item.description());
                            statement.setString(5,item.value());
                            statement.setString(6,item.actionTarget());
                            statement.setString(7,item.key());
                            statement.executeUpdate();
                        }
                    }else{
                        try(PreparedStatement statement=connection.prepareStatement("""
                                UPDATE operational_action_states
                                   SET action_type=?,severity=?,title_snapshot=?,description_snapshot=?,
                                       value_snapshot=?,action_target=?,last_seen_at=CURRENT_TIMESTAMP
                                 WHERE action_key=?
                                """)){
                            statement.setString(1,item.type());
                            statement.setString(2,item.severity());
                            statement.setString(3,item.title());
                            statement.setString(4,item.description());
                            statement.setString(5,item.value());
                            statement.setString(6,item.actionTarget());
                            statement.setString(7,item.key());
                            statement.executeUpdate();
                        }
                    }
                }

                List<String> activeKeys=new ArrayList<>();
                try(PreparedStatement statement=connection.prepareStatement("""
                        SELECT action_key
                          FROM operational_action_states
                         WHERE status<>'RESOLVED'
                         FOR UPDATE
                        """);
                    ResultSet rs=statement.executeQuery()){
                    while(rs.next()) activeKeys.add(rs.getString("action_key"));
                }

                for(String key:activeKeys){
                    if(current.containsKey(key)) continue;
                    try(PreparedStatement statement=connection.prepareStatement("""
                            UPDATE operational_action_states
                               SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP
                             WHERE action_key=?
                            """)){
                        statement.setString(1,key);
                        statement.executeUpdate();
                    }
                }

                connection.commit();
            }catch(SQLException|RuntimeException exception){
                connection.rollback();
                throw exception;
            }finally{
                connection.setAutoCommit(true);
            }
        }
    }

    private GovernedAction governedActionByKey(String key) throws SQLException {
        try(Connection connection=dataSource.getConnection();
            PreparedStatement statement=connection.prepareStatement("""
                    SELECT action_key,action_type,severity,title_snapshot,description_snapshot,
                           value_snapshot,action_target,status,first_seen_at,last_seen_at,
                           acknowledged_at,acknowledged_by,acknowledgement_note,resolved_at
                      FROM operational_action_states
                     WHERE action_key=?
                    """)){
            statement.setString(1,key);
            try(ResultSet rs=statement.executeQuery()){
                if(!rs.next()) throw business("Ação não encontrada");
                return mapGovernedAction(rs);
            }
        }
    }

    private GovernedAction mapGovernedAction(ResultSet rs) throws SQLException {
        LocalDateTime first=rs.getTimestamp("first_seen_at").toLocalDateTime();
        LocalDateTime last=rs.getTimestamp("last_seen_at").toLocalDateTime();
        Timestamp acknowledgedTs=rs.getTimestamp("acknowledged_at");
        LocalDateTime acknowledged=acknowledgedTs==null?null:acknowledgedTs.toLocalDateTime();
        long sla=slaHours(rs.getString("severity"));
        LocalDateTime end=acknowledged==null?LocalDateTime.now():acknowledged;
        long age=Math.max(0,Duration.between(first,end).toHours());
        boolean breached=age>sla;
        return new GovernedAction(
                rs.getString("action_key"),
                rs.getString("action_type"),
                rs.getString("severity"),
                rs.getString("title_snapshot"),
                rs.getString("description_snapshot"),
                rs.getString("value_snapshot"),
                rs.getString("action_target"),
                rs.getString("status"),
                first,last,acknowledged,
                rs.getString("acknowledged_by"),
                rs.getString("acknowledgement_note"),
                age,sla,breached
        );
    }

    private long slaHours(String severity){
        return switch(severity){
            case "CRITICAL"->4L;
            case "WARNING"->24L;
            default->72L;
        };
    }

    private void bindAction(PreparedStatement statement,DailyActionItem item) throws SQLException {
        statement.setString(1,item.key());
        statement.setString(2,item.type());
        statement.setString(3,item.severity());
        statement.setString(4,item.title());
        statement.setString(5,item.description());
        statement.setString(6,item.value());
        statement.setString(7,item.actionTarget());
    }

    private ReplenishmentPolicy mapPolicy(ResultSet rs) throws SQLException {
        Timestamp updated=rs.getTimestamp("updated_at");
        Long supplierId=(Long)rs.getObject("preferred_supplier_id");
        return new ReplenishmentPolicy(
                rs.getLong("product_id"),rs.getString("sku"),rs.getString("product_name"),
                rs.getBoolean("enabled"),rs.getInt("target_coverage_days"),
                rs.getBigDecimal("safety_stock_multiplier"),rs.getBigDecimal("minimum_order_quantity"),
                rs.getBigDecimal("order_multiple"),supplierId,rs.getString("preferred_supplier_name"),
                rs.getString("updated_by"),updated==null?null:updated.toLocalDateTime()
        );
    }

    private OperationalException mapException(ResultSet rs) throws SQLException {
        Timestamp starts=rs.getTimestamp("starts_at");
        Timestamp expires=rs.getTimestamp("expires_at");
        Timestamp cancelled=rs.getTimestamp("cancelled_at");
        Timestamp created=rs.getTimestamp("created_at");
        return new OperationalException(
                rs.getLong("id"),rs.getLong("product_id"),rs.getString("sku"),
                rs.getString("product_name"),rs.getString("exception_type"),rs.getString("reason"),
                rs.getString("status"),starts==null?null:starts.toLocalDateTime(),
                expires==null?null:expires.toLocalDateTime(),rs.getString("created_by"),
                rs.getString("cancelled_by"),cancelled==null?null:cancelled.toLocalDateTime(),
                created==null?null:created.toLocalDateTime()
        );
    }

    private OperationalException exceptionById(long id) throws SQLException {
        try(Connection connection=dataSource.getConnection();
            PreparedStatement statement=connection.prepareStatement("""
                    SELECT e.id,e.product_id,p.sku,p.name AS product_name,
                           e.exception_type,e.reason,e.status,e.starts_at,e.expires_at,
                           e.created_by,e.cancelled_by,e.cancelled_at,e.created_at
                      FROM operational_exceptions e
                      JOIN products p ON p.id=e.product_id
                     WHERE e.id=?
                    """)){
            statement.setLong(1,id);
            try(ResultSet rs=statement.executeQuery()){
                if(!rs.next()) throw business("Exceção não encontrada");
                return mapException(rs);
            }
        }
    }

    private void expireExceptions(Connection connection) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("""
                UPDATE operational_exceptions
                   SET status='EXPIRED'
                 WHERE status='ACTIVE'
                   AND expires_at<=NOW()
                """)){
            statement.executeUpdate();
        }
    }

    private void ensureProduct(Connection connection,long productId) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("""
                SELECT id FROM products WHERE id=? AND active=TRUE
                """)){
            statement.setLong(1,productId);
            try(ResultSet rs=statement.executeQuery()){
                if(!rs.next()) throw business("Produto inexistente ou inativo");
            }
        }
    }

    private void ensureSupplier(Connection connection,long supplierId) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("""
                SELECT id FROM suppliers WHERE id=? AND active=TRUE
                """)){
            statement.setLong(1,supplierId);
            try(ResultSet rs=statement.executeQuery()){
                if(!rs.next()) throw business("Fornecedor preferencial inexistente ou inativo");
            }
        }
    }

    private int priorityRank(String priority){
        return switch(priority){
            case "CRITICAL"->0;
            case "HIGH"->1;
            case "ATTENTION"->2;
            case "LOW"->3;
            default->4;
        };
    }

    private String normalizeText(String value){
        if(value==null) return null;
        String normalized=value.trim();
        return normalized.isEmpty()?null:normalized;
    }

    private void setNullableText(PreparedStatement statement,int index,String value) throws SQLException {
        if(value==null) statement.setNull(index,Types.VARCHAR);
        else statement.setString(index,value);
    }

    private String safeActor(String actor){
        return actor==null||actor.isBlank()?"system":actor;
    }

    private SQLException business(String message){
        return new SQLException(message,"45000");
    }

    private record CycleBase(
            long productId,
            String sku,
            String productName,
            String category,
            BigDecimal stockValue,
            LocalDateTime lastCountedAt,
            BigDecimal lastDifference,
            boolean paused
    ){}
}
