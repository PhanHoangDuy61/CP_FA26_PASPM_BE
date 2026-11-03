package vn.edu.fpt.paspm.mining;

import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilitySnapshotRepository;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.SnapshotView;
import static vn.edu.fpt.paspm.mining.MiningContracts.*;

@Service
public class MiningService {
    private final MiningRepository repository;private final MiningInputBuilder builder;private final MiningEngine engine;
    private final MiningWorker worker;private final UtilitySnapshotRepository snapshots;private final CatalogAccess access;
    private final AuthService auth;private final JdbcTemplate jdbc;private final TransactionTemplate capture;
    public MiningService(MiningRepository repository,MiningInputBuilder builder,MiningEngine engine,MiningWorker worker,
        UtilitySnapshotRepository snapshots,CatalogAccess access,AuthService auth,JdbcTemplate jdbc,PlatformTransactionManager manager){
        this.repository=repository;this.builder=builder;this.engine=engine;this.worker=worker;this.snapshots=snapshots;this.access=access;this.auth=auth;this.jdbc=jdbc;
        capture=new TransactionTemplate(manager);capture.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public RequestView create(Jwt jwt,CreateRequest request){
        int store=access.currentStoreId(jwt);
        if(new HashSet<>(request.algorithms()).size()!=request.algorithms().size())throw new ApiException(400,"Algorithms must be unique");
        String hash=MiningInputBuilder.sha(request.snapshotId()+"|"+request.algorithms().stream().sorted().toList()+"|"+request.minUtilMode()+"|"+request.minUtilValue().stripTrailingZeros().toPlainString());
        var previous=repository.byKey(store,request.requestKey());if(previous!=null)return same(previous,hash);
        // Resolve ownership before dependency health, so foreign snapshots always follow the safe 404 convention.
        if(snapshots.find(store,request.snapshotId())==null)throw new ApiException(404,"Utility snapshot not found");
        engine.requireAvailable();
        MiningRepository.Stored created;
        try{
            created=capture.execute(tx->{
                jdbc.queryForObject("SELECT snapshot_id FROM utility_snapshot WHERE store_id=? AND snapshot_id=? FOR UPDATE",Integer.class,store,request.snapshotId());
                var snapshot=snapshots.find(store,request.snapshotId());
                if(snapshots.countItems(store,request.snapshotId())>MiningInputBuilder.MAX_ROWS)throw new ApiException(400,"Snapshot matrix exceeds mining row limit");
                var rows=snapshots.page(store,request.snapshotId(),0,MiningInputBuilder.MAX_ROWS);
                snapshots.assertMiningOwnership(store,request.snapshotId());
                var input=builder.build(snapshot,rows);int threshold=builder.threshold(input,request.minUtilMode(),request.minUtilValue());
                return repository.create(store,auth.current(jwt).id(),request,hash,snapshot,input,threshold);
            });
        }catch(DuplicateKeyException e){var raced=repository.byKey(store,request.requestKey());if(raced==null)throw e;return same(raced,hash);}
        worker.enqueue(store,Objects.requireNonNull(created).view().requestId());
        return repository.find(store,created.view().requestId()).view();
    }
    /** Explicit Store is accepted only by the System Admin controller; all lookups remain Store-scoped. */
    public RequestView createAdministrative(Jwt jwt, int store, int snapshotId, UUID key, boolean compare) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM store WHERE store_id=?",Integer.class,store)!=1)
            throw new ApiException(404,"Store not found");
        String hash=MiningInputBuilder.sha("ADMIN|"+snapshotId+"|"+compare);
        var previous=repository.byKey(store,key);
        if(previous!=null)return same(previous,hash);
        if(snapshots.find(store,snapshotId)==null)throw new ApiException(404,"Utility snapshot not found");
        engine.requireAvailable();
        MiningRepository.Stored created;
        try {
            created=capture.execute(tx->{
                long version=jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1 FOR UPDATE",Long.class);
                if(version==0)throw new ApiException(409,"Configure mining policy before execution");
                var policy=jdbc.queryForObject("SELECT algorithm,min_util_mode,min_util_value FROM mining_config_version WHERE config_version=?",
                    (rs,row)->new CreateRequest(key,snapshotId,compare?List.of(MiningEngine.Algorithm.HUI_MINER,MiningEngine.Algorithm.EFIM):List.of(MiningEngine.Algorithm.valueOf(rs.getString(1))),MinUtilMode.valueOf(rs.getString(2)),rs.getBigDecimal(3)),version);
                jdbc.queryForObject("SELECT snapshot_id FROM utility_snapshot WHERE store_id=? AND snapshot_id=? FOR UPDATE",Integer.class,store,snapshotId);
                var snapshot=snapshots.find(store,snapshotId);
                if(snapshots.countItems(store,snapshotId)>MiningInputBuilder.MAX_ROWS)throw new ApiException(400,"Snapshot matrix exceeds mining row limit");
                var rows=snapshots.page(store,snapshotId,0,MiningInputBuilder.MAX_ROWS);
                snapshots.assertMiningOwnership(store,snapshotId);
                var input=builder.build(snapshot,rows);
                int threshold=builder.threshold(input,policy.minUtilMode(),policy.minUtilValue());
                var saved=repository.create(store,auth.current(jwt).id(),policy,hash,snapshot,input,threshold);
                jdbc.update("UPDATE mining_request SET config_version=? WHERE store_id=? AND request_id=?",version,store,saved.view().requestId());
                return saved;
            });
        } catch(DuplicateKeyException e) { var raced=repository.byKey(store,key); if(raced==null)throw e;return same(raced,hash); }
        worker.enqueue(store,Objects.requireNonNull(created).view().requestId());
        return repository.find(store,created.view().requestId()).view();
    }
    private RequestView same(MiningRepository.Stored previous,String hash){if(!previous.payloadHash().equals(hash))throw new ApiException(409,"requestKey already used for a different mining request");return previous.view();}
    public Page<SnapshotView> snapshots(Jwt jwt,int page,int size){pagination(page,size);return repository.snapshots(access.currentStoreId(jwt),page,size,snapshots);}
    public Page<RequestView> requests(Jwt jwt,int page,int size){pagination(page,size);return repository.requests(access.currentStoreId(jwt),page,size);}
    public RequestView request(Jwt jwt,long id){return repository.find(access.currentStoreId(jwt),id).view();}
    public List<RunView> runs(Jwt jwt,long id){return repository.runs(access.currentStoreId(jwt),id);}
    public RunView run(Jwt jwt,long id){return repository.run(access.currentStoreId(jwt),id);}
    public Page<PatternView> patterns(Jwt jwt,long id,int page,int size){pagination(page,size);return repository.patterns(access.currentStoreId(jwt),id,page,size);}
    /** System Admin reads: the Store is explicit, existence is checked, and every lookup stays Store-composite-keyed. */
    public Page<SnapshotView> snapshotsForStore(int store,int page,int size){pagination(page,size);requireStore(store);return repository.snapshots(store,page,size,snapshots);}
    public Page<RequestView> requestsForStore(int store,int page,int size){pagination(page,size);requireStore(store);return repository.requests(store,page,size);}
    public RequestView requestForStore(int store,long id){requireStore(store);return repository.find(store,id).view();}
    public List<RunView> runsForStore(int store,long request){requireStore(store);return repository.runs(store,request);}
    public Page<PatternView> patternsForStore(int store,long run,int page,int size){pagination(page,size);requireStore(store);return repository.patterns(store,run,page,size);}
    private void requireStore(int store){if(jdbc.queryForObject("SELECT COUNT(*) FROM store WHERE store_id=?",Integer.class,store)!=1)throw new ApiException(404,"Store not found");}
    public BenchmarkView benchmark(Jwt jwt,long id){return benchmarkForStore(access.currentStoreId(jwt),id);}
    public BenchmarkView benchmarkForStore(int store,long id){var run=repository.run(store,id);var request=repository.find(store,run.requestId()).view();return new BenchmarkView(run,request.snapshotHash(),request.transactionCount(),request.distinctItems(),request.inputBytes(),request.divisor(),request.utilityScale(),request.effectiveMinUtility(),request.comparisonStatus(),"ONE_COLD_RUN_PER_ALGORITHM_NO_WARMUP_SEQUENTIAL_SAME_LIMITS");}
    private void pagination(int page,int size){if(page<0||size<1||size>100)throw new ApiException(400,"page must be >=0 and pageSize in 1..100");}
}
