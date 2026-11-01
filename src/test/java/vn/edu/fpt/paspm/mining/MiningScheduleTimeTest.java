package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class MiningScheduleTimeTest {
    @Test void weeklyAndMonthlyRecurrencesRespectLocalCalendarAndMonthEnd() {
        var zone=ZoneId.of("Asia/Ho_Chi_Minh");
        assertEquals(ZonedDateTime.of(2026,11,2,9,0,0,0,zone).toInstant(),
            MiningScheduleTime.nextWeekly(Instant.parse("2026-10-31T12:00:00Z"),zone,DayOfWeek.MONDAY,LocalTime.of(9,0)));
        assertEquals(ZonedDateTime.of(2027,2,28,9,0,0,0,zone).toInstant(),
            MiningScheduleTime.nextMonthly(Instant.parse("2027-02-01T00:00:00Z"),zone,31,LocalTime.of(9,0)));
    }
    @Test void daylightSavingGapMovesForwardAndOverlapUsesEarlierOffset() {
        var zone=ZoneId.of("America/New_York");
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"),MiningScheduleTime.nextWeekly(
            Instant.parse("2026-03-02T00:00:00Z"),zone,DayOfWeek.SUNDAY,LocalTime.of(2,30)));
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"),MiningScheduleTime.nextWeekly(
            Instant.parse("2026-10-26T00:00:00Z"),zone,DayOfWeek.SUNDAY,LocalTime.of(1,30)));
    }
}
