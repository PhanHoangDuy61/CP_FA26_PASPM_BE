package vn.edu.fpt.paspm.utility;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.*;

@Repository
public class UtilitySnapshotRepository {
    private final JdbcTemplate jdbc;
    private final UtilitySettingsRepository json;
    public UtilitySnapshotRepository(JdbcTemplate jdbc,UtilitySettingsRepository json) { this.jdbc=jdbc;this.json=json; }
    public int create(int store,int actor,RangeRequest range,FrozenConfiguration configuration,MatrixResult matrix) {
        var key=new GeneratedKeyHolder();
        jdbc.update(connection->{
            var ps=connection.prepareStatement("INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (?,?,?,?,?,?,?,?,?,6,'READY')",new String[]{"snapshot_id"});
            ps.setInt(1,store);ps.setInt(2,actor);ps.setDate(3,Date.valueOf(range.rangeStart()));ps.setDate(4,Date.valueOf(range.rangeEnd()));ps.setDate(5,Date.valueOf(range.asOfDate()));
            ps.setString(6,json.write(configuration));ps.setString(7,matrix.sourceHash());ps.setString(8,matrix.matrixHash());ps.setBigDecimal(9,matrix.totalWeightedUtility());return ps;
        },key);
        return key.getKey().intValue();
    }
    public void saveItems(int snapshot,List<MatrixItem> items) {
        jdbc.batchUpdate("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,quantity,base_utility,stock_score,expiry_score,seasonal_score,expiry_status,weight_value,weighted_utility) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            items,500,(ps,item)-> { ps.setInt(1,snapshot);ps.setInt(2,item.transactionId());ps.setInt(3,item.productId());ps.setInt(4,item.quantity());ps.setBigDecimal(5,item.baseUtility());ps.setBigDecimal(6,item.stockScore());ps.setBigDecimal(7,item.expiryScore());ps.setBigDecimal(8,item.seasonalScore());ps.setString(9,item.expiryStatus());ps.setBigDecimal(10,item.weightValue());ps.setBigDecimal(11,item.weightedUtility()); });
    }
    public SnapshotView find(int store,int id) {
        return jdbc.query("SELECT * FROM utility_snapshot WHERE store_id=? AND snapshot_id=?",(rs,row)->new SnapshotView(rs.getInt("snapshot_id"),rs.getInt("store_id"),rs.getInt("created_by"),rs.getObject("range_start",LocalDate.class),rs.getObject("range_end",LocalDate.class),rs.getObject("as_of_date",LocalDate.class),json.read(rs.getString("config_snapshot"),FrozenConfiguration.class),rs.getString("source_hash"),rs.getString("matrix_hash"),rs.getBigDecimal("total_weighted_utility"),rs.getInt("rounding_scale"),rs.getString("status"),rs.getTimestamp("created_at").toInstant()),store,id).stream().findFirst().orElse(null);
    }
    public long countItems(int id) { return jdbc.queryForObject("SELECT COUNT(*) FROM utility_matrix_item WHERE snapshot_id=?",Long.class,id); }
    public List<MatrixItem> page(int store,int id,int page,int size) {
        return jdbc.query("""
            SELECT m.*,t.total AS transaction_total FROM utility_matrix_item m
            JOIN utility_snapshot s ON s.snapshot_id=m.snapshot_id
            JOIN (SELECT transaction_id,SUM(weighted_utility) AS total FROM utility_matrix_item
                  WHERE snapshot_id=? GROUP BY transaction_id) t ON t.transaction_id=m.transaction_id
            WHERE s.store_id=? AND m.snapshot_id=? ORDER BY m.transaction_id,m.product_id LIMIT ? OFFSET ?
            """,(rs,row)->new MatrixItem(rs.getInt("transaction_id"),rs.getInt("product_id"),rs.getInt("product_id"),rs.getInt("quantity"),rs.getBigDecimal("base_utility"),rs.getBigDecimal("stock_score"),rs.getBigDecimal("expiry_score"),rs.getBigDecimal("seasonal_score"),rs.getString("expiry_status"),rs.getBigDecimal("weight_value"),rs.getBigDecimal("weighted_utility"),rs.getBigDecimal("transaction_total")),id,store,id,size,(long)page*size);
    }
}
