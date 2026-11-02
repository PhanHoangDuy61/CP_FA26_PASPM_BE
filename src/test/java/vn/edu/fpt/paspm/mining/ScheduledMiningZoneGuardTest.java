package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Decision D4: the scheduler never runs under a Store calendar zone other than the approved one. */
class ScheduledMiningZoneGuardTest {
    @Test void theApprovedZoneStartsWithOrWithoutTheDispatcher() {
        assertDoesNotThrow(() -> ScheduledMiningZoneGuard.check(true, "Asia/Ho_Chi_Minh"));
        assertDoesNotThrow(() -> ScheduledMiningZoneGuard.check(false, "Asia/Ho_Chi_Minh"));
    }
    @Test void anotherZoneIsRefusedOnlyWhenTheDispatcherWouldRun() {
        assertDoesNotThrow(() -> ScheduledMiningZoneGuard.check(false, "UTC"), "ordinary environments are unaffected");
        for (String zone : new String[] {"UTC", "Asia/Bangkok", "Asia/Saigon", "Pacific/Auckland"}) {
            var e = assertThrows(IllegalStateException.class, () -> ScheduledMiningZoneGuard.check(true, zone), zone);
            assertTrue(e.getMessage().contains("Asia/Ho_Chi_Minh") && e.getMessage().contains(zone), e.getMessage());
        }
    }
}
