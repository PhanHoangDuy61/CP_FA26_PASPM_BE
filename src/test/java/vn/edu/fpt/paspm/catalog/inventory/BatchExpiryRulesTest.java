package vn.edu.fpt.paspm.catalog.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** DEC-20261124-01 (R1) and DEC-20261124-02 (R2): the single place that decides "expired", effectiveStatus, daysToExpiry and the push-day warning. */
class BatchExpiryRulesTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    @Test void aBatchIsExpiredOnlyWhenTodayIsAfterTheExpiryDate() {
        assertFalse(BatchExpiryRules.isExpired(TODAY.plusDays(1), TODAY));
        assertFalse(BatchExpiryRules.isExpired(TODAY, TODAY), "on the expiry date itself the batch is still active");
        assertTrue(BatchExpiryRules.isExpired(TODAY.minusDays(1), TODAY));
        assertFalse(BatchExpiryRules.isExpired(null, TODAY), "no expiry date never expires");
    }

    @Test void effectiveStatusIsComputedAndExpiredBeatsBlocked() {
        assertEquals("ACTIVE", BatchExpiryRules.effectiveStatus("ACTIVE", TODAY, TODAY));
        assertEquals("ACTIVE", BatchExpiryRules.effectiveStatus("ACTIVE", null, TODAY));
        assertEquals("EXPIRED", BatchExpiryRules.effectiveStatus("ACTIVE", TODAY.minusDays(1), TODAY));
        assertEquals("BLOCKED", BatchExpiryRules.effectiveStatus("BLOCKED", TODAY, TODAY));
        assertEquals("BLOCKED", BatchExpiryRules.effectiveStatus("BLOCKED", null, TODAY));
        assertEquals("EXPIRED", BatchExpiryRules.effectiveStatus("BLOCKED", TODAY.minusDays(1), TODAY));
    }

    @Test void daysToExpiryIsZeroOnTheExpiryDayAndNegativeAfterwards() {
        assertEquals(7, BatchExpiryRules.daysToExpiry(TODAY.plusDays(7), TODAY));
        assertEquals(0, BatchExpiryRules.daysToExpiry(TODAY, TODAY));
        assertEquals(-1, BatchExpiryRules.daysToExpiry(TODAY.minusDays(1), TODAY));
        assertNull(BatchExpiryRules.daysToExpiry(null, TODAY));
    }

    @Test void missingPushDayWarnsOnlyForExpiryTrackedBatchesThatAreNotExpired() {
        assertEquals(List.of("MISSING_PUSH_DAY"), BatchExpiryRules.warnings(true, null, "ACTIVE"));
        assertEquals(List.of("MISSING_PUSH_DAY"), BatchExpiryRules.warnings(true, null, "BLOCKED"),
                "PO rule: regardless of stored status or remaining quantity");
        assertEquals(List.of(), BatchExpiryRules.warnings(true, null, "EXPIRED"));
        assertEquals(List.of(), BatchExpiryRules.warnings(false, null, "ACTIVE"));
        assertEquals(List.of(), BatchExpiryRules.warnings(true, TODAY, "ACTIVE"));
    }
}
