package vn.edu.fpt.paspm.mining;

import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilitySettingsRepository;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.SnapshotView;
import static vn.edu.fpt.paspm.mining.MiningContracts.*;

@Repository
public class MiningRepository {
    private final JdbcTemplate jdbc;
    private final UtilitySettingsRepository json;
    public MiningRepository(JdbcTemplate jdbc,UtilitySettingsRepository json){this.jdbc=jdbc;this.json=json;}
    record Stored(RequestView view,MiningInputBuilder.Dataset input,String payloadHash) {}
    Stored find(int store,long id){return jdbc.query("SELECT * FROM mining_request WHERE store_id=? AND request_id=?",this::stored,store,id).stream().findFirst().orElseThrow(()->new ApiException(404,"Mining request not found"));}
    Stored byKey(int store,UUID key){return jdbc.query("SELECT * FROM mining_request WHERE store_id=? AND request_key=?",this::stored,store,key.toString()).stream().findFirst().orElse(null);}
    private Stored stored(ResultSet rs,int row)throws SQLException{
        var input=json.read(rs.getString("frozen_input"),MiningInputBuilder.Dataset.class);
        var snapshot=json.read(rs.getString("utility_config_snapshot"),SnapshotView.class);
        var algorithms=Arrays.stream(rs.getString("algorithms").split(",")).map(MiningEngine.Algorithm::valueOf).toList();
        var view=new RequestView(rs.getLong("request_id"),rs.getInt("store_id"),rs.getInt("requested_by"),UUID.fromString(rs.getString("request_key")),rs.getInt("utility_snapshot_id"),algorithms,MinUtilMode.valueOf(rs.getString("min_util_mode")),rs.getBigDecimal("min_util_value"),rs.getInt("min_util_spmf"),input.originalUtility(rs.getInt("min_util_spmf")),rs.getString("status"),rs.getString("comparison_status"),snapshot.matrixHash(),rs.getString("input_snapshot_hash"),input.conversionVersion(),input.utilityScale(),input.roundingMode(),input.divisor(),input.totalUtility(),input.transactionCount(),input.products().size(),input.inputBytes(),snapshot,rs.getTimestamp("created_at").toInstant());
        return new Stored(view,input,rs.getString("payload_hash"));
    }
    Stored create(int store,int actor,CreateRequest request,String payloadHash,SnapshotView snapshot,MiningInputBuilder.Dataset input,int threshold){
        String algorithms=request.algorithms().stream().sorted().map(Enum::name).collect(java.util.stream.Collectors.joining(","));
        long id=insert("INSERT INTO mining_request(store_id,requested_by,utility_snapshot_id,request_key,payload_hash,algorithms,min_util_mode,min_util_value,min_util_spmf,input_snapshot_hash,frozen_input,utility_config_snapshot,comparison_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)","request_id",store,actor,snapshot.snapshotId(),request.requestKey().toString(),payloadHash,algorithms,request.minUtilMode().name(),request.minUtilValue(),threshold,input.hash(),json.write(input),json.write(snapshot),request.algorithms().size()==2?"PENDING":"NOT_REQUESTED");
        for(var algorithm:request.algorithms().stream().sorted().toList())jdbc.update("INSERT INTO mining_run(store_id,request_id,algorithm,library_version,conversion_version,memory_method,runtime_method) VALUES (?,?,?,?,?,?,?)",store,id,algorithm.name(),SpmfMiningEngine.VERSION,input.conversionVersion(),SpmfMiningEngine.MEMORY_METHOD,SpmfMiningEngine.RUNTIME_METHOD);
        return find(store,id);
    }
    public Page<RequestView> requests(int store,int page,int size){return new Page<>(jdbc.query("SELECT * FROM mining_request WHERE store_id=? ORDER BY request_id DESC LIMIT ? OFFSET ?",this::stored,store,size,(long)page*size).stream().map(Stored::view).toList(),page,size,jdbc.queryForObject("SELECT COUNT(*) FROM mining_request WHERE store_id=?",Long.class,store));}
    public Page<SnapshotView> snapshots(int store,int page,int size,vn.edu.fpt.paspm.utility.UtilitySnapshotRepository snapshots){
        var ids=jdbc.queryForList("SELECT snapshot_id FROM utility_snapshot WHERE store_id=? AND status='READY' ORDER BY snapshot_id DESC LIMIT ? OFFSET ?",Integer.class,store,size,(long)page*size);
        return new Page<>(ids.stream().map(id->snapshots.find(store,id)).toList(),page,size,jdbc.queryForObject("SELECT COUNT(*) FROM utility_snapshot WHERE store_id=? AND status='READY'",Long.class,store));
    }
    public List<RunView> runs(int store,long request){find(store,request);return jdbc.query("SELECT r.*,q.utility_snapshot_id,q.min_util_spmf,q.input_snapshot_hash FROM mining_run r JOIN mining_request q ON q.request_id=r.request_id AND q.store_id=r.store_id WHERE r.store_id=? AND r.request_id=? ORDER BY r.run_id",this::run,store,request);}
    public RunView run(int store,long id){return jdbc.query("SELECT r.*,q.utility_snapshot_id,q.min_util_spmf,q.input_snapshot_hash FROM mining_run r JOIN mining_request q ON q.request_id=r.request_id AND q.store_id=r.store_id WHERE r.store_id=? AND r.run_id=?",this::run,store,id).stream().findFirst().orElseThrow(()->new ApiException(404,"Mining run not found"));}
    private RunView run(ResultSet r,int row)throws SQLException{
        Timestamp start=r.getTimestamp("started_at"),end=r.getTimestamp("completed_at");
        return new RunView(r.getLong("run_id"),r.getLong("request_id"),r.getInt("store_id"),r.getInt("utility_snapshot_id"),MiningEngine.Algorithm.valueOf(r.getString("algorithm")),r.getInt("attempt_no"),r.getString("library_version"),r.getString("status"),r.getInt("min_util_spmf"),r.getString("input_snapshot_hash"),r.getString("conversion_version"),start==null?null:start.toInstant(),end==null?null:end.toInstant(),r.getObject("runtime_ms",Long.class),r.getObject("memory_mib",Double.class),r.getString("memory_method"),r.getString("runtime_method"),r.getString("environment"),r.getObject("itemset_count",Integer.class),r.getString("failure_code"),r.getString("failure_message"));
    }
    boolean claimRequest(int store,long id){return jdbc.update("UPDATE mining_request SET status='RUNNING' WHERE store_id=? AND request_id=? AND status='PENDING'",store,id)==1;}
    boolean claimRun(int store,long id){return jdbc.update("UPDATE mining_run SET status='RUNNING',started_at=CURRENT_TIMESTAMP WHERE store_id=? AND run_id=? AND status='PENDING'",store,id)==1;}
    @Transactional
    public void complete(int store,long run,MiningInputBuilder.Dataset input,MiningEngine.Output output){
        var status=jdbc.queryForObject("SELECT status FROM mining_run WHERE store_id=? AND run_id=? FOR UPDATE",String.class,store,run);
        if(!"RUNNING".equals(status))throw new IllegalStateException("Mining run is not claimed");
        persistPatterns(store,run,input,output.patterns());
        jdbc.update("UPDATE mining_run SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP,runtime_ms=?,memory_mib=?,environment=?,itemset_count=? WHERE store_id=? AND run_id=?",output.runtimeMillis(),output.memoryMiB(),output.environment(),output.patterns().size(),store,run);
    }
    void persistPatterns(int store,long run,MiningInputBuilder.Dataset input,Map<String,Integer> patterns){
        var reverse=new HashMap<Integer,Integer>();input.products().forEach((id,product)->reverse.put(product,id));
        for(var pattern:patterns.entrySet()){
            var ids=Arrays.stream(pattern.getKey().split(",")).map(Integer::parseInt).toList();
            long id=insert("INSERT INTO high_utility_pattern(store_id,run_id,canonical_key,canonical_hash,utility,utility_spmf,pattern_size) VALUES (?,?,?,?,?,?,?)","pattern_id",store,run,pattern.getKey(),MiningInputBuilder.sha(pattern.getKey()),input.originalUtility(pattern.getValue()),pattern.getValue(),ids.size());
            for(int product:ids)jdbc.update("INSERT INTO pattern_item(pattern_id,store_id,product_id,algorithm_item_id) VALUES (?,?,?,?)",id,store,product,reverse.get(product));
        }
    }
    public Page<PatternView> patterns(int store,long run,int page,int size){
        run(store,run);
        return new Page<>(jdbc.query("SELECT * FROM high_utility_pattern WHERE store_id=? AND run_id=? ORDER BY utility_spmf DESC,canonical_key ASC LIMIT ? OFFSET ?",(r,n)->new PatternView(r.getLong("pattern_id"),run,Arrays.stream(r.getString("canonical_key").split(",")).map(Integer::parseInt).toList(),r.getBigDecimal("utility"),r.getInt("utility_spmf"),r.getInt("pattern_size"),r.getObject("support_count",Integer.class)),store,run,size,(long)page*size),page,size,jdbc.queryForObject("SELECT COUNT(*) FROM high_utility_pattern WHERE store_id=? AND run_id=?",Long.class,store,run));
    }
    Map<String,Integer> canonical(int store,long run){var map=new TreeMap<String,Integer>();jdbc.query("SELECT canonical_key,utility_spmf FROM high_utility_pattern WHERE store_id=? AND run_id=?",(org.springframework.jdbc.core.RowCallbackHandler)r->map.put(r.getString(1),r.getInt(2)),store,run);return map;}
    void failRun(int store,long id,String code,String message){jdbc.update("UPDATE mining_run SET status=?,failure_code=?,failure_message=?,completed_at=CURRENT_TIMESTAMP WHERE store_id=? AND run_id=? AND status IN ('PENDING','RUNNING')",code.equals("TIMED_OUT")?"TIMED_OUT":"FAILED",code,message,store,id);}
    void finish(int store,long request,String status,String comparison){jdbc.update("UPDATE mining_request SET status=?,comparison_status=? WHERE store_id=? AND request_id=? AND status IN ('PENDING','RUNNING')",status,comparison,store,request);}
    @Transactional
    public void recoverInterrupted(){
        jdbc.update("UPDATE mining_run SET status='FAILED',failure_code='INTERRUPTED',failure_message='Backend restarted before completion; submit a new requestKey to retry',completed_at=CURRENT_TIMESTAMP WHERE status IN ('PENDING','RUNNING')");
        jdbc.update("UPDATE mining_request SET status='FAILED',comparison_status=CASE WHEN comparison_status='PENDING' THEN 'BLOCKED' ELSE comparison_status END WHERE status IN ('PENDING','RUNNING')");
    }
    private long insert(String sql,String key,Object... args){var holder=new GeneratedKeyHolder();jdbc.update(c->{var ps=c.prepareStatement(sql,new String[]{key});for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},holder);return Objects.requireNonNull(holder.getKey()).longValue();}
}
