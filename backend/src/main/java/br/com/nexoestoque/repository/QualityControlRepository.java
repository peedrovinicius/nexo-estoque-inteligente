package br.com.nexoestoque.repository;

import br.com.nexoestoque.dto.LotRecallRequest;
import br.com.nexoestoque.dto.ReceiptVarianceRequest;
import br.com.nexoestoque.model.QualityControl.*;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class QualityControlRepository {
    private static final Set<String> VARIANCE_TYPES =
            Set.of("SHORT","EXCESS","DAMAGED","REJECTED","OTHER");

    private final DataSource dataSource;

    public QualityControlRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<BatchQualityState> heldBatches() throws SQLException {
        List<BatchQualityState> items = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT b.id AS batch_id, b.product_id, p.sku, p.name AS product_name,
                            b.lot_code, w.name AS warehouse_name, l.code AS location_code,
                            b.quantity, b.quality_status, b.quality_reason,
                            b.quality_updated_by, b.quality_updated_at
                       FROM stock_batches b
                       JOIN products p ON p.id=b.product_id
                       JOIN stock_locations l ON l.id=b.location_id
                       JOIN warehouses w ON w.id=l.warehouse_id
                      WHERE b.quality_status<>'AVAILABLE'
                      ORDER BY
                        CASE b.quality_status WHEN 'BLOCKED' THEN 0 ELSE 1 END,
                        b.quality_updated_at DESC, b.id DESC
                     """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) items.add(mapBatchState(rs));
        }
        return items;
    }

    public BatchQualityState quarantineBatch(long batchId, String reason, String actor) throws SQLException {
        return changeBatchStatus(batchId,"QUARANTINED",reason,actor);
    }

    public BatchQualityState releaseBatch(long batchId, String reason, String actor) throws SQLException {
        try (Connection connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BatchQualityState current=batchStateById(connection,batchId,true);
                if(current==null) throw business("Lote não encontrado");
                if("BLOCKED".equals(current.qualityStatus())) {
                    throw business("Lote bloqueado por recall não pode ser liberado manualmente");
                }
                if("AVAILABLE".equals(current.qualityStatus())) {
                    connection.commit();
                    return current;
                }

                recordQualityEvent(connection,batchId,current.qualityStatus(),"AVAILABLE",reason,actor);
                try(PreparedStatement statement=connection.prepareStatement("""
                        UPDATE stock_batches
                           SET quality_status='AVAILABLE',
                               quality_reason=?,
                               quality_updated_by=?,
                               quality_updated_at=CURRENT_TIMESTAMP
                         WHERE id=?
                        """)){
                    statement.setString(1,"Liberado: "+normalizeReason(reason));
                    statement.setString(2,safeActor(actor));
                    statement.setLong(3,batchId);
                    statement.executeUpdate();
                }
                connection.commit();
                return batchStateById(connection,batchId,false);
            } catch(SQLException|RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private BatchQualityState changeBatchStatus(
            long batchId,
            String targetStatus,
            String reason,
            String actor
    ) throws SQLException {
        try(Connection connection=dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BatchQualityState current=batchStateById(connection,batchId,true);
                if(current==null) throw business("Lote não encontrado");
                if("BLOCKED".equals(current.qualityStatus())) {
                    throw business("Lote bloqueado por recall não pode ter status alterado manualmente");
                }
                if(targetStatus.equals(current.qualityStatus())) {
                    connection.commit();
                    return current;
                }

                recordQualityEvent(connection,batchId,current.qualityStatus(),targetStatus,reason,actor);
                try(PreparedStatement statement=connection.prepareStatement("""
                        UPDATE stock_batches
                           SET quality_status=?,
                               quality_reason=?,
                               quality_updated_by=?,
                               quality_updated_at=CURRENT_TIMESTAMP
                         WHERE id=?
                        """)){
                    statement.setString(1,targetStatus);
                    statement.setString(2,normalizeReason(reason));
                    statement.setString(3,safeActor(actor));
                    statement.setLong(4,batchId);
                    statement.executeUpdate();
                }

                connection.commit();
                return batchStateById(connection,batchId,false);
            } catch(SQLException|RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<LotRecall> recalls(String status) throws SQLException {
        String normalized=status==null||status.isBlank()?null:status.trim().toUpperCase();
        if(normalized!=null&&!Set.of("OPEN","CLOSED").contains(normalized)) {
            throw business("Status de recall inválido");
        }

        List<LotRecall> items=new ArrayList<>();
        try(Connection connection=dataSource.getConnection();
            PreparedStatement statement=connection.prepareStatement("""
                    SELECT lr.id,lr.product_id,p.sku,p.name AS product_name,lr.lot_code,
                           lr.reason,lr.status,lr.created_by,lr.created_at,
                           lr.closed_by,lr.closed_at,lr.resolution,
                           COALESCE(SUM(b.quantity),0) AS current_quantity,
                           COUNT(lrb.batch_id) AS affected_batch_count
                      FROM lot_recalls lr
                      JOIN products p ON p.id=lr.product_id
                      LEFT JOIN lot_recall_batches lrb ON lrb.recall_id=lr.id
                      LEFT JOIN stock_batches b ON b.id=lrb.batch_id
                     WHERE (? IS NULL OR lr.status=?)
                     GROUP BY lr.id,lr.product_id,p.sku,p.name,lr.lot_code,lr.reason,
                              lr.status,lr.created_by,lr.created_at,lr.closed_by,
                              lr.closed_at,lr.resolution
                     ORDER BY
                       CASE lr.status WHEN 'OPEN' THEN 0 ELSE 1 END,
                       lr.created_at DESC,lr.id DESC
                    """)){
            setNullableText(statement,1,normalized);
            setNullableText(statement,2,normalized);
            try(ResultSet rs=statement.executeQuery()){
                while(rs.next()) items.add(mapRecall(rs));
            }
        }
        return items;
    }

    public LotRecall createRecall(LotRecallRequest request,String actor) throws SQLException {
        String lotCode=request.lotCode().trim();
        String reason=normalizeReason(request.reason());

        try(Connection connection=dataSource.getConnection()){
            connection.setAutoCommit(false);
            try{
                try(PreparedStatement statement=connection.prepareStatement("""
                        SELECT id FROM products WHERE id=? FOR SHARE
                        """)){
                    statement.setLong(1,request.productId());
                    try(ResultSet rs=statement.executeQuery()){
                        if(!rs.next()) throw business("Produto não encontrado");
                    }
                }

                try(PreparedStatement statement=connection.prepareStatement("""
                        SELECT id
                          FROM lot_recalls
                         WHERE product_id=? AND lot_code=? AND status='OPEN'
                         LIMIT 1
                         FOR UPDATE
                        """)){
                    statement.setLong(1,request.productId());
                    statement.setString(2,lotCode);
                    try(ResultSet rs=statement.executeQuery()){
                        if(rs.next()) throw business("Já existe recall aberto para este produto e lote");
                    }
                }

                List<RecallBatchSnapshot> batches=new ArrayList<>();
                try(PreparedStatement statement=connection.prepareStatement("""
                        SELECT id,quality_status,quality_reason,quantity
                          FROM stock_batches
                         WHERE product_id=? AND lot_code=?
                         ORDER BY id
                         FOR UPDATE
                        """)){
                    statement.setLong(1,request.productId());
                    statement.setString(2,lotCode);
                    try(ResultSet rs=statement.executeQuery()){
                        while(rs.next()){
                            batches.add(new RecallBatchSnapshot(
                                    rs.getLong("id"),
                                    rs.getString("quality_status"),
                                    rs.getString("quality_reason"),
                                    rs.getBigDecimal("quantity")
                            ));
                        }
                    }
                }
                if(batches.isEmpty()) throw business("Lote não encontrado para o produto informado");

                long recallId;
                try(PreparedStatement statement=connection.prepareStatement("""
                        INSERT INTO lot_recalls(product_id,lot_code,reason,created_by)
                        VALUES(?,?,?,?)
                        """,Statement.RETURN_GENERATED_KEYS)){
                    statement.setLong(1,request.productId());
                    statement.setString(2,lotCode);
                    statement.setString(3,reason);
                    statement.setString(4,safeActor(actor));
                    statement.executeUpdate();
                    try(ResultSet keys=statement.getGeneratedKeys()){
                        if(!keys.next()) throw new SQLException("Recall criado sem identificador");
                        recallId=keys.getLong(1);
                    }
                }

                for(RecallBatchSnapshot batch:batches){
                    try(PreparedStatement statement=connection.prepareStatement("""
                            INSERT INTO lot_recall_batches(
                                recall_id,batch_id,previous_quality_status,
                                previous_quality_reason,quantity_snapshot
                            ) VALUES(?,?,?,?,?)
                            """)){
                        statement.setLong(1,recallId);
                        statement.setLong(2,batch.batchId());
                        statement.setString(3,batch.status());
                        setNullableText(statement,4,batch.reason());
                        statement.setBigDecimal(5,batch.quantity());
                        statement.executeUpdate();
                    }

                    recordQualityEvent(
                            connection,batch.batchId(),batch.status(),"BLOCKED",
                            "Recall #"+recallId+": "+reason,actor
                    );

                    try(PreparedStatement statement=connection.prepareStatement("""
                            UPDATE stock_batches
                               SET quality_status='BLOCKED',
                                   quality_reason=?,
                                   quality_updated_by=?,
                                   quality_updated_at=CURRENT_TIMESTAMP
                             WHERE id=?
                            """)){
                        statement.setString(1,"Recall #"+recallId+": "+reason);
                        statement.setString(2,safeActor(actor));
                        statement.setLong(3,batch.batchId());
                        statement.executeUpdate();
                    }
                }

                connection.commit();
                return recallById(connection,recallId);
            } catch(SQLException|RuntimeException exception){
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public LotRecall closeRecall(long recallId,String resolution,String actor) throws SQLException {
        try(Connection connection=dataSource.getConnection()){
            connection.setAutoCommit(false);
            try{
                String status;
                try(PreparedStatement statement=connection.prepareStatement("""
                        SELECT status FROM lot_recalls WHERE id=? FOR UPDATE
                        """)){
                    statement.setLong(1,recallId);
                    try(ResultSet rs=statement.executeQuery()){
                        if(!rs.next()) throw business("Recall não encontrado");
                        status=rs.getString("status");
                    }
                }
                if("CLOSED".equals(status)){
                    connection.commit();
                    return recallById(connection,recallId);
                }

                List<RestoreBatchSnapshot> batches=new ArrayList<>();
                try(PreparedStatement statement=connection.prepareStatement("""
                        SELECT lrb.batch_id,lrb.previous_quality_status,
                               lrb.previous_quality_reason,b.quality_status
                          FROM lot_recall_batches lrb
                          JOIN stock_batches b ON b.id=lrb.batch_id
                         WHERE lrb.recall_id=?
                         ORDER BY lrb.batch_id
                         FOR UPDATE
                        """)){
                    statement.setLong(1,recallId);
                    try(ResultSet rs=statement.executeQuery()){
                        while(rs.next()){
                            batches.add(new RestoreBatchSnapshot(
                                    rs.getLong("batch_id"),
                                    rs.getString("previous_quality_status"),
                                    rs.getString("previous_quality_reason"),
                                    rs.getString("quality_status")
                            ));
                        }
                    }
                }

                for(RestoreBatchSnapshot batch:batches){
                    if(!"BLOCKED".equals(batch.currentStatus())) continue;
                    recordQualityEvent(
                            connection,batch.batchId(),"BLOCKED",batch.previousStatus(),
                            "Encerramento do recall #"+recallId+": "+normalizeReason(resolution),actor
                    );
                    try(PreparedStatement statement=connection.prepareStatement("""
                            UPDATE stock_batches
                               SET quality_status=?,
                                   quality_reason=?,
                                   quality_updated_by=?,
                                   quality_updated_at=CURRENT_TIMESTAMP
                             WHERE id=?
                            """)){
                        statement.setString(1,batch.previousStatus());
                        setNullableText(statement,2,batch.previousReason());
                        statement.setString(3,safeActor(actor));
                        statement.setLong(4,batch.batchId());
                        statement.executeUpdate();
                    }
                }

                try(PreparedStatement statement=connection.prepareStatement("""
                        UPDATE lot_recalls
                           SET status='CLOSED',
                               closed_by=?,
                               closed_at=CURRENT_TIMESTAMP,
                               resolution=?
                         WHERE id=?
                        """)){
                    statement.setString(1,safeActor(actor));
                    statement.setString(2,normalizeReason(resolution));
                    statement.setLong(3,recallId);
                    statement.executeUpdate();
                }

                connection.commit();
                return recallById(connection,recallId);
            } catch(SQLException|RuntimeException exception){
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public RecallImpact recallImpact(long recallId) throws SQLException {
        try(Connection connection=dataSource.getConnection()){
            LotRecall recall=recallById(connection,recallId);
            if(recall==null) throw business("Recall não encontrado");

            List<RecallBatchLocation> batches=new ArrayList<>();
            Set<String> locations=new HashSet<>();
            try(PreparedStatement statement=connection.prepareStatement("""
                    SELECT b.id AS batch_id,w.name AS warehouse_name,l.code AS location_code,
                           lrb.quantity_snapshot,b.quantity AS current_quantity,b.quality_status
                      FROM lot_recall_batches lrb
                      JOIN stock_batches b ON b.id=lrb.batch_id
                      JOIN stock_locations l ON l.id=b.location_id
                      JOIN warehouses w ON w.id=l.warehouse_id
                     WHERE lrb.recall_id=?
                     ORDER BY w.name,l.code,b.id
                    """)){
                statement.setLong(1,recallId);
                try(ResultSet rs=statement.executeQuery()){
                    while(rs.next()){
                        locations.add(rs.getString("warehouse_name")+"|"+rs.getString("location_code"));
                        batches.add(new RecallBatchLocation(
                                rs.getLong("batch_id"),
                                rs.getString("warehouse_name"),
                                rs.getString("location_code"),
                                rs.getBigDecimal("quantity_snapshot"),
                                rs.getBigDecimal("current_quantity"),
                                rs.getString("quality_status")
                        ));
                    }
                }
            }

            BigDecimal receiptQuantity=BigDecimal.ZERO;
            LocalDateTime firstReceiptAt=null;
            try(PreparedStatement statement=connection.prepareStatement("""
                    SELECT COALESCE(SUM(pri.quantity),0) AS receipt_quantity,
                           MIN(pr.created_at) AS first_receipt_at
                      FROM purchase_receipt_items pri
                      JOIN purchase_receipts pr ON pr.id=pri.purchase_receipt_id
                      JOIN purchase_order_items poi ON poi.id=pri.purchase_order_item_id
                     WHERE poi.product_id=? AND pri.lot_code=?
                    """)){
                statement.setLong(1,recall.productId());
                statement.setString(2,recall.lotCode());
                try(ResultSet rs=statement.executeQuery()){
                    rs.next();
                    receiptQuantity=rs.getBigDecimal("receipt_quantity");
                    Timestamp first=rs.getTimestamp("first_receipt_at");
                    firstReceiptAt=first==null?null:first.toLocalDateTime();
                }
            }

            BigDecimal exitedQuantity=BigDecimal.ZERO;
            LocalDateTime lastExitAt=null;
            try(PreparedStatement statement=connection.prepareStatement("""
                    SELECT COALESCE(SUM(quantity),0) AS exited_quantity,
                           MAX(created_at) AS last_exit_at
                      FROM (
                          SELECT a.quantity,sm.created_at
                            FROM stock_movement_allocations a
                            JOIN stock_movements sm ON sm.id=a.movement_id
                            JOIN stock_batches b ON b.id=a.batch_id
                           WHERE sm.product_id=? AND sm.movement_type='EXIT' AND b.lot_code=?
                          UNION ALL
                          SELECT aa.quantity,sma.created_at
                            FROM stock_movement_allocations_archive aa
                            JOIN stock_movements_archive sma
                              ON sma.original_movement_id=aa.original_movement_id
                           WHERE sma.product_id=? AND sma.movement_type='EXIT' AND aa.lot_code=?
                      ) exits
                    """)){
                statement.setLong(1,recall.productId());
                statement.setString(2,recall.lotCode());
                statement.setLong(3,recall.productId());
                statement.setString(4,recall.lotCode());
                try(ResultSet rs=statement.executeQuery()){
                    rs.next();
                    exitedQuantity=rs.getBigDecimal("exited_quantity");
                    Timestamp last=rs.getTimestamp("last_exit_at");
                    lastExitAt=last==null?null:last.toLocalDateTime();
                }
            }

            return new RecallImpact(
                    recall,
                    recall.currentQuantity(),
                    receiptQuantity,
                    exitedQuantity,
                    locations.size(),
                    firstReceiptAt,
                    lastExitAt,
                    batches
            );
        }
    }

    public ReceiptVariance createVariance(ReceiptVarianceRequest request,String actor) throws SQLException {
        String type=request.varianceType().trim().toUpperCase();
        if(!VARIANCE_TYPES.contains(type)) throw business("Tipo de divergência inválido");

        try(Connection connection=dataSource.getConnection()){
            connection.setAutoCommit(false);
            try{
                try(PreparedStatement statement=connection.prepareStatement("""
                        SELECT id
                          FROM purchase_order_items
                         WHERE id=? AND purchase_order_id=?
                         FOR SHARE
                        """)){
                    statement.setLong(1,request.purchaseOrderItemId());
                    statement.setLong(2,request.purchaseOrderId());
                    try(ResultSet rs=statement.executeQuery()){
                        if(!rs.next()) throw business("Item não pertence ao pedido informado");
                    }
                }

                if(request.purchaseReceiptId()!=null){
                    try(PreparedStatement statement=connection.prepareStatement("""
                            SELECT pr.id
                              FROM purchase_receipts pr
                              JOIN purchase_receipt_items pri ON pri.purchase_receipt_id=pr.id
                             WHERE pr.id=?
                               AND pr.purchase_order_id=?
                               AND pri.purchase_order_item_id=?
                             LIMIT 1
                            """)){
                        statement.setLong(1,request.purchaseReceiptId());
                        statement.setLong(2,request.purchaseOrderId());
                        statement.setLong(3,request.purchaseOrderItemId());
                        try(ResultSet rs=statement.executeQuery()){
                            if(!rs.next()) throw business("Recebimento não corresponde ao pedido e item informados");
                        }
                    }
                }

                long id;
                try(PreparedStatement statement=connection.prepareStatement("""
                        INSERT INTO purchase_receipt_variances(
                            purchase_order_id,purchase_order_item_id,purchase_receipt_id,
                            variance_type,quantity,reason,reported_by
                        ) VALUES(?,?,?,?,?,?,?)
                        """,Statement.RETURN_GENERATED_KEYS)){
                    statement.setLong(1,request.purchaseOrderId());
                    statement.setLong(2,request.purchaseOrderItemId());
                    if(request.purchaseReceiptId()==null) statement.setNull(3,Types.BIGINT);
                    else statement.setLong(3,request.purchaseReceiptId());
                    statement.setString(4,type);
                    statement.setBigDecimal(5,request.quantity());
                    statement.setString(6,normalizeReason(request.reason()));
                    statement.setString(7,safeActor(actor));
                    statement.executeUpdate();
                    try(ResultSet keys=statement.getGeneratedKeys()){
                        if(!keys.next()) throw new SQLException("Divergência criada sem identificador");
                        id=keys.getLong(1);
                    }
                }

                connection.commit();
                return varianceById(connection,id);
            } catch(SQLException|RuntimeException exception){
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<ReceiptVariance> variances(int limit) throws SQLException {
        int safeLimit=limit<=0?200:Math.min(limit,1000);
        List<ReceiptVariance> items=new ArrayList<>();
        try(Connection connection=dataSource.getConnection();
            PreparedStatement statement=connection.prepareStatement("""
                    SELECT prv.id,prv.purchase_order_id,prv.purchase_order_item_id,
                           prv.purchase_receipt_id,poi.product_id,p.sku,p.name AS product_name,
                           s.name AS supplier_name,prv.variance_type,prv.quantity,
                           prv.reason,prv.reported_by,prv.created_at
                      FROM purchase_receipt_variances prv
                      JOIN purchase_order_items poi ON poi.id=prv.purchase_order_item_id
                      JOIN purchase_orders po ON po.id=prv.purchase_order_id
                      JOIN suppliers s ON s.id=po.supplier_id
                      JOIN products p ON p.id=poi.product_id
                     ORDER BY prv.created_at DESC,prv.id DESC
                     LIMIT ?
                    """)){
            statement.setInt(1,safeLimit);
            try(ResultSet rs=statement.executeQuery()){
                while(rs.next()) items.add(mapVariance(rs));
            }
        }
        return items;
    }

    public QualitySummary summary() throws SQLException {
        try(Connection connection=dataSource.getConnection()){
            int quarantined=scalarInt(connection,
                    "SELECT COUNT(*) FROM stock_batches WHERE quality_status='QUARANTINED' AND quantity>0");
            int blocked=scalarInt(connection,
                    "SELECT COUNT(*) FROM stock_batches WHERE quality_status='BLOCKED' AND quantity>0");
            int openRecalls=scalarInt(connection,
                    "SELECT COUNT(*) FROM lot_recalls WHERE status='OPEN'");
            int variances=scalarInt(connection,
                    "SELECT COUNT(*) FROM purchase_receipt_variances WHERE created_at>=DATE_SUB(NOW(),INTERVAL 30 DAY)");
            BigDecimal held=scalarDecimal(connection,
                    "SELECT COALESCE(SUM(quantity),0) FROM stock_batches WHERE quality_status<>'AVAILABLE' AND quantity>0");
            return new QualitySummary(quarantined,blocked,openRecalls,variances,held);
        }
    }

    private LotRecall recallById(Connection connection,long recallId) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("""
                SELECT lr.id,lr.product_id,p.sku,p.name AS product_name,lr.lot_code,
                       lr.reason,lr.status,lr.created_by,lr.created_at,
                       lr.closed_by,lr.closed_at,lr.resolution,
                       COALESCE(SUM(b.quantity),0) AS current_quantity,
                       COUNT(lrb.batch_id) AS affected_batch_count
                  FROM lot_recalls lr
                  JOIN products p ON p.id=lr.product_id
                  LEFT JOIN lot_recall_batches lrb ON lrb.recall_id=lr.id
                  LEFT JOIN stock_batches b ON b.id=lrb.batch_id
                 WHERE lr.id=?
                 GROUP BY lr.id,lr.product_id,p.sku,p.name,lr.lot_code,lr.reason,
                          lr.status,lr.created_by,lr.created_at,lr.closed_by,
                          lr.closed_at,lr.resolution
                """)){
            statement.setLong(1,recallId);
            try(ResultSet rs=statement.executeQuery()){
                return rs.next()?mapRecall(rs):null;
            }
        }
    }

    private BatchQualityState batchStateById(Connection connection,long batchId,boolean lock) throws SQLException {
        String sql="""
                SELECT b.id AS batch_id,b.product_id,p.sku,p.name AS product_name,
                       b.lot_code,w.name AS warehouse_name,l.code AS location_code,
                       b.quantity,b.quality_status,b.quality_reason,
                       b.quality_updated_by,b.quality_updated_at
                  FROM stock_batches b
                  JOIN products p ON p.id=b.product_id
                  JOIN stock_locations l ON l.id=b.location_id
                  JOIN warehouses w ON w.id=l.warehouse_id
                 WHERE b.id=?
                """+(lock?" FOR UPDATE":"");
        try(PreparedStatement statement=connection.prepareStatement(sql)){
            statement.setLong(1,batchId);
            try(ResultSet rs=statement.executeQuery()){
                return rs.next()?mapBatchState(rs):null;
            }
        }
    }

    private ReceiptVariance varianceById(Connection connection,long id) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("""
                SELECT prv.id,prv.purchase_order_id,prv.purchase_order_item_id,
                       prv.purchase_receipt_id,poi.product_id,p.sku,p.name AS product_name,
                       s.name AS supplier_name,prv.variance_type,prv.quantity,
                       prv.reason,prv.reported_by,prv.created_at
                  FROM purchase_receipt_variances prv
                  JOIN purchase_order_items poi ON poi.id=prv.purchase_order_item_id
                  JOIN purchase_orders po ON po.id=prv.purchase_order_id
                  JOIN suppliers s ON s.id=po.supplier_id
                  JOIN products p ON p.id=poi.product_id
                 WHERE prv.id=?
                """)){
            statement.setLong(1,id);
            try(ResultSet rs=statement.executeQuery()){
                return rs.next()?mapVariance(rs):null;
            }
        }
    }

    private void recordQualityEvent(
            Connection connection,long batchId,String fromStatus,String toStatus,
            String reason,String actor
    ) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement("""
                INSERT INTO batch_quality_events(
                    batch_id,from_status,to_status,reason,actor_username
                ) VALUES(?,?,?,?,?)
                """)){
            statement.setLong(1,batchId);
            statement.setString(2,fromStatus);
            statement.setString(3,toStatus);
            statement.setString(4,normalizeReason(reason));
            statement.setString(5,safeActor(actor));
            statement.executeUpdate();
        }
    }

    private BatchQualityState mapBatchState(ResultSet rs) throws SQLException {
        Timestamp updated=rs.getTimestamp("quality_updated_at");
        return new BatchQualityState(
                rs.getLong("batch_id"),
                rs.getLong("product_id"),
                rs.getString("sku"),
                rs.getString("product_name"),
                rs.getString("lot_code"),
                rs.getString("warehouse_name"),
                rs.getString("location_code"),
                rs.getBigDecimal("quantity"),
                rs.getString("quality_status"),
                rs.getString("quality_reason"),
                rs.getString("quality_updated_by"),
                updated==null?null:updated.toLocalDateTime()
        );
    }

    private LotRecall mapRecall(ResultSet rs) throws SQLException {
        Timestamp created=rs.getTimestamp("created_at");
        Timestamp closed=rs.getTimestamp("closed_at");
        return new LotRecall(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getString("sku"),
                rs.getString("product_name"),
                rs.getString("lot_code"),
                rs.getString("reason"),
                rs.getString("status"),
                rs.getString("created_by"),
                created==null?null:created.toLocalDateTime(),
                rs.getString("closed_by"),
                closed==null?null:closed.toLocalDateTime(),
                rs.getString("resolution"),
                rs.getBigDecimal("current_quantity"),
                rs.getInt("affected_batch_count")
        );
    }

    private ReceiptVariance mapVariance(ResultSet rs) throws SQLException {
        Timestamp created=rs.getTimestamp("created_at");
        Long receiptId=(Long)rs.getObject("purchase_receipt_id");
        return new ReceiptVariance(
                rs.getLong("id"),
                rs.getLong("purchase_order_id"),
                rs.getLong("purchase_order_item_id"),
                receiptId,
                rs.getLong("product_id"),
                rs.getString("sku"),
                rs.getString("product_name"),
                rs.getString("supplier_name"),
                rs.getString("variance_type"),
                rs.getBigDecimal("quantity"),
                rs.getString("reason"),
                rs.getString("reported_by"),
                created==null?null:created.toLocalDateTime()
        );
    }

    private int scalarInt(Connection connection,String sql) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement(sql);
            ResultSet rs=statement.executeQuery()){
            rs.next();
            return rs.getInt(1);
        }
    }

    private BigDecimal scalarDecimal(Connection connection,String sql) throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement(sql);
            ResultSet rs=statement.executeQuery()){
            rs.next();
            return rs.getBigDecimal(1);
        }
    }

    private void setNullableText(PreparedStatement statement,int index,String value) throws SQLException {
        if(value==null) statement.setNull(index,Types.VARCHAR);
        else statement.setString(index,value);
    }

    private String normalizeReason(String reason){
        return reason==null?"":reason.trim();
    }

    private String safeActor(String actor){
        return actor==null||actor.isBlank()?"system":actor;
    }

    private SQLException business(String message){
        return new SQLException(message,"45000");
    }

    private record RecallBatchSnapshot(
            long batchId,String status,String reason,BigDecimal quantity
    ){}

    private record RestoreBatchSnapshot(
            long batchId,String previousStatus,String previousReason,String currentStatus
    ){}
}
