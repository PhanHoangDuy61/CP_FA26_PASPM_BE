package vn.edu.fpt.paspm.mining;

import java.sql.Timestamp;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.common.ApiException;
import static vn.edu.fpt.paspm.mining.MiningScheduleController.*;

@Service
public class MiningScheduleService {
    private final JdbcTemplate jdbc; private final AuthService auth;
    public MiningScheduleService(JdbcTemplate jdbc,AuthService auth){this.jdbc=jdbc;this.auth=auth;}
    private void validate(Request r){
        if(!ZoneId.getAvailableZoneIds().contains(r.timeZone()))throw new ApiException(400,"Invalid IANA timezone");
        if((r.frequency()==Frequency.WEEKLY&&(r.dayOfWeek()==null||r.dayOfMonth()!=null)) ||
           (r.frequency()==Frequency.MONTHLY&&(r.dayOfMonth()==null||r.dayOfWeek()!=null)))throw new ApiException(400,"Invalid recurrence day");
        if(r.localTime().getSecond()!=0||r.localTime().getNano()!=0)throw new ApiException(400,"Schedule time must have minute precision");
        if(jdbc.queryForObject("SELECT COUNT(*) FROM store WHERE store_id=?",Integer.class,r.storeId())!=1)throw new ApiException(404,"Store not found");
    }
    @Transactional public View create(Jwt jwt,Request r){
        validate(r);int actor=auth.current(jwt).id();var key=new GeneratedKeyHolder();
        jdbc.update(c->{var p=c.prepareStatement("INSERT INTO mining_schedule(store_id,created_by,updated_by,frequency,day_of_week,day_of_month,local_time,time_zone,lookback_days,compare_algorithms) VALUES(?,?,?,?,?,?,?,?,?,?)",new String[]{"schedule_id"});
            p.setInt(1,r.storeId());p.setInt(2,actor);p.setInt(3,actor);p.setString(4,r.frequency().name());p.setObject(5,r.dayOfWeek());p.setObject(6,r.dayOfMonth());p.setString(7,r.localTime().toString());p.setString(8,r.timeZone());p.setInt(9,r.lookbackDays());p.setBoolean(10,Boolean.TRUE.equals(r.compareAlgorithms()));return p;},key);
        return get(key.getKey().longValue());
    }
    @Transactional(readOnly=true) public View get(long id){
        return jdbc.query("SELECT * FROM mining_schedule WHERE schedule_id=?",(r,n)->new View(r.getLong("schedule_id"),r.getInt("store_id"),r.getInt("created_by"),r.getInt("updated_by"),Frequency.valueOf(r.getString("frequency")),r.getObject("day_of_week",Integer.class),r.getObject("day_of_month",Integer.class),LocalTime.parse(r.getString("local_time")),r.getString("time_zone"),r.getInt("lookback_days"),r.getBoolean("compare_algorithms"),r.getBoolean("enabled"),r.getTimestamp("next_fire_at")==null?null:r.getTimestamp("next_fire_at").toInstant(),r.getLong("schedule_version")),id).stream().findFirst().orElseThrow(()->new ApiException(404,"Schedule not found"));
    }
    @Transactional(readOnly=true) public MiningContracts.Page<View> list(int page,int size){
        if(page<0||size<1||size>100)throw new ApiException(400,"Invalid pagination");
        var ids=jdbc.queryForList("SELECT schedule_id FROM mining_schedule ORDER BY schedule_id DESC LIMIT ? OFFSET ?",Long.class,size,(long)page*size);
        return new MiningContracts.Page<>(ids.stream().map(this::get).toList(),page,size,jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule",Long.class));
    }
    @Transactional(readOnly=true) public MiningContracts.Page<OccurrenceView> occurrences(long id,int page,int size){
        if(page<0||size<1||size>100)throw new ApiException(400,"Invalid pagination");
        get(id);
        var items=jdbc.query("SELECT * FROM mining_schedule_occurrence WHERE schedule_id=? ORDER BY occurrence_id DESC LIMIT ? OFFSET ?",this::mapOccurrence,id,size,(long)page*size);
        return new MiningContracts.Page<>(items,page,size,jdbc.queryForObject("SELECT COUNT(*) FROM mining_schedule_occurrence WHERE schedule_id=?",Long.class,id));
    }
    @Transactional(readOnly=true) public OccurrenceView occurrence(long scheduleId,long occurrenceId){
        return jdbc.query("SELECT * FROM mining_schedule_occurrence WHERE schedule_id=? AND occurrence_id=?",this::mapOccurrence,scheduleId,occurrenceId).stream().findFirst().orElseThrow(()->new ApiException(404,"Occurrence not found"));
    }
    private OccurrenceView mapOccurrence(java.sql.ResultSet r,int n)throws java.sql.SQLException{
        var nextRetry=r.getTimestamp("next_retry_at");
        return new OccurrenceView(r.getLong("occurrence_id"),r.getLong("schedule_id"),r.getInt("store_id"),r.getTimestamp("scheduled_at").toInstant(),r.getLong("schedule_version"),r.getLong("config_version"),r.getObject("actor_id",Integer.class),r.getObject("range_start",LocalDate.class),r.getObject("range_end",LocalDate.class),r.getObject("analysis_date",LocalDate.class),r.getString("time_zone"),r.getObject("compare_algorithms",Boolean.class),r.getObject("snapshot_id",Integer.class),r.getObject("request_id",Long.class),r.getString("status"),r.getString("failure_code"),r.getInt("attempt_count"),nextRetry==null?null:nextRetry.toInstant(),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    }
    @Transactional public View update(Jwt jwt,long id,Update u){
        var r=u.schedule();validate(r);var existing=jdbc.query("SELECT store_id,schedule_version FROM mining_schedule WHERE schedule_id=? FOR UPDATE",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},id).stream().findFirst().orElseThrow(()->new ApiException(404,"Schedule not found"));
        if(existing[0]!=r.storeId())throw new ApiException(400,"Schedule Store cannot be changed");
        if(existing[1]!=u.expectedVersion())throw new ApiException(409,"Schedule version conflict");
        // Fail closed until the durable dispatcher and fresh-snapshot recovery are implemented.
        if(u.enabled())throw new ApiException(503,"Scheduled mining dispatch is not available");
        Instant next=u.enabled()?next(r,Instant.now()):null;
        jdbc.update("UPDATE mining_schedule SET updated_by=?,frequency=?,day_of_week=?,day_of_month=?,local_time=?,time_zone=?,lookback_days=?,compare_algorithms=?,enabled=?,next_fire_at=?,schedule_version=schedule_version+1,updated_at=CURRENT_TIMESTAMP WHERE schedule_id=?",auth.current(jwt).id(),r.frequency().name(),r.dayOfWeek(),r.dayOfMonth(),r.localTime().toString(),r.timeZone(),r.lookbackDays(),Boolean.TRUE.equals(r.compareAlgorithms()),u.enabled(),next==null?null:Timestamp.from(next),id);
        return get(id);
    }
    static Instant next(Request r,Instant after){var zone=ZoneId.of(r.timeZone());return r.frequency()==Frequency.WEEKLY?MiningScheduleTime.nextWeekly(after,zone,DayOfWeek.of(r.dayOfWeek()),r.localTime()):MiningScheduleTime.nextMonthly(after,zone,r.dayOfMonth(),r.localTime());}
}
