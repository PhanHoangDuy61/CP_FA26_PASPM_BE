package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Characterises the CURRENT recurrence arithmetic (month-end clamp, DST gap forward, DST overlap earlier offset). These
 * tests pin engineering behaviour; they are not Product Owner approval of decisions D4 (time zone) or D5 (monthly overflow).
 */
class MiningScheduleTimeEdgeCasesTest {
    static final ZoneId HCM = ZoneId.of("Asia/Ho_Chi_Minh");

    static Instant at(int y, int m, int d, int h, int min, ZoneId zone) { return ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant(); }

    @Test void monthlyDay29ClampsOnlyInNonLeapFebruaryAndHonoursLeapYears() {
        var t = LocalTime.of(9, 0);
        assertEquals(at(2027, 2, 28, 9, 0, HCM), MiningScheduleTime.nextMonthly(at(2027, 1, 30, 0, 0, HCM), HCM, 29, t));
        assertEquals(at(2028, 2, 29, 9, 0, HCM), MiningScheduleTime.nextMonthly(at(2028, 1, 30, 0, 0, HCM), HCM, 29, t));
        assertEquals(at(2028, 2, 29, 9, 0, HCM), MiningScheduleTime.nextMonthly(at(2028, 1, 31, 10, 0, HCM), HCM, 31, t));
        assertEquals(at(2100, 2, 28, 9, 0, HCM), MiningScheduleTime.nextMonthly(at(2100, 2, 1, 0, 0, HCM), HCM, 29, t), "2100 is not a leap year");
    }

    @Test void monthlyDay30And31UseTheLastDayOfShortMonths() {
        var t = LocalTime.of(23, 30);
        assertEquals(at(2026, 4, 30, 23, 30, HCM), MiningScheduleTime.nextMonthly(at(2026, 4, 1, 0, 0, HCM), HCM, 31, t));
        assertEquals(at(2026, 2, 28, 23, 30, HCM), MiningScheduleTime.nextMonthly(at(2026, 2, 1, 0, 0, HCM), HCM, 30, t));
        assertEquals(at(2026, 12, 31, 23, 30, HCM), MiningScheduleTime.nextMonthly(at(2026, 12, 1, 0, 0, HCM), HCM, 31, t));
    }

    @Test void afterAClampedFireTheNextMonthRestoresTheConfiguredDay() {
        var t = LocalTime.of(9, 0);
        Instant feb = MiningScheduleTime.nextMonthly(at(2027, 2, 1, 0, 0, HCM), HCM, 31, t);
        assertEquals(at(2027, 2, 28, 9, 0, HCM), feb);
        assertEquals(at(2027, 3, 31, 9, 0, HCM), MiningScheduleTime.nextMonthly(feb, HCM, 31, t), "March keeps day 31, it does not drift to 28");
        Instant apr = MiningScheduleTime.nextMonthly(at(2027, 3, 31, 9, 0, HCM), HCM, 31, t);
        assertEquals(at(2027, 4, 30, 9, 0, HCM), apr);
        assertEquals(at(2027, 5, 31, 9, 0, HCM), MiningScheduleTime.nextMonthly(apr, HCM, 31, t));
    }

    @Test void theResultIsStrictlyAfterTheInstantEvenAtTheExactFireTime() {
        var t = LocalTime.of(9, 0);
        Instant fire = at(2026, 11, 2, 9, 0, HCM);
        assertEquals(at(2026, 11, 9, 9, 0, HCM), MiningScheduleTime.nextWeekly(fire, HCM, DayOfWeek.MONDAY, t));
        assertEquals(at(2026, 12, 2, 9, 0, HCM), MiningScheduleTime.nextMonthly(fire, HCM, 2, t));
        assertEquals(fire, MiningScheduleTime.nextWeekly(fire.minusSeconds(1), HCM, DayOfWeek.MONDAY, t), "one second earlier still returns the fire itself");
    }

    @Test void weeklyHandlesTheYearBoundaryEveryWeekdayAndLocalDateOfTheZone() {
        var t = LocalTime.of(0, 30);
        for (DayOfWeek day : DayOfWeek.values()) {
            Instant next = MiningScheduleTime.nextWeekly(at(2026, 12, 30, 12, 0, HCM), HCM, day, t);
            var local = next.atZone(HCM);
            assertEquals(day, local.getDayOfWeek());
            assertEquals(t, local.toLocalTime());
            assertTrue(next.isAfter(at(2026, 12, 30, 12, 0, HCM)));
            assertTrue(next.isBefore(at(2026, 12, 30, 12, 0, HCM).plus(Duration.ofDays(7))));
        }
        assertEquals(at(2027, 1, 1, 0, 30, HCM), MiningScheduleTime.nextWeekly(at(2026, 12, 30, 12, 0, HCM), HCM, DayOfWeek.FRIDAY, t));
    }

    @Test void theZoneNotUtcDecidesWhichCalendarDayAnInstantBelongsTo() {
        var auckland = ZoneId.of("Pacific/Auckland");
        // 2026-11-01T23:00Z is already Monday 2026-11-02 in Auckland (UTC+13 in summer time) but Sunday in UTC
        Instant after = Instant.parse("2026-11-01T23:00:00Z");
        assertEquals(at(2026, 11, 9, 9, 0, auckland), MiningScheduleTime.nextWeekly(after, auckland, DayOfWeek.MONDAY, LocalTime.of(9, 0)));
        assertEquals(at(2026, 11, 2, 9, 0, auckland), MiningScheduleTime.nextWeekly(after.minus(Duration.ofHours(12)), auckland, DayOfWeek.MONDAY, LocalTime.of(9, 0)));
    }

    @Test void halfHourAndFortyFiveMinuteOffsetsKeepTheLocalWallClockTime() {
        for (String id : List.of("Asia/Kolkata", "Asia/Kathmandu", "Australia/Lord_Howe", "Pacific/Chatham")) {
            ZoneId zone = ZoneId.of(id);
            Instant next = MiningScheduleTime.nextMonthly(Instant.parse("2026-06-01T00:00:00Z"), zone, 15, LocalTime.of(6, 15));
            assertEquals(LocalTime.of(6, 15), next.atZone(zone).toLocalTime(), id);
            assertEquals(15, next.atZone(zone).getDayOfMonth(), id);
        }
    }

    @Test void monthlyAcrossTheSpringForwardGapMovesToTheFirstValidInstant() {
        var ny = ZoneId.of("America/New_York");
        // 2026-03-08 02:30 does not exist in New York; Java moves it forward by the gap length to 03:30 EDT
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), MiningScheduleTime.nextMonthly(Instant.parse("2026-03-01T00:00:00Z"), ny, 8, LocalTime.of(2, 30)));
        // the following month is back to the configured wall clock
        assertEquals(at(2026, 4, 8, 2, 30, ny), MiningScheduleTime.nextMonthly(Instant.parse("2026-03-08T07:30:00Z"), ny, 8, LocalTime.of(2, 30)));
    }

    @Test void monthlyAcrossTheFallBackOverlapUsesTheEarlierOffsetAndFiresOnce() {
        var ny = ZoneId.of("America/New_York");
        Instant first = MiningScheduleTime.nextMonthly(Instant.parse("2026-11-01T00:00:00Z"), ny, 1, LocalTime.of(1, 30));
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), first, "01:30 EDT, the earlier of the two 01:30 instants");
        Instant next = MiningScheduleTime.nextMonthly(first, ny, 1, LocalTime.of(1, 30));
        assertEquals(at(2026, 12, 1, 1, 30, ny), next, "the second 01:30 (EST) is not a separate fire: the next fire is a month later");
    }

    @Test void aThreeYearSequenceIsStrictlyIncreasingAndNeverSkipsOrRepeatsAMonth() {
        var zone = ZoneId.of("Europe/Berlin");
        for (int day : new int[] {1, 15, 28, 29, 30, 31}) {
            List<Instant> fires = new ArrayList<>();
            Instant cursor = Instant.parse("2026-01-01T00:00:00Z");
            for (int i = 0; i < 36; i++) { cursor = MiningScheduleTime.nextMonthly(cursor, zone, day, LocalTime.of(2, 30)); fires.add(cursor); }
            for (int i = 1; i < fires.size(); i++) assertTrue(fires.get(i).isAfter(fires.get(i - 1)), "day " + day + " index " + i);
            YearMonth expected = YearMonth.of(2026, 1);
            for (Instant f : fires) {
                assertEquals(expected, YearMonth.from(f.atZone(zone)), "day " + day);
                expected = expected.plusMonths(1);
            }
        }
    }

    @Test void aThreeYearWeeklySequenceKeepsTheLocalTimeAcrossBothDstChanges() {
        var zone = ZoneId.of("Europe/London");
        Instant cursor = Instant.parse("2026-01-01T00:00:00Z");
        for (int i = 0; i < 156; i++) {
            Instant next = MiningScheduleTime.nextWeekly(cursor, zone, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0));
            assertTrue(next.isAfter(cursor));
            assertEquals(LocalTime.of(9, 0), next.atZone(zone).toLocalTime());
            assertEquals(DayOfWeek.WEDNESDAY, next.atZone(zone).getDayOfWeek());
            assertTrue(java.time.temporal.ChronoUnit.DAYS.between(cursor.atZone(zone).toLocalDate(), next.atZone(zone).toLocalDate()) <= 7);
            cursor = next;
        }
    }

    @Test void invalidMonthDaysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MiningScheduleTime.nextMonthly(Instant.now(), HCM, 0, LocalTime.NOON));
        assertThrows(IllegalArgumentException.class, () -> MiningScheduleTime.nextMonthly(Instant.now(), HCM, 32, LocalTime.NOON));
    }
}
