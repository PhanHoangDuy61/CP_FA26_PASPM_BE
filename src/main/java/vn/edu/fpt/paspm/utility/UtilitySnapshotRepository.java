package vn.edu.fpt.paspm.utility;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.*;

@Repository
public class UtilitySnapshotRepository {
    private final JdbcTemplate jdbc;
    private final UtilitySettingsRepository json;
    public UtilitySnapshotRepository(JdbcTemplate jdbc,UtilitySettingsRepository json) { this.jdbc=jdbc;this.json=json; }
    @Transactional
    public int create(int store,int actor,RangeRequest range,FrozenConfiguration configuration,MatrixResult matrix) {
        var key=new GeneratedKeyHolder();
        jdbc.update(connection->{
            var ps=connection.prepareStatement("INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status,validation_warnings) VALUES (?,?,?,?,?,?,?,?,?,6,'READY',?)",new String[]{"snapshot_id"});
            ps.setInt(1,store);ps.setInt(2,actor);ps.setDate(3,Date.valueOf(range.rangeStart()));ps.setDate(4,Date.valueOf(range.rangeEnd()));ps.setDate(5,Date.valueOf(range.asOfDate()));
            ps.setString(6,json.write(configuration));ps.setString(7,matrix.sourceHash());ps.setString(8,matrix.matrixHash());ps.setBigDecimal(9,matrix.totalWeightedUtility());ps.setString(10,json.write(matrix.warnings()));return ps;
        },key);
        int id=key.getKey().intValue();
        jdbc.update("INSERT INTO utility_snapshot_storage(snapshot_id,store_id,storage_kind,source_namespace) VALUES(?,?,'F04_LIVE',?)",id,store,configuration.source());
        return id;
    }
    @Transactional
    public void saveItems(int snapshot,List<MatrixItem> items) {
        int store=owner(snapshot);
        if(route(store,snapshot,false).legacy())throw invalid();
        jdbc.update("INSERT INTO utility_snapshot_storage(snapshot_id,store_id,storage_kind,source_namespace) SELECT snapshot_id,store_id,'F04_LIVE','F04_DATABASE_V1' FROM utility_snapshot WHERE snapshot_id=? AND NOT EXISTS (SELECT 1 FROM utility_snapshot_storage WHERE snapshot_id=?)",snapshot,snapshot);
        jdbc.batchUpdate("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,quantity,base_utility,stock_score,expiry_score,seasonal_score,expiry_status,weight_value,weighted_utility,expiry_evidence) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            items,500,(ps,item)-> { ps.setInt(1,snapshot);ps.setInt(2,item.transactionId());ps.setInt(3,item.productId());ps.setInt(4,item.quantity());ps.setBigDecimal(5,item.baseUtility());ps.setBigDecimal(6,item.stockScore());ps.setBigDecimal(7,item.expiryScore());ps.setBigDecimal(8,item.seasonalScore());ps.setString(9,item.expiryStatus());ps.setBigDecimal(10,item.weightValue());ps.setBigDecimal(11,item.weightedUtility());ps.setString(12,json.write(item.expiryEvidence())); });
    }
    public SnapshotView find(int store,int id) {
        return jdbc.query("SELECT * FROM utility_snapshot WHERE store_id=? AND snapshot_id=?",(rs,row)->new SnapshotView(rs.getInt("snapshot_id"),rs.getInt("store_id"),rs.getInt("created_by"),rs.getObject("range_start",LocalDate.class),rs.getObject("range_end",LocalDate.class),rs.getObject("as_of_date",LocalDate.class),json.read(rs.getString("config_snapshot"),FrozenConfiguration.class),rs.getString("source_hash"),rs.getString("matrix_hash"),rs.getBigDecimal("total_weighted_utility"),rs.getInt("rounding_scale"),rs.getString("status"),rs.getTimestamp("created_at").toInstant(),warnings(rs.getString("validation_warnings"))),store,id).stream().findFirst().orElse(null);
    }
    private List<String> warnings(String value) { return value==null?List.of():List.of(json.read(value,String[].class)); }
    private record Route(String table,boolean legacy) {}
    private ApiException invalid(){return new ApiException(409,"Frozen matrix storage is inconsistent or unavailable");}
    private int owner(int id){
        var owners=jdbc.queryForList("SELECT store_id FROM utility_snapshot WHERE snapshot_id=?",Integer.class,id);
        if(owners.size()!=1)throw new ApiException(404,"Utility snapshot not found");return owners.get(0);
    }
    private Route route(int store,int id,boolean complete){
        if(owner(id)!=store)throw new ApiException(404,"Utility snapshot not found");
        var routes=jdbc.query("SELECT store_id,storage_kind FROM utility_snapshot_storage WHERE snapshot_id=?",(r,n)->Map.entry(r.getInt(1),r.getString(2)),id);
        if(routes.size()>1)throw invalid();
        boolean legacy=false;
        if(!routes.isEmpty()) {
            var entry=routes.get(0);if(entry.getKey()!=store)throw invalid();
            legacy="LEGACY_V5_ARCHIVE".equals(entry.getValue());
            if(!legacy&&!"F04_LIVE".equals(entry.getValue()))throw invalid();
        }
        // Missing registry entries are supported only for demonstrable pre-registry F04 snapshots.
        if(routes.isEmpty()) {
            try {
                var frozen=json.read(jdbc.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,id),FrozenConfiguration.class);
                if(frozen==null||!"F04_DATABASE_V1".equals(frozen.source()))throw invalid();
            } catch(RuntimeException ex) {throw invalid();}
        }
        String table=legacy?"utility_matrix_item_legacy_v5":"utility_matrix_item";
        String other=legacy?"utility_matrix_item":"utility_matrix_item_legacy_v5";
        if(jdbc.queryForObject("SELECT COUNT(*) FROM "+other+" WHERE snapshot_id=?",Long.class,id)!=0)throw invalid();
        String sales=legacy?"":" LEFT JOIN sales_transaction t ON t.transaction_id=m.transaction_id AND t.store_id=?";
        String sql="SELECT COUNT(*) FROM "+table+" m LEFT JOIN product p ON p.product_id=m.product_id AND p.store_id=?"+sales+" WHERE m.snapshot_id=? AND (p.product_id IS NULL"+(legacy?"":" OR t.transaction_id IS NULL")+")";
        Long foreign=legacy?jdbc.queryForObject(sql,Long.class,store,id):jdbc.queryForObject(sql,Long.class,store,store,id);
        if(foreign!=0)throw invalid();
        if(complete) {
            var total=jdbc.queryForObject("SELECT COALESCE(SUM(weighted_utility),0) FROM "+table+" WHERE snapshot_id=?",java.math.BigDecimal.class,id);
            var expected=jdbc.queryForObject("SELECT total_weighted_utility FROM utility_snapshot WHERE snapshot_id=?",java.math.BigDecimal.class,id);
            if(expected==null||total.compareTo(expected)!=0) {
                if(legacy)throw invalid();
                throw new ApiException(400,"Snapshot total does not match frozen matrix");
            }
        }
        return new Route(table,legacy);
    }
    public void assertMiningOwnership(int store,int id){route(store,id,true);}
    public void assertAssociationEvidence(int store,int id){
        var route=route(store,id,true);
        if(route.legacy())throw invalid();
        var snapshot=find(store,id);
        if(snapshot.configuration()==null||!"F04_DATABASE_V1".equals(snapshot.configuration().source()))throw invalid();
        var namespaces=jdbc.queryForList("SELECT source_namespace FROM utility_snapshot_storage WHERE snapshot_id=?",String.class,id);
        if(!namespaces.isEmpty()&&!"F04_DATABASE_V1".equals(namespaces.get(0)))throw invalid();
    }
    public long countItems(int id){return countItems(owner(id),id);}
    public long countItems(int store,int id){var route=route(store,id,true);return jdbc.queryForObject("SELECT COUNT(*) FROM "+route.table()+" WHERE snapshot_id=?",Long.class,id);}
    public List<MatrixItem> page(int store,int id,int page,int size) {
        var route=route(store,id,true);
        String projection=route.legacy()?"m.*,NULL AS expiry_evidence":"m.*";
        return jdbc.query("""
            SELECT %s,t.total AS transaction_total FROM %s m
            JOIN utility_snapshot s ON s.snapshot_id=m.snapshot_id
            JOIN (SELECT transaction_id,SUM(weighted_utility) AS total FROM %s
                  WHERE snapshot_id=? GROUP BY transaction_id) t ON t.transaction_id=m.transaction_id
            WHERE s.store_id=? AND m.snapshot_id=? ORDER BY m.transaction_id,m.product_id LIMIT ? OFFSET ?
            """.formatted(projection,route.table(),route.table()),(rs,row)->new MatrixItem(rs.getInt("transaction_id"),rs.getInt("product_id"),rs.getInt("product_id"),rs.getInt("quantity"),rs.getBigDecimal("base_utility"),rs.getBigDecimal("stock_score"),rs.getBigDecimal("expiry_score"),rs.getBigDecimal("seasonal_score"),rs.getString("expiry_status"),rs.getBigDecimal("weight_value"),rs.getBigDecimal("weighted_utility"),rs.getBigDecimal("transaction_total"),rs.getString("expiry_evidence")==null?null:json.read(rs.getString("expiry_evidence"),ExpiryScoreCalculator.Evidence.class)),id,store,id,size,(long)page*size);
    }
}
