package vn.edu.fpt.paspm.dashboard;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.dashboard.DashboardContracts.SnapshotUtility;

@Repository
class DashboardRepository {
    private static final String READY_SNAPSHOTS = " FROM utility_snapshot WHERE store_id=? AND status='READY'";

    record BundleBucket(int dayIndex, String origin, long actions, long units) {}

    private final JdbcTemplate jdbc;

    DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    long countReadySnapshots(int store, Integer snapshotId) {
        return snapshotId == null
                ? jdbc.queryForObject("SELECT COUNT(*)" + READY_SNAPSHOTS, Long.class, store)
                : jdbc.queryForObject("SELECT COUNT(*)" + READY_SNAPSHOTS + " AND snapshot_id=?",
                        Long.class, store, snapshotId);
    }

    List<SnapshotUtility> snapshotUtilities(int store, Integer snapshotId, int page, int size) {
        long offset = (long) page * size;
        Object[] parameters = snapshotId == null ? new Object[]{store, size, offset}
                : new Object[]{store, snapshotId, size, offset};
        return jdbc.query("""
                SELECT snapshot_id,range_start,range_end,as_of_date,created_at,total_weighted_utility
                """ + READY_SNAPSHOTS + (snapshotId == null ? "" : " AND snapshot_id=?")
                + " ORDER BY created_at DESC,snapshot_id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new SnapshotUtility(rs.getInt("snapshot_id"), rs.getObject("range_start", LocalDate.class),
                        rs.getObject("range_end", LocalDate.class), rs.getObject("as_of_date", LocalDate.class),
                        rs.getTimestamp("created_at").toInstant(), rs.getBigDecimal("total_weighted_utility")),
                parameters);
    }

    List<BundleBucket> bundleBuckets(int store, Long campaignId, List<LocalDate> days, ZoneId zone) {
        // One indexed UTC range per day. Build the boundaries in Java so SQL needs no timezone tables.
        var sql = new StringJoiner(" UNION ALL ");
        var parameters = new ArrayList<>();
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            sql.add("SELECT " + dayIndex + " AS day_index,bundle_origin,COUNT(*) AS actions,"
                    + "SUM(added_product_units) AS units"
                    + " FROM storefront_event WHERE store_id=? AND event_type='BUNDLE_ADDED'"
                    + " AND occurred_at>=? AND occurred_at<? AND bundle_origin IN ('MINED','MANUAL')"
                    + (campaignId == null ? "" : " AND campaign_id=?") + " GROUP BY bundle_origin");
            LocalDate day = days.get(dayIndex);
            parameters.add(store);
            parameters.add(Timestamp.from(day.atStartOfDay(zone).toInstant()));
            parameters.add(Timestamp.from(day.plusDays(1).atStartOfDay(zone).toInstant()));
            if (campaignId != null) parameters.add(campaignId);
        }
        return jdbc.query(sql.toString(), (rs, row) -> new BundleBucket(rs.getInt("day_index"),
                rs.getString("bundle_origin"), rs.getLong("actions"), rs.getLong("units")), parameters.toArray());
    }
}
