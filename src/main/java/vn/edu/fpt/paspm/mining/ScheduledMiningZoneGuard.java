package vn.edu.fpt.paspm.mining;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.utility.UtilityTimeZone;

/**
 * Decision D4: this release schedules Store calendar days in {@value #APPROVED_ZONE}. F03 (snapshot dates), F04 (import dates) and the
 * scheduler all read {@code paspm.store-time-zone}; a process that would run the scheduler under another value refuses to start
 * instead of silently analysing the wrong calendar day. Nothing is checked while the dispatcher is off.
 */
@Component
public class ScheduledMiningZoneGuard {
    public static final String APPROVED_ZONE = "Asia/Ho_Chi_Minh";
    private final boolean dispatchEnabled;private final UtilityTimeZone zone;
    public ScheduledMiningZoneGuard(@Value("${paspm.mining.schedule-dispatch-enabled:false}") boolean dispatchEnabled,UtilityTimeZone zone){this.dispatchEnabled=dispatchEnabled;this.zone=zone;}
    @EventListener(ApplicationReadyEvent.class) public void verify(){check(dispatchEnabled,zone.id());}
    static void check(boolean dispatchEnabled,String storeZone){
        if(dispatchEnabled&&!APPROVED_ZONE.equals(storeZone))
            throw new IllegalStateException("paspm.mining.schedule-dispatch-enabled=true requires paspm.store-time-zone="+APPROVED_ZONE+" (decision D4), found "+storeZone);
    }
}
