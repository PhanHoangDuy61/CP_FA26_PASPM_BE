package vn.edu.fpt.paspm.mining;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilitySnapshotRepository;
import static vn.edu.fpt.paspm.mining.MiningContracts.*;

/** Internal-only atomic occurrence/request linking. Caller must prepare its snapshot first. */
@Service
public class MiningScheduledRequest {
    private final JdbcTemplate jdbc;private final MiningRepository repository;private final UtilitySnapshotRepository snapshots;
    private final MiningInputBuilder builder;private final MiningEngine engine;private final MiningWorker worker;private final SchedulerPrincipal principal;private final TransactionTemplate tx;
    public MiningScheduledRequest(JdbcTemplate jdbc,MiningRepository repository,UtilitySnapshotRepository snapshots,MiningInputBuilder builder,MiningEngine engine,MiningWorker worker,SchedulerPrincipal principal,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.repository=repository;this.snapshots=snapshots;this.builder=builder;this.engine=engine;this.worker=worker;this.principal=principal;
        tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    private record Result(RequestView view,boolean created) {}
    private record Occ(int store,int actor,long scheduleVersion,long configVersion,UUID key,Integer snapshot,Long request,String status,boolean compare) {}
    public RequestView submit(long occurrenceId){
        Long scheduleId=jdbc.query("SELECT schedule_id FROM mining_schedule_occurrence WHERE occurrence_id=?",(r,n)->r.getLong(1),occurrenceId).stream().findFirst().orElseThrow(()->new ApiException(404,"Occurrence not found"));
        Result result=null;
        for(int attempt=0;attempt<3;attempt++) {
        try { result=tx.execute(status->{
            var schedule=jdbc.query("SELECT store_id,enabled,schedule_version FROM mining_schedule WHERE schedule_id=? FOR UPDATE",(r,n)->new long[]{r.getLong(1),r.getBoolean(2)?1:0,r.getLong(3)},scheduleId).stream().findFirst().orElseThrow(()->new ApiException(404,"Schedule not found"));
            var o=jdbc.query("SELECT * FROM mining_schedule_occurrence WHERE occurrence_id=? AND schedule_id=? FOR UPDATE",(r,n)->new Occ(r.getInt("store_id"),r.getInt("actor_id"),r.getLong("schedule_version"),r.getLong("config_version"),UUID.fromString(r.getString("request_key")),r.getObject("snapshot_id",Integer.class),r.getObject("request_id",Long.class),r.getString("status"),r.getBoolean("compare_algorithms")),occurrenceId,scheduleId).stream().findFirst().orElseThrow(()->new ApiException(404,"Occurrence not found"));
            if(schedule[0]!=o.store())throw new ApiException(409,"Occurrence Store mismatch");
            if(o.request()!=null)return new Result(repository.find(o.store(),o.request()).view(),false);
            if(schedule[1]!=1||schedule[2]!=o.scheduleVersion())throw new ApiException(409,"Schedule is disabled or has changed");
            if((!"PENDING".equals(o.status())&&!"PROCESSING".equals(o.status()))||o.snapshot()==null||o.actor()<=0||o.configVersion()<=0)throw new ApiException(409,"Occurrence is not ready to submit");
            principal.require(o.actor());
            engine.requireAvailable();
            var request=jdbc.query("SELECT algorithm,min_util_mode,min_util_value FROM mining_config_version WHERE config_version=?",(r,n)->new CreateRequest(o.key(),o.snapshot(),o.compare()?List.of(MiningEngine.Algorithm.HUI_MINER,MiningEngine.Algorithm.EFIM):List.of(MiningEngine.Algorithm.valueOf(r.getString(1))),MinUtilMode.valueOf(r.getString(2)),r.getBigDecimal(3)),o.configVersion()).stream().findFirst().orElseThrow(()->new ApiException(409,"Occurrence policy unavailable"));
            var snapshot=snapshots.find(o.store(),o.snapshot());if(snapshot==null)throw new ApiException(404,"Utility snapshot not found");
            if(snapshots.countItems(o.store(),o.snapshot())>MiningInputBuilder.MAX_ROWS)throw new ApiException(400,"Snapshot matrix exceeds mining row limit");
            var rows=snapshots.page(o.store(),o.snapshot(),0,MiningInputBuilder.MAX_ROWS);snapshots.assertMiningOwnership(o.store(),o.snapshot());
            var input=builder.build(snapshot,rows);int threshold=builder.threshold(input,request.minUtilMode(),request.minUtilValue());
            var saved=repository.create(o.store(),o.actor(),request,MiningInputBuilder.sha("SCHEDULE|"+occurrenceId),snapshot,input,threshold);
            jdbc.update("UPDATE mining_request SET config_version=? WHERE store_id=? AND request_id=?",o.configVersion(),o.store(),saved.view().requestId());
            jdbc.update("UPDATE mining_schedule_occurrence SET request_id=?,status='DISPATCHED',updated_at=CURRENT_TIMESTAMP WHERE occurrence_id=?",saved.view().requestId(),occurrenceId);
            return new Result(saved.view(),true);
        }); break; }
        catch(org.springframework.dao.CannotAcquireLockException e) { if(attempt==2)throw e; }
        }
        if(result==null)throw new IllegalStateException("Occurrence submission failed");
        if(result.created())worker.enqueue(result.view().storeId(),result.view().requestId());
        return repository.find(result.view().storeId(),result.view().requestId()).view();
    }
}
