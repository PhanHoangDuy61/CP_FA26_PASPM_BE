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
 */
@Service
public class MiningScheduleReservation {
    private final JdbcTemplate jdbc;private final UtilityTimeZone storeZone;
    public MiningScheduleReservation(JdbcTemplate jdbc,UtilityTimeZone storeZone){this.jdbc=jdbc;this.storeZone=storeZone;}
    private record Due(long id,int store,int actor,long version,Frequency frequency,Integer dayOfWeek,Integer dayOfMonth,
        LocalTime time,String zone,int lookback,boolean compare,Instant scheduled) {}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Optional<Long> reserveOne(Instant now){
        var due=jdbc.query("SELECT * FROM mining_schedule WHERE enabled=TRUE AND next_fire_at<=? AND time_zone=? AND lookback_days BETWEEN ? AND ? ORDER BY next_fire_at,schedule_id LIMIT 1 FOR UPDATE",
            (r,n)->new Due(r.getLong("schedule_id"),r.getInt("store_id"),r.getInt("updated_by"),r.getLong("schedule_version"),Frequency.valueOf(r.getString("frequency")),r.getObject("day_of_week",Integer.class),r.getObject("day_of_month",Integer.class),LocalTime.parse(r.getString("local_time")),r.getString("time_zone"),r.getInt("lookback_days"),r.getBoolean("compare_algorithms"),r.getTimestamp("next_fire_at").toInstant()),Timestamp.from(now),storeZone.id(),MIN_LOOKBACK_DAYS,MAX_LOOKBACK_DAYS);
        if(due.isEmpty())return Optional.empty();
        Due d=due.get(0);
        long config=jdbc.queryForObject("SELECT config_version FROM mining_config_head WHERE config_id=1 FOR UPDATE",Long.class);
        if(config==0)throw new ApiException(409,"Configure mining policy before scheduling");
        var analysis=d.scheduled().atZone(ZoneId.of(d.zone())).toLocalDate();
        var end=analysis.minusDays(1);var start=end.minusDays(d.lookback()-1L);
        var requestKey=UUID.nameUUIDFromBytes(("PASPM-SCHEDULE-V1|"+d.id()+"|"+d.scheduled()).getBytes(StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO mining_schedule_occurrence(schedule_id,store_id,scheduled_at,schedule_version,config_version,request_key,status,actor_id,range_start,range_end,analysis_date,time_zone,compare_algorithms) VALUES(?,?,?,?,?,?,'PENDING',?,?,?,?,?,?)",
            d.id(),d.store(),Timestamp.from(d.scheduled()),d.version(),config,requestKey.toString(),d.actor(),start,end,analysis,d.zone(),d.compare());
        long id=jdbc.queryForObject("SELECT occurrence_id FROM mining_schedule_occurrence WHERE schedule_id=? AND scheduled_at=?",Long.class,d.id(),Timestamp.from(d.scheduled()));
        var request=new Request(d.store(),d.frequency(),d.dayOfWeek(),d.dayOfMonth(),d.time(),d.zone(),d.lookback(),d.compare());
        jdbc.update("UPDATE mining_schedule SET next_fire_at=? WHERE schedule_id=?",Timestamp.from(MiningScheduleService.next(request,d.scheduled())),d.id());
        return Optional.of(id);
    }
}
