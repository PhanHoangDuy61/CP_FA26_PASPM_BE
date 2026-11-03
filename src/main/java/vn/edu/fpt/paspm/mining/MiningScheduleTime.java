package vn.edu.fpt.paspm.mining;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

/** Calendar recurrence: missing month days clamp to month end; DST gaps move forward, overlaps use earlier offset. */
final class MiningScheduleTime {
    private MiningScheduleTime() {}
    static Instant nextWeekly(Instant after, ZoneId zone, DayOfWeek day, LocalTime time) {
        LocalDate date=after.atZone(zone).toLocalDate().with(TemporalAdjusters.nextOrSame(day));
        Instant candidate=date.atTime(time).atZone(zone).toInstant();
        return candidate.isAfter(after)?candidate:date.plusWeeks(1).atTime(time).atZone(zone).toInstant();
    }
    static Instant nextMonthly(Instant after, ZoneId zone, int day, LocalTime time) {
        if(day<1||day>31)throw new IllegalArgumentException("Month day must be 1..31");
        YearMonth month=YearMonth.from(after.atZone(zone));
        Instant candidate=month.atDay(Math.min(day,month.lengthOfMonth())).atTime(time).atZone(zone).toInstant();
        if(candidate.isAfter(after))return candidate;
        month=month.plusMonths(1);
        return month.atDay(Math.min(day,month.lengthOfMonth())).atTime(time).atZone(zone).toInstant();
    }
}
