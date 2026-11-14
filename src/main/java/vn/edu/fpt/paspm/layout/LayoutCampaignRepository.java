package vn.edu.fpt.paspm.layout;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.common.ApiException;
import static vn.edu.fpt.paspm.layout.LayoutContracts.*;

@Repository
public class LayoutCampaignRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public LayoutCampaignRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record CampaignRow(long id, int storeId, int snapshotId, String name, CampaignStatus status,
            long version, Long layoutAId, Long layoutBId, Long selectedLayoutId,
            CampaignDecision decision, Long decisionReportId, Instant createdAt, Instant publishedAt,
            Instant activatedAt, Instant endedAt) {}

    public record LayoutRow(long id, int storeId, long campaignId, int snapshotId, LayoutRole role,
            LayoutStatus status, long version, String name, String baselineParams, Instant createdAt, Instant updatedAt) {}

    public record PlacementRow(Integer productId, Long recommendationId, String type, String sku,
            String productName, String zone, int blockNo, int position) {}

    public record ReportRow(long reportId, long campaignId, int snapshotId, LocalDate rangeStart,
            LocalDate rangeEnd, int createdBy, String sourceNamespace, String datasetHash, int evaluationCount,
            boolean simulated, String algorithmVersion, String assumptions, Instant createdAt) {}

    public record ReportLayoutRow(long layoutId, long layoutVersion, LayoutRole role,
            int hitCount, int hitDenominator, int bundleCount, int bundleDenominator,
            BigDecimal utilityNumerator, BigDecimal utilityDenominator) {}

    private static final RowMapper<CampaignRow> CAMPAIGN = (rs, row) -> campaign(rs);
    private static final RowMapper<LayoutRow> LAYOUT = (rs, row) -> layout(rs);
    private static final RowMapper<ReportRow> REPORT = (rs, row) -> report(rs);

    public void lockStore(int storeId) {
        List<Integer> rows = jdbc.query("SELECT store_id FROM store WHERE store_id=? FOR UPDATE",
                (rs, row) -> rs.getInt(1), storeId);
        if (rows.isEmpty()) throw new ApiException(404, "Store not found");
    }

    public long createCampaign(int storeId, int snapshotId, String name, int actorId) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO campaign(store_id,utility_snapshot_id,name,created_by)
                    VALUES (?,?,?,?)
                    """, new String[] {"campaign_id"});
            statement.setInt(1, storeId);
            statement.setInt(2, snapshotId);
            statement.setString(3, name);
            statement.setInt(4, actorId);
            return statement;
        }, key);
        return requiredKey(key.getKey(), "campaign_id");
    }

    public CampaignRow campaign(int storeId, long campaignId, boolean lock) {
        var rows = jdbc.query("SELECT * FROM campaign WHERE store_id=? AND campaign_id=?"
                + (lock ? " FOR UPDATE" : ""), CAMPAIGN, storeId, campaignId);
        if (rows.isEmpty()) throw new ApiException(404, "Campaign not found");
        return rows.get(0);
    }

    public CampaignRow activeCampaign(int storeId, boolean lock) {
        var rows = jdbc.query("SELECT * FROM campaign WHERE store_id=? AND status='ACTIVE' ORDER BY campaign_id"
                + (lock ? " FOR UPDATE" : ""), CAMPAIGN, storeId);
        if (rows.size() > 1) throw new IllegalStateException("Multiple active campaigns violate the Store invariant");
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long insertLayout(int storeId, long campaignId, int snapshotId, LayoutRole role,
            LayoutStatus status, String name, String baselineParams, int actorId) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO storefront_layout(store_id,campaign_id,utility_snapshot_id,role,status,name,baseline_params,created_by)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, new String[] {"layout_id"});
            statement.setInt(1, storeId);
            statement.setLong(2, campaignId);
            statement.setInt(3, snapshotId);
            statement.setString(4, role.name());
            statement.setString(5, status.name());
            statement.setString(6, name);
            statement.setString(7, baselineParams);
            statement.setInt(8, actorId);
            return statement;
        }, key);
        return requiredKey(key.getKey(), "layout_id");
    }

    public void setCampaignLayouts(int storeId, long campaignId, long layoutAId, long layoutBId) {
        int changed = jdbc.update("""
                UPDATE campaign SET layout_a_id=?,layout_b_id=?
                WHERE store_id=? AND campaign_id=? AND layout_a_id IS NULL AND layout_b_id IS NULL
                """, layoutAId, layoutBId, storeId, campaignId);
        if (changed != 1) throw new IllegalStateException("Campaign layouts were not initialized exactly once");
    }

    public LayoutRow layout(int storeId, long layoutId, boolean lock) {
        var rows = jdbc.query("SELECT * FROM storefront_layout WHERE store_id=? AND layout_id=?"
                + (lock ? " FOR UPDATE" : ""), LAYOUT, storeId, layoutId);
        if (rows.isEmpty()) throw new ApiException(404, "Layout not found");
        return rows.get(0);
    }

    public List<LayoutRow> layouts(int storeId, Long campaignId, int page, int pageSize) {
        String filter = campaignId == null ? "" : " AND campaign_id=?";
        Object[] args = campaignId == null
                ? new Object[] {storeId, pageSize, (long) page * pageSize}
                : new Object[] {storeId, campaignId, pageSize, (long) page * pageSize};
        return jdbc.query("SELECT * FROM storefront_layout WHERE store_id=?" + filter
                + " ORDER BY updated_at DESC,layout_id DESC LIMIT ? OFFSET ?", LAYOUT, args);
    }

    public long countLayouts(int storeId, Long campaignId) {
        if (campaignId == null) return jdbc.queryForObject(
                "SELECT COUNT(*) FROM storefront_layout WHERE store_id=?", Long.class, storeId);
        return jdbc.queryForObject("SELECT COUNT(*) FROM storefront_layout WHERE store_id=? AND campaign_id=?",
                Long.class, storeId, campaignId);
    }

    public List<PlacementRow> placements(int storeId, long layoutId) {
        return jdbc.query("""
                SELECT p.product_id,p.recommendation_id,r.type,p.zone,p.block_no,p.position,
                       c.sku,c.name AS product_name
                FROM layout_placement p
                JOIN product c ON c.store_id=p.store_id AND c.product_id=p.product_id
                LEFT JOIN recommendation r ON r.store_id=p.store_id AND r.recommendation_id=p.recommendation_id
                WHERE p.store_id=? AND p.layout_id=?
                ORDER BY p.zone,p.block_no,p.position,p.product_id
                """, (rs, row) -> new PlacementRow(rs.getInt("product_id"),
                nullableLong(rs, "recommendation_id"), rs.getString("type"), rs.getString("sku"),
                rs.getString("product_name"), rs.getString("zone"), rs.getInt("block_no"),
                rs.getInt("position")), storeId, layoutId);
    }

    public void replacePlacements(int storeId, long layoutId, int snapshotId, List<PlacementBlock> blocks) {
        jdbc.update("DELETE FROM layout_placement WHERE store_id=? AND layout_id=?", storeId, layoutId);
        var rows = new ArrayList<Object[]>();
        for (PlacementBlock block : blocks) {
            for (int i = 0; i < block.productIds().size(); i++) {
                rows.add(new Object[] {storeId, layoutId, snapshotId, block.recommendationId(),
                        block.productIds().get(i), block.zone(), block.blockNo(), block.startPosition() + i});
            }
        }
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO layout_placement(store_id,layout_id,utility_snapshot_id,recommendation_id,
                        product_id,zone,block_no,position)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, rows);
        }
    }

    public void insertBaselinePlacements(int storeId, long layoutId, int snapshotId, List<Integer> productIds) {
        var rows = new ArrayList<Object[]>();
        for (int i = 0; i < productIds.size(); i++) {
            int position = i + 1;
            rows.add(new Object[] {storeId, layoutId, snapshotId, productIds.get(i), "BASELINE", position, position});
        }
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO layout_placement(store_id,layout_id,utility_snapshot_id,recommendation_id,
                        product_id,zone,block_no,position)
                    VALUES (?,?,?,NULL,?,?,?,?)
                    """, rows);
        }
    }

    public void saveRevision(int storeId, long layoutId, long version,
            List<PlacementRow> placements, int actorId, String contentHash) {
        String payload = writeJson(placements);
        jdbc.update("""
                INSERT INTO storefront_layout_revision(store_id,layout_id,version,placements_json,
                    content_sha256,created_by)
                VALUES (?,?,?,?,?,?)
                """, storeId, layoutId, version, payload, contentHash, actorId);
    }

    public List<Integer> baselineProducts(int storeId, int snapshotId) {
        return jdbc.query("""
                SELECT m.product_id
                FROM utility_matrix_item m
                JOIN utility_snapshot s ON s.snapshot_id=m.snapshot_id AND s.store_id=?
                WHERE m.snapshot_id=?
                GROUP BY m.product_id
                ORDER BY COUNT(DISTINCT m.transaction_id) DESC,m.product_id ASC
                """, (rs, row) -> rs.getInt(1), storeId, snapshotId);
    }

    public Map<Integer, BigDecimal> weightedUtilityByProduct(int snapshotId) {
        var map = new LinkedHashMap<Integer, BigDecimal>();
        jdbc.query("""
                SELECT product_id,SUM(weighted_utility) AS utility
                FROM utility_matrix_item WHERE snapshot_id=? GROUP BY product_id
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                map.put(rs.getInt("product_id"), rs.getBigDecimal("utility")), snapshotId);
        return map;
    }

    public void updateLayoutState(int storeId, long layoutId, LayoutStatus status) {
        jdbc.update("UPDATE storefront_layout SET status=?,updated_at=CURRENT_TIMESTAMP WHERE store_id=? AND layout_id=?",
                status.name(), storeId, layoutId);
    }

    public void updateLayoutVersion(int storeId, long layoutId, long expectedVersion) {
        int changed = jdbc.update("""
                UPDATE storefront_layout SET version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE store_id=? AND layout_id=? AND version=?
                """, storeId, layoutId, expectedVersion);
        if (changed != 1) throw new ApiException(409, "Layout version is stale");
    }

    public void updateCampaignDecision(int storeId, long campaignId, long expectedVersion,
            CampaignDecision decision, Long selectedLayoutId, Long reportId) {
        int changed = jdbc.update("""
                UPDATE campaign SET decision=?,selected_layout_id=?,decision_report_id=?,version=version+1
                WHERE store_id=? AND campaign_id=? AND version=? AND status='DRAFT'
                """, decision.name(), selectedLayoutId, reportId, storeId, campaignId, expectedVersion);
        if (changed != 1) throw new ApiException(409, "Campaign state or version changed");
    }

    public void clearCampaignDecision(int storeId, long campaignId) {
        jdbc.update("""
                UPDATE campaign SET decision=NULL,selected_layout_id=NULL,decision_report_id=NULL,version=version+1
                WHERE store_id=? AND campaign_id=? AND status='DRAFT' AND decision IS NOT NULL
                """, storeId, campaignId);
    }

    public void withdrawCampaign(int storeId, long campaignId, long expectedVersion) {
        int changed = jdbc.update("""
                UPDATE campaign
                SET status='DRAFT',version=version+1,decision=NULL,selected_layout_id=NULL,decision_report_id=NULL
                WHERE store_id=? AND campaign_id=? AND version=? AND status='PUBLISHED'
                """, storeId, campaignId, expectedVersion);
        if (changed != 1) throw new ApiException(409, "Campaign state or version changed");
    }

    public void transitionCampaign(int storeId, long campaignId, long expectedVersion,
            CampaignStatus from, CampaignStatus to, int actorId) {
        String auditColumns = switch (to) {
            case PUBLISHED -> ",published_by=?,published_at=CURRENT_TIMESTAMP";
            case ACTIVE -> ",activated_by=?,activated_at=CURRENT_TIMESTAMP,ended_by=NULL,ended_at=NULL";
            case ENDED -> ",ended_by=?,ended_at=CURRENT_TIMESTAMP";
            case DRAFT -> "";
        };
        Object[] args = auditColumns.isEmpty()
                ? new Object[] {to.name(), storeId, campaignId, expectedVersion, from.name()}
                : new Object[] {to.name(), actorId, storeId, campaignId, expectedVersion, from.name()};
        int changed = jdbc.update("UPDATE campaign SET status=?,version=version+1" + auditColumns
                + " WHERE store_id=? AND campaign_id=? AND version=? AND status=?", args);
        if (changed != 1) throw new ApiException(409, "Campaign state or version changed");
    }

    public void history(int storeId, long campaignId, String oldStatus, CampaignStatus newStatus,
            int actorId, Long previousActiveId, Object validation) {
        jdbc.update("""
                INSERT INTO campaign_history(store_id,campaign_id,old_status,new_status,actor_id,
                    previous_active_campaign_id,validation_result)
                VALUES (?,?,?,?,?,?,?)
                """, storeId, campaignId, oldStatus, newStatus.name(), actorId, previousActiveId,
                validation == null ? null : writeJson(validation));
    }

    public Long previousActiveCampaign(int storeId, long campaignId) {
        return jdbc.query("""
                SELECT previous_active_campaign_id FROM campaign_history
                WHERE store_id=? AND campaign_id=? AND new_status='ACTIVE'
                ORDER BY occurred_at DESC,history_id DESC LIMIT 1
                """, rs -> rs.next() ? nullableLong(rs, 1) : null, storeId, campaignId);
    }

    public long countCampaigns(int storeId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM campaign WHERE store_id=?", Long.class, storeId);
    }

    public List<CampaignRow> campaigns(int storeId, int page, int pageSize) {
        return jdbc.query("""
                SELECT * FROM campaign WHERE store_id=?
                ORDER BY created_at DESC,campaign_id DESC LIMIT ? OFFSET ?
                """, CAMPAIGN, storeId, pageSize, (long) page * pageSize);
    }

    public List<Long> campaignReportIds(int storeId, long campaignId) {
        return jdbc.query("""
                SELECT report_id FROM comparison_report
                WHERE store_id=? AND campaign_id=? ORDER BY created_at DESC,report_id DESC
                """, (rs, row) -> rs.getLong(1), storeId, campaignId);
    }

    public long createReport(int storeId, long campaignId, LocalDate rangeStart, LocalDate rangeEnd,
            String datasetHash, String sourceNamespace, int evaluationCount, String algorithmVersion,
            Object assumptions, int actorId) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO comparison_report(store_id,campaign_id,test_range_start,test_range_end,
                        dataset_hash,source_namespace,evaluation_count,algorithm_version,assumptions,simulated,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,TRUE,?)
                    """, new String[] {"report_id"});
            statement.setInt(1, storeId);
            statement.setLong(2, campaignId);
            statement.setDate(3, java.sql.Date.valueOf(rangeStart));
            statement.setDate(4, java.sql.Date.valueOf(rangeEnd));
            statement.setString(5, datasetHash);
            statement.setString(6, sourceNamespace);
            statement.setInt(7, evaluationCount);
            statement.setString(8, algorithmVersion);
            statement.setString(9, writeJson(assumptions));
            statement.setInt(10, actorId);
            return statement;
        }, key);
        return requiredKey(key.getKey(), "report_id");
    }

    public void insertReportLayout(long reportId, int storeId, long layoutId, long layoutVersion,
            LayoutRole role, int hitCount, int hitDenominator, int bundleCount, int bundleDenominator,
            BigDecimal utilityNumerator, BigDecimal utilityDenominator) {
        jdbc.update("""
                INSERT INTO comparison_report_layout(report_id,store_id,layout_id,layout_version,role,
                    hit_count,hit_denominator,bundle_conversion_count,bundle_conversion_denominator,
                    captured_utility,captured_utility_denominator,priority_coverage,priority_coverage_denominator)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,NULL,NULL)
                """, reportId, storeId, layoutId, layoutVersion, role.name(), hitCount, hitDenominator,
                bundleCount, bundleDenominator, utilityNumerator, utilityDenominator);
    }

    public void insertReportTransactions(long reportId, int storeId, List<Integer> transactionIds) {
        if (transactionIds.isEmpty()) return;
        var rows = transactionIds.stream().map(id -> new Object[] {storeId, reportId, id}).toList();
        jdbc.batchUpdate("INSERT INTO comparison_report_transaction(store_id,report_id,transaction_id) VALUES (?,?,?)", rows);
    }

    public ReportRow report(int storeId, long reportId) {
        var rows = jdbc.query("SELECT r.*,c.utility_snapshot_id FROM comparison_report r "
                + "JOIN campaign c ON c.store_id=r.store_id AND c.campaign_id=r.campaign_id "
                + "WHERE r.store_id=? AND r.report_id=?", REPORT, storeId, reportId);
        if (rows.isEmpty()) throw new ApiException(404, "Campaign report not found");
        return rows.get(0);
    }

    public List<ReportLayoutRow> reportLayouts(int storeId, long reportId) {
        return jdbc.query("""
                SELECT layout_id,layout_version,role,hit_count,hit_denominator,
                    bundle_conversion_count,bundle_conversion_denominator,
                    captured_utility,captured_utility_denominator
                FROM comparison_report_layout WHERE store_id=? AND report_id=? ORDER BY role
                """, (rs, row) -> new ReportLayoutRow(rs.getLong("layout_id"), rs.getLong("layout_version"),
                LayoutRole.valueOf(rs.getString("role")), rs.getInt("hit_count"), rs.getInt("hit_denominator"),
                rs.getInt("bundle_conversion_count"), rs.getInt("bundle_conversion_denominator"),
                rs.getBigDecimal("captured_utility"), rs.getBigDecimal("captured_utility_denominator")),
                storeId, reportId);
    }

    public List<ReportRow> reports(int storeId, long campaignId, int page, int pageSize) {
        return jdbc.query("""
                SELECT r.*,c.utility_snapshot_id FROM comparison_report r
                JOIN campaign c ON c.store_id=r.store_id AND c.campaign_id=r.campaign_id
                WHERE r.store_id=? AND r.campaign_id=?
                ORDER BY r.created_at DESC,r.report_id DESC LIMIT ? OFFSET ?
                """, REPORT, storeId, campaignId, pageSize, (long) page * pageSize);
    }

    public long countReports(int storeId, long campaignId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM comparison_report WHERE store_id=? AND campaign_id=?",
                Long.class, storeId, campaignId);
    }

    public List<ReportLayoutRow> reportLayoutsForCampaign(int storeId, long campaignId, long reportId) {
        report(storeId, reportId);
        var count = jdbc.queryForObject("SELECT COUNT(*) FROM comparison_report WHERE store_id=? AND campaign_id=? AND report_id=?",
                Integer.class, storeId, campaignId, reportId);
        if (count == null || count != 1) throw new ApiException(404, "Campaign report not found");
        return reportLayouts(storeId, reportId);
    }

    public long reportLayoutVersion(int storeId, long reportId, long layoutId, LayoutRole role) {
        var rows = jdbc.query("""
                SELECT layout_version FROM comparison_report_layout
                WHERE store_id=? AND report_id=? AND layout_id=? AND role=?
                """, (rs, row) -> rs.getLong(1), storeId, reportId, layoutId, role.name());
        if (rows.isEmpty()) throw new ApiException(404, "Campaign report layout not found");
        return rows.get(0);
    }

    public long totalReportsInCampaign(int storeId, long campaignId, long reportId) {
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM comparison_report WHERE store_id=? AND campaign_id=? AND report_id=?
                """, Long.class, storeId, campaignId, reportId);
        return count == null ? 0 : count;
    }

    public void evaluationRows(int storeId, int snapshotId, Timestamp from, Timestamp to,
            RowCallbackHandler handler) {
        jdbc.query("""
                SELECT t.transaction_id,t.occurred_at,i.product_id,i.quantity,i.unit_price,i.unit_cost
                FROM sales_transaction t
                JOIN transaction_item i ON i.store_id=t.store_id AND i.transaction_id=t.transaction_id
                WHERE t.store_id=? AND t.status='COMPLETED' AND t.f03_eligible=TRUE
                  AND t.occurred_at>=? AND t.occurred_at<?
                  AND NOT EXISTS (
                      SELECT 1 FROM utility_matrix_item m
                      WHERE m.snapshot_id=? AND m.transaction_id=t.transaction_id
                  )
                ORDER BY t.transaction_id,i.product_id
                """, handler, storeId, from, to, snapshotId);
    }

    public void insertEvaluationTransactionIds(long reportId, int storeId, int snapshotId,
            Timestamp from, Timestamp to) {
        jdbc.update("""
                INSERT INTO comparison_report_transaction(store_id,report_id,transaction_id)
                SELECT t.store_id,?,t.transaction_id
                FROM sales_transaction t
                WHERE t.store_id=? AND t.status='COMPLETED' AND t.f03_eligible=TRUE
                  AND t.occurred_at>=? AND t.occurred_at<?
                  AND EXISTS (SELECT 1 FROM transaction_item i
                              WHERE i.store_id=t.store_id AND i.transaction_id=t.transaction_id)
                  AND NOT EXISTS (
                      SELECT 1 FROM utility_matrix_item m
                      WHERE m.snapshot_id=? AND m.transaction_id=t.transaction_id
                  )
                """, reportId, storeId, from, to, snapshotId);
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize layout or report evidence", e);
        }
    }

    private static long requiredKey(Number key, String name) {
        if (key == null) throw new IllegalStateException("Database did not return generated " + name);
        return key.longValue();
    }

    private static CampaignRow campaign(ResultSet rs) throws SQLException {
        return new CampaignRow(rs.getLong("campaign_id"), rs.getInt("store_id"), rs.getInt("utility_snapshot_id"),
                rs.getString("name"), CampaignStatus.valueOf(rs.getString("status")), rs.getLong("version"),
                nullableLong(rs, "layout_a_id"), nullableLong(rs, "layout_b_id"),
                nullableLong(rs, "selected_layout_id"), rs.getString("decision") == null
                        ? null : CampaignDecision.valueOf(rs.getString("decision")),
                nullableLong(rs, "decision_report_id"),
                instant(rs, "created_at"), instant(rs, "published_at"), instant(rs, "activated_at"),
                instant(rs, "ended_at"));
    }

    private static LayoutRow layout(ResultSet rs) throws SQLException {
        return new LayoutRow(rs.getLong("layout_id"), rs.getInt("store_id"), rs.getLong("campaign_id"),
                rs.getInt("utility_snapshot_id"), LayoutRole.valueOf(rs.getString("role")),
                LayoutStatus.valueOf(rs.getString("status")), rs.getLong("version"), rs.getString("name"),
                rs.getString("baseline_params"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static ReportRow report(ResultSet rs) throws SQLException {
        return new ReportRow(rs.getLong("report_id"), rs.getLong("campaign_id"),
                rs.getInt("utility_snapshot_id"), rs.getDate("test_range_start").toLocalDate(),
                rs.getDate("test_range_end").toLocalDate(), rs.getInt("created_by"), rs.getString("source_namespace"),
                rs.getString("dataset_hash"), rs.getInt("evaluation_count"), rs.getBoolean("simulated"),
                rs.getString("algorithm_version"), rs.getString("assumptions"), instant(rs, "created_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
