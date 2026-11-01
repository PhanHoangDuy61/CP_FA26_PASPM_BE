package vn.edu.fpt.paspm.mining;

import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityConfigurationService;
import vn.edu.fpt.paspm.utility.UtilityMath;
import vn.edu.fpt.paspm.utility.UtilityMatrixBuilder;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.*;
import vn.edu.fpt.paspm.utility.UtilitySettingsRepository;
import vn.edu.fpt.paspm.utility.UtilitySnapshotRepository;
import vn.edu.fpt.paspm.utility.UtilityTimeZone;

/** Internal-only occurrence-scoped snapshot creation; no HTTP endpoint grants F03 write access. */
@Service
public class MiningScheduledSnapshot {
    private final JdbcTemplate jdbc;private final UtilitySettingsRepository settings;private final UtilityMatrixBuilder builder;
    private final UtilitySnapshotRepository snapshots;private final UtilityTimeZone timeZone;private final TransactionTemplate transaction;
    public MiningScheduledSnapshot(JdbcTemplate jdbc,UtilitySettingsRepository settings,UtilityMatrixBuilder builder,UtilitySnapshotRepository snapshots,UtilityTimeZone timeZone,PlatformTransactionManager manager){this.jdbc=jdbc;this.settings=settings;this.builder=builder;this.snapshots=snapshots;this.timeZone=timeZone;this.transaction=new TransactionTemplate(manager);this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);}
    private record Occurrence(int store,int actor,long version,String status,Integer snapshot,LocalDate start,LocalDate end,LocalDate date) {}
    public int prepare(long occurrenceId){
        var scheduleId=jdbc.query("SELECT schedule_id FROM mining_schedule_occurrence WHERE occurrence_id=?",(r,n)->r.getLong(1),occurrenceId).stream().findFirst().orElseThrow(()->new ApiException(404,"Occurrence not found"));
        for(int attempt=0;attempt<3;attempt++) {
            try { return java.util.Objects.requireNonNull(transaction.execute(status->prepareLocked(occurrenceId,scheduleId))); }
            catch(org.springframework.dao.CannotAcquireLockException e) { if(attempt==2)throw e; }
        }
        throw new IllegalStateException("Snapshot retry exhausted");
    }
    private int prepareLocked(long occurrenceId,long scheduleId){
        var schedule=jdbc.query("SELECT store_id,enabled,schedule_version FROM mining_schedule WHERE schedule_id=? FOR UPDATE",(r,n)->new long[]{r.getLong(1),r.getBoolean(2)?1:0,r.getLong(3)},scheduleId).stream().findFirst().orElseThrow(()->new ApiException(404,"Schedule not found"));
        var row=jdbc.query("SELECT * FROM mining_schedule_occurrence WHERE occurrence_id=? AND schedule_id=? FOR UPDATE",(r,n)->new Occurrence(r.getInt("store_id"),r.getInt("actor_id"),r.getLong("schedule_version"),r.getString("status"),r.getObject("snapshot_id",Integer.class),r.getObject("range_start",LocalDate.class),r.getObject("range_end",LocalDate.class),r.getObject("analysis_date",LocalDate.class)),occurrenceId,scheduleId).stream().findFirst().orElseThrow(()->new ApiException(404,"Occurrence not found"));
        if(schedule[0]!=row.store()||schedule[1]!=1||schedule[2]!=row.version())throw new ApiException(409,"Schedule is disabled or has changed");
        if(!"PENDING".equals(row.status())&&!"PROCESSING".equals(row.status()))throw new ApiException(409,"Occurrence cannot create a snapshot");
        if(row.snapshot()!=null){if(snapshots.find(row.store(),row.snapshot())==null)throw new ApiException(409,"Occurrence snapshot unavailable");return row.snapshot();}
        if(row.actor()<=0||row.start()==null||row.end()==null||row.date()==null||row.start().isAfter(row.end()))throw new ApiException(409,"Occurrence context unavailable");
        int active=jdbc.queryForObject("SELECT COUNT(*) FROM app_user u JOIN role r ON r.role_id=u.role_id WHERE u.user_id=? AND u.status='ACTIVE' AND r.code='SYSTEM_ADMIN'",Integer.class,row.actor());
        if(active!=1)throw new ApiException(403,"Schedule owner unavailable");
        var policy=settings.policy(true);var config=settings.config(row.store(),true);
        UtilityConfigurationService.requireLambda(config.lambda(),policy,409);
        var range=new RangeRequest(row.start(),row.end(),row.date());
        var frozen=new FrozenConfiguration(config,policy,new EffectiveCoefficients(config.effectiveAlpha(),config.effectiveBeta(),config.effectiveGamma()),row.date(),builder.sourceType(),UtilityMath.SCALE,"HALF_UP","PRODUCT_ID_V1",timeZone.id());
        var matrix=builder.build(row.store(),range,frozen);
        if(!matrix.ready())throw new ApiException(400,"Scheduled snapshot requires complete enabled scores and expiry metadata");
        if(matrix.items().isEmpty())throw new ApiException(400,"No eligible transaction items in the selected range");
        int id=snapshots.create(row.store(),row.actor(),range,frozen,matrix);snapshots.saveItems(id,matrix.items());
        jdbc.update("UPDATE mining_schedule_occurrence SET snapshot_id=?,updated_at=CURRENT_TIMESTAMP WHERE occurrence_id=?",id,occurrenceId);
        return id;
    }
}
