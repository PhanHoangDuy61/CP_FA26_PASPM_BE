package vn.edu.fpt.paspm.mining;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityTimeZone;
import static vn.edu.fpt.paspm.mining.MiningScheduleController.*;

/**
 * Transactionally reserves one occurrence and its immutable execution context. Does not dispatch.
 * Only schedules that satisfy the approved policy are due: the Store calendar zone (D4) and a 7..365-day lookback (D3). A schedule
 * stored earlier with another zone or lookback is kept untouched and is simply never reserved until an administrator edits it.
 * Catch-up (D6): when several fire times are due, only the latest becomes a PENDING occurrence; each older one is recorded as
 * SKIPPED in the same transaction that advances the schedule cursor, so a crash or a competing instance can neither lose nor duplicate it.
 */
@Service
public class MiningScheduleReservation {
    /** Why an older missed fire time was not run. */
    public static final String SKIP_SUPERSEDED = "MISSED_SUPERSEDED_BY_LATER_OCCURRENCE";
    /** Operational bound per transaction; a longer backlog simply continues on the next call. It is not a maximum catch-up age. */
    static final int MAX_SKIPS_PER_TRANSACTION = 500;
    private final JdbcTemplate jdbc;private final UtilityTimeZone storeZone;private final SchedulerPrincipal principal;
    public MiningScheduleReservation(JdbcTemplate jdbc,UtilityTimeZone storeZone,SchedulerPrincipal principal){this.jdbc=jdbc;this.storeZone=storeZone;this.principal=principal;}
    private record Due(long id,int store,long version,Frequency frequency,Integer dayOfWeek,Integer dayOfMonth,
        LocalTime time,String zone,int lookback,boolean compare,Instant scheduled) {}
    /** {@code occurrenceId} is the PENDING occurrence reserved by this call, if any; {@code skipped} counts older fire times recorded as SKIPPED. */
    public record Outcome(Long occurrenceId,int skipped) { boolean progressed(){return occurrenceId!=null||skipped>0;} }
    /** Kept for callers that only need the reserved occurrence; transactional itself because a self-invoked {@link #reserve} would bypass the proxy. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Optional<Long> reserveOne(Instant now){return Optional.ofNullable(reserve(now).occurrenceId());}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Outcome reserve(Instant now){
        var due=jdbc.query("SELECT * FROM mining_schedule WHERE enabled=TRUE AND next_fire_at<=? AND time_zone=? AND lookback_days BETWEEN ? AND ? ORDER BY next_fire_at,schedule_id LIMIT 1 FOR UPDATE",
            (r,n)->new Due(r.getLong("schedule_id"),r.getInt("store_id"),r.getLong("schedule_version"),Frequency.valueOf(r.getString("frequency")),r.getObject("day_of_week",Integer.class),r.getObject("day_of_month",Integer.class),LocalTime.parse(r.getString("local_time")),r.getString("time_zone"),r.getInt("lookback_days"),r.getBoolean("compare_algorithms"),r.getTimestamp("next_fire_at").toInstant()),Timestamp.from(now),storeZone.id(),MIN_LOOKBACK_DAYS,MAX_LOOKBACK_DAYS);
        if(due.isEmpty())return new Outcome(null,0);
        Due d=due.get(0);
        long config=jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1 FOR UPDATE",Long.class);
        if(config==0)throw new ApiException(409,"Configure mining policy before scheduling");
        int actor=principal.id();
        var recurrence=new Request(d.store(),d.frequency(),d.dayOfWeek(),d.dayOfMonth(),d.time(),d.zone(),d.lookback(),d.compare());
        Instant fire=d.scheduled();int skipped=0;
        while(true){
            Instant following=MiningScheduleService.next(recurrence,fire);
            if(following.isAfter(now)){
                long id=insertPending(d,fire,config,actor);
                jdbc.update("UPDATE mining_schedule SET next_fire_at=? WHERE schedule_id=?",Timestamp.from(following),d.id());
                return new Outcome(id,skipped);
            }
            if(skipped>=MAX_SKIPS_PER_TRANSACTION){
                jdbc.update("UPDATE mining_schedule SET next_fire_at=? WHERE schedule_id=?",Timestamp.from(fire),d.id());
                return new Outcome(null,skipped);
            }
            insertSkipped(d,fire,config);skipped++;fire=following;
        }
    }
    private static String requestKey(Due d,Instant fire){return UUID.nameUUIDFromBytes(("PASPM-SCHEDULE-V1|"+d.id()+"|"+fire).getBytes(StandardCharsets.UTF_8)).toString();}
    private long insertPending(Due d,Instant fire,long config,int actor){
        var analysis=fire.atZone(ZoneId.of(d.zone())).toLocalDate();
        var end=analysis.minusDays(1);var start=end.minusDays(d.lookback()-1L);
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,actor_id,range_start,range_end,analysis_date,time_zone,compare_algorithms) VALUES(?,?,?,?,?,?,'PENDING',?,?,?,?,?,?)",
            d.id(),d.store(),Timestamp.from(fire),d.version(),config,requestKey(d,fire),actor,start,end,analysis,d.zone(),d.compare());
        return jdbc.queryForObject("SELECT occurrence_id FROM mining_schedule_occurrence WHERE schedule_id=? AND scheduled_at=?",Long.class,d.id(),Timestamp.from(fire));
    }
    /** A skipped occurrence has no actor, no analysed range, no snapshot and no request: nothing was prepared or run for it. */
    private void insertSkipped(Due d,Instant fire,long config){
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,skip_reason,time_zone,compare_algorithms) VALUES(?,?,?,?,?,?,'SKIPPED',?,?,?)",
            d.id(),d.store(),Timestamp.from(fire),d.version(),config,requestKey(d,fire),SKIP_SUPERSEDED,d.zone(),d.compare());
    }
}
