package vn.edu.fpt.paspm.catalog.inventory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The single definition of "expired" for inventory batches (DEC-20261124-01).
 *
 * <p>A batch is expired only when the Store-local date is AFTER its expiry date, so on the expiry date itself it is
 * still sellable. Expiry is never stored: {@code inventory_batch.status} stays {@code ACTIVE|BLOCKED} and
 * {@code effectiveStatus} is computed on read. F02 summary, F03 FEFO/push-day rule and F06 eligibility all call this.
 */
public final class BatchExpiryRules {
    public static final String ACTIVE = "ACTIVE";
    public static final String BLOCKED = "BLOCKED";
    public static final String EXPIRED = "EXPIRED";
    /** Same code F03 uses for a batch it cannot push because it has no push day. */
    public static final String MISSING_PUSH_DAY = "MISSING_PUSH_DAY";

    private BatchExpiryRules() {}

    public static boolean isExpired(LocalDate expiryDate, LocalDate today) {
        return expiryDate != null && today.isAfter(expiryDate);
    }

    /** EXPIRED wins over BLOCKED; otherwise the stored status. */
    public static String effectiveStatus(String storedStatus, LocalDate expiryDate, LocalDate today) {
        return isExpired(expiryDate, today) ? EXPIRED : storedStatus;
    }

    /** Whole days from today to the expiry date: 0 on the expiry day, negative once expired, null without a date. */
    public static Integer daysToExpiry(LocalDate expiryDate, LocalDate today) {
        if (expiryDate == null) return null;
        return Math.toIntExact(ChronoUnit.DAYS.between(today, expiryDate));
    }

    /**
     * DEC-20261124-02: warn when an expiry-tracked product's batch has no push day and is not expired. The stored
     * status and the remaining quantity do not matter here (F03 keeps its own, narrower snapshot conditions).
     */
    public static List<String> warnings(boolean requiresExpiry, LocalDate pushDay, String effectiveStatus) {
        return requiresExpiry && pushDay == null && !EXPIRED.equals(effectiveStatus)
                ? List.of(MISSING_PUSH_DAY) : List.of();
    }
}
