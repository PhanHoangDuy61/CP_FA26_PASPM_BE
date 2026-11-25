package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * DEC-20261124-01: a batch is expired only when {@code asOfDate} is AFTER its expiry date. On the expiry date itself it is
 * still a FEFO/push-day candidate; F03 and the F06 lists share this boundary. Frozen snapshots are never recomputed.
 */
class ExpiryBoundaryF03F06Test {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 24);
    private final ExpiryRiskCalculator risk = new ExpiryRiskCalculator();

    private static ExpiryRiskCalculator.Batch batch(int id, LocalDate pushDay, LocalDate expiry, long quantity) {
        return new ExpiryRiskCalculator.Batch(id, pushDay, expiry, quantity, "ACTIVE");
    }

    @Test void f03TreatsTheBatchExpiringTodayAsEligibleWithItsWholeQuantityAtRisk() {
        var result = risk.calculate(true, List.of(batch(1, DAY.minusDays(3), DAY, 10)), DAY, 30, 30);
        assertEquals(10, result.evidence().eligibleQuantity(), "expiry date itself is still sellable");
        var row = result.evidence().batches().get(0);
        assertTrue(row.eligible());
        assertEquals(List.of("PUSH_DAY_REACHED"), row.warnings());
        assertEquals(0, result.score().compareTo(java.math.BigDecimal.ONE), "push day reached: the whole batch is at risk");
    }

    @Test void f03ExcludesOnlyBatchesWhoseExpiryDateIsBeforeTheAnalysisDate() {
        var result = risk.calculate(true, List.of(batch(1, DAY.minusDays(5), DAY.minusDays(1), 10)), DAY, 30, 30);
        assertEquals(0, result.evidence().eligibleQuantity());
        var row = result.evidence().batches().get(0);
        assertFalse(row.eligible());
        assertEquals(List.of(ExpiryRiskCalculator.EXPIRED), row.warnings());
    }

    @Test void f03BatchExpiringTodayWithoutPushDayOnlyWarns() {
        var result = risk.calculate(true, List.of(batch(1, null, DAY, 10)), DAY, 30, 30);
        assertEquals(List.of(1), result.batchesMissingPushDay());
    }

    @Test void expiryPushListKeepsTheBatchThatExpiresTodayAndDropsYesterdays() {
        var result = PushDayRule.evaluate(7, List.of(
                batch(1, DAY.minusDays(2), DAY, 4),
                batch(2, DAY.minusDays(9), DAY.minusDays(1), 4)), DAY);
        assertEquals(List.of(1), result.due().stream().map(PushDayRule.DueBatch::batchId).toList());
    }
}
