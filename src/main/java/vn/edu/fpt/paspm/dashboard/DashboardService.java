package vn.edu.fpt.paspm.dashboard;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.dashboard.DashboardContracts.BundleAdditionDay;
import vn.edu.fpt.paspm.dashboard.DashboardContracts.BundleAdditions;
import vn.edu.fpt.paspm.dashboard.DashboardContracts.BundleOriginCounts;
import vn.edu.fpt.paspm.dashboard.DashboardContracts.SnapshotUtilityPage;
import vn.edu.fpt.paspm.layout.LayoutCampaignRepository;
import vn.edu.fpt.paspm.utility.UtilityTimeZone;

@Service
public class DashboardService {
    private static final int DEFAULT_WINDOW_DAYS = 30;
    private static final int MAX_WINDOW_DAYS = 90;

    private final DashboardRepository dashboard;
    private final CatalogAccess access;
    private final LayoutCampaignRepository campaigns;
    private final UtilityTimeZone timeZone;

    DashboardService(DashboardRepository dashboard, CatalogAccess access,
            LayoutCampaignRepository campaigns, UtilityTimeZone timeZone) {
        this.dashboard = dashboard;
        this.access = access;
        this.campaigns = campaigns;
        this.timeZone = timeZone;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SnapshotUtilityPage snapshotUtilities(Jwt jwt, int page, int pageSize, Integer snapshotId) {
        int store = access.currentStoreId(jwt);
        long count = dashboard.countReadySnapshots(store, snapshotId);
        if (snapshotId != null && count == 0) throw new ApiException(404, "Snapshot not found");
        return new SnapshotUtilityPage(dashboard.snapshotUtilities(store, snapshotId, page, pageSize),
                page, pageSize, count);
    }

    @Transactional(readOnly = true)
    public BundleAdditions bundleAdditions(Jwt jwt, String from, String to, Long campaignId) {
        int store = access.currentStoreId(jwt);
        DateWindow window = resolveWindow(from, to);
        if (campaignId != null) campaigns.campaign(store, campaignId, false);
        List<LocalDate> dates = window.from().datesUntil(window.to().plusDays(1)).toList();

        long[] mined = new long[dates.size()];
        long[] manual = new long[dates.size()];
        long[] units = new long[dates.size()];
        for (var bucket : dashboard.bundleBuckets(store, campaignId, dates, timeZone.zone())) {
            int day = bucket.dayIndex();
            if ("MINED".equals(bucket.origin())) {
                mined[day] = bucket.actions();
            } else {
                manual[day] = bucket.actions();
            }
            units[day] += bucket.units();
        }

        var days = new ArrayList<BundleAdditionDay>(dates.size());
        long totalMined = 0, totalManual = 0, totalUnits = 0;
        for (int day = 0; day < dates.size(); day++) {
            days.add(new BundleAdditionDay(dates.get(day), new BundleOriginCounts(mined[day], manual[day]), units[day]));
            totalMined += mined[day];
            totalManual += manual[day];
            totalUnits += units[day];
        }
        return new BundleAdditions(window.from(), window.to(), timeZone.id(), campaignId, totalMined + totalManual,
                new BundleOriginCounts(totalMined, totalManual), totalUnits, List.copyOf(days));
    }

    private DateWindow resolveWindow(String from, String to) {
        if (from == null && to == null) {
            LocalDate today = timeZone.today();
            return new DateWindow(today.minusDays(DEFAULT_WINDOW_DAYS - 1), today);
        }
        if (from == null || to == null) throw new ApiException(400, "Supply both from and to");
        try {
            LocalDate start = LocalDate.parse(from);
            LocalDate end = LocalDate.parse(to);
            long days = ChronoUnit.DAYS.between(start, end) + 1;
            if (days < 1 || days > MAX_WINDOW_DAYS) {
                throw new ApiException(400, "Date window must contain 1 to 90 inclusive days");
            }
            return new DateWindow(start, end);
        } catch (DateTimeParseException exception) {
            throw new ApiException(400, "from and to must be valid ISO dates");
        }
    }

    private record DateWindow(LocalDate from, LocalDate to) {}
}
