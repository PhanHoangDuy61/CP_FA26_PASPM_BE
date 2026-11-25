package vn.edu.fpt.paspm.storefront;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.common.ApiException;

@Repository
public class StorefrontRepository {
    private static final int ID_CHUNK_SIZE = 500;
    private final JdbcTemplate jdbc;

    public StorefrontRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record StoreRow(int id, String slug, String name) {}
    public record ProductRow(int id, String name, String description, String imageUrl, String unit,
            BigDecimal sellingPrice, Integer categoryId, String categoryName,
            boolean requiresExpiry, String status) {}
    public record AvailabilityRow(boolean productActive, long sellableQuantity, boolean missingRequiredExpiry) {}
    public record ProductResults(List<ProductRow> items, long totalElements) {}
    public record ActiveLayout(String campaignName, int snapshotId, long layoutId, String role, String status) {}
    public record PlacementRow(int productId, Long recommendationId, String recommendationType,
            Integer recommendationSnapshotId, String recommendationStatus, String recommendationPolicy,
            String zone, int blockNo, int position, String name, String description, String imageUrl,
            String unit, BigDecimal sellingPrice, Integer categoryId, String categoryName,
            boolean requiresExpiry, String productStatus) {}
    public record RecommendationRow(long id, int snapshotId, String type, String status, String policy) {}

    private static final String PRODUCT_SELECT = """
            SELECT p.product_id,p.name,p.description,p.image_url,p.unit,p.selling_price,
                   p.category_id,c.name AS category_name,p.requires_expiry,p.status
            FROM product p
            LEFT JOIN category c ON c.store_id=p.store_id AND c.category_id=p.category_id
            """;

    public List<StoreRow> publicStores() {
        return jdbc.query("""
                SELECT store_id,store_slug,name FROM store
                WHERE storefront_enabled=TRUE AND store_slug IS NOT NULL
                ORDER BY name,store_slug
                """, (rs, row) -> new StoreRow(rs.getInt("store_id"), rs.getString("store_slug"),
                rs.getString("name")));
    }

    public StoreRow publicStore(String slug) {
        return jdbc.query("""
                SELECT store_id,store_slug,name FROM store
                WHERE store_slug=? AND storefront_enabled=TRUE
                """, (rs, row) -> new StoreRow(rs.getInt("store_id"), rs.getString("store_slug"),
                rs.getString("name")), slug).stream().findFirst().orElse(null);
    }

    public VisibilityViewState setVisibility(int storeId, boolean enabled) {
        List<StoreRow> stores = jdbc.query("SELECT store_id,store_slug,name FROM store WHERE store_id=?",
                (rs, row) -> new StoreRow(rs.getInt("store_id"), rs.getString("store_slug"),
                        rs.getString("name")), storeId);
        if (stores.isEmpty()) throw new ApiException(404, "Store not found");
        StoreRow store = stores.get(0);
        String slug = store.slug();
        if (slug == null) {
            slug = StoreSlug.generate(store.name(), storeId);
            jdbc.update("UPDATE store SET store_slug=? WHERE store_id=? AND store_slug IS NULL", slug, storeId);
        }
        jdbc.update("UPDATE store SET storefront_enabled=? WHERE store_id=?", enabled, storeId);
        return new VisibilityViewState(storeId, slug, enabled);
    }

    public record VisibilityViewState(int storeId, String storeSlug, boolean storefrontEnabled) {}

    public boolean categoryExists(int storeId, int categoryId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE store_id=? AND category_id=?",
                Integer.class, storeId, categoryId);
        return count != null && count == 1;
    }

    public ProductResults products(int storeId, String search, Integer categoryId,
            String sortColumn, String direction, int page, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE p.store_id=? AND p.status='ACTIVE'");
        List<Object> parameters = new ArrayList<>();
        parameters.add(storeId);
        if (!search.isBlank()) {
            where.append(" AND (LOWER(p.name) LIKE ? OR LOWER(COALESCE(p.description,'')) LIKE ?)");
            String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (categoryId != null) {
            where.append(" AND p.category_id=?");
            parameters.add(categoryId);
        }
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM product p" + where, Long.class, parameters.toArray());
        parameters.add(pageSize);
        parameters.add((long) page * pageSize);
        List<ProductRow> items = jdbc.query(PRODUCT_SELECT + where + " ORDER BY " + sortColumn + " "
                + direction + ",p.product_id ASC LIMIT ? OFFSET ?", productMapper(), parameters.toArray());
        return new ProductResults(items, count == null ? 0 : count);
    }

    public ProductRow activeProduct(int storeId, int productId) {
        return jdbc.query(PRODUCT_SELECT + " WHERE p.store_id=? AND p.product_id=? AND p.status='ACTIVE'",
                productMapper(), storeId, productId).stream().findFirst().orElse(null);
    }

    public List<ProductRow> activeProductsByCategory(int storeId) {
        return jdbc.query(PRODUCT_SELECT + """
                WHERE p.store_id=? AND p.status='ACTIVE'
                ORDER BY c.name IS NULL,c.name,c.category_id,p.name,p.product_id
                """, productMapper(), storeId);
    }

    public Map<Integer, AvailabilityRow> availability(int storeId, List<Integer> productIds, LocalDate today) {
        if (productIds.isEmpty()) return Map.of();
        var result = new HashMap<Integer, AvailabilityRow>();
        List<Integer> ids = new TreeSet<>(productIds).stream().toList();
        for (int start = 0; start < ids.size(); start += ID_CHUNK_SIZE) {
            List<Integer> chunk = ids.subList(start, Math.min(start + ID_CHUNK_SIZE, ids.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(chunk.size(), "?"));
            List<Object> args = new ArrayList<>();
            args.add(today);
            args.add(storeId);
            args.addAll(chunk);
            jdbc.query("""
                    SELECT p.product_id,p.status,p.requires_expiry,
                           COALESCE(SUM(CASE WHEN b.status='ACTIVE' AND b.remaining_quantity>0
                               AND (b.expiry_date IS NULL OR b.expiry_date>?)
                               AND (p.requires_expiry=FALSE OR b.expiry_date IS NOT NULL)
                               THEN b.remaining_quantity ELSE 0 END),0) AS sellable_quantity,
                           CASE WHEN p.requires_expiry=TRUE THEN
                               MAX(CASE WHEN b.status='ACTIVE' AND b.remaining_quantity>0
                                        AND b.expiry_date IS NULL THEN 1 ELSE 0 END)
                               ELSE 0 END AS missing_required_expiry
                    FROM product p
                    LEFT JOIN inventory_batch b ON b.product_id=p.product_id
                    WHERE p.store_id=? AND p.product_id IN (
                    """ + placeholders + ") GROUP BY p.product_id,p.status,p.requires_expiry",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(rs.getInt("product_id"), new AvailabilityRow(
                            "ACTIVE".equals(rs.getString("status")), rs.getLong("sellable_quantity"),
                            rs.getBoolean("missing_required_expiry"))), args.toArray());
        }
        return Map.copyOf(result);
    }

    public ActiveLayout activeLayout(int storeId) {
        List<ActiveLayout> layouts = jdbc.query("""
                SELECT c.name AS campaign_name,c.utility_snapshot_id,l.layout_id,l.role,l.status
                FROM campaign c
                LEFT JOIN storefront_layout l
                  ON l.store_id=c.store_id AND l.layout_id=c.selected_layout_id
                WHERE c.store_id=? AND c.status='ACTIVE'
                ORDER BY c.campaign_id
                """, (rs, row) -> {
            long layoutId = rs.getLong("layout_id");
            if (rs.wasNull()) return new ActiveLayout(rs.getString("campaign_name"),
                    rs.getInt("utility_snapshot_id"), 0, null, null);
            return new ActiveLayout(rs.getString("campaign_name"), rs.getInt("utility_snapshot_id"),
                    layoutId, rs.getString("role"), rs.getString("status"));
        }, storeId);
        if (layouts.size() > 1) throw new IllegalStateException("Multiple active campaigns violate the Store invariant");
        if (layouts.isEmpty()) return null;
        ActiveLayout active = layouts.get(0);
        if (active.layoutId() <= 0) throw new IllegalStateException("Active campaign has no selected layout");
        return active;
    }

    public List<PlacementRow> placements(int storeId, long layoutId) {
        return jdbc.query("""
                SELECT p.product_id,p.recommendation_id,r.type AS recommendation_type,
                       r.utility_snapshot_id AS recommendation_snapshot_id,r.review_status AS recommendation_status,
                       r.policy_snapshot AS recommendation_policy,p.zone,p.block_no,p.position,
                       pr.name,pr.description,pr.image_url,pr.unit,pr.selling_price,pr.category_id,
                       c.name AS category_name,pr.requires_expiry,pr.status AS product_status
                FROM layout_placement p
                JOIN product pr ON pr.store_id=p.store_id AND pr.product_id=p.product_id
                LEFT JOIN category c ON c.store_id=pr.store_id AND c.category_id=pr.category_id
                LEFT JOIN recommendation r ON r.store_id=p.store_id AND r.recommendation_id=p.recommendation_id
                WHERE p.store_id=? AND p.layout_id=?
                ORDER BY p.zone,p.block_no,p.position,p.product_id
                """, (rs, row) -> {
            long recommendationId = rs.getLong("recommendation_id");
            Long nullableRecommendationId = rs.wasNull() ? null : recommendationId;
            int snapshot = rs.getInt("recommendation_snapshot_id");
            Integer nullableSnapshot = rs.wasNull() ? null : snapshot;
            return new PlacementRow(rs.getInt("product_id"), nullableRecommendationId,
                    rs.getString("recommendation_type"), nullableSnapshot, rs.getString("recommendation_status"),
                    rs.getString("recommendation_policy"), rs.getString("zone"), rs.getInt("block_no"),
                    rs.getInt("position"), rs.getString("name"), rs.getString("description"),
                    rs.getString("image_url"), rs.getString("unit"), rs.getBigDecimal("selling_price"),
                    (Integer) rs.getObject("category_id"), rs.getString("category_name"),
                    rs.getBoolean("requires_expiry"), rs.getString("product_status"));
        }, storeId, layoutId);
    }

    public Map<Long, RecommendationRow> recommendations(int storeId, List<Long> recommendationIds) {
        if (recommendationIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(recommendationIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(storeId);
        args.addAll(recommendationIds);
        var rows = jdbc.query("SELECT recommendation_id,utility_snapshot_id,type,review_status,policy_snapshot "
                + "FROM recommendation WHERE store_id=? AND recommendation_id IN (" + placeholders + ")",
                (rs, row) -> new RecommendationRow(rs.getLong("recommendation_id"),
                        rs.getInt("utility_snapshot_id"), rs.getString("type"), rs.getString("review_status"),
                        rs.getString("policy_snapshot")), args.toArray());
        var result = new HashMap<Long, RecommendationRow>();
        rows.forEach(row -> result.put(row.id(), row));
        return Map.copyOf(result);
    }

    public Map<Long, List<Integer>> recommendationProducts(int storeId, List<Long> recommendationIds) {
        if (recommendationIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(recommendationIds.size(), "?"));
        List<Object> args = new ArrayList<>();
        args.add(storeId);
        args.addAll(recommendationIds);
        Map<Long, List<Integer>> result = new LinkedHashMap<>();
        jdbc.query("SELECT recommendation_id,product_id FROM recommendation_item WHERE store_id=? "
                + "AND recommendation_id IN (" + placeholders + ") ORDER BY recommendation_id,product_id",
                (org.springframework.jdbc.core.RowCallbackHandler) rs -> result
                        .computeIfAbsent(rs.getLong("recommendation_id"), ignored -> new ArrayList<>())
                        .add(rs.getInt("product_id")), args.toArray());
        result.replaceAll((id, ids) -> List.copyOf(ids));
        return Map.copyOf(result);
    }

    private org.springframework.jdbc.core.RowMapper<ProductRow> productMapper() {
        return (rs, row) -> new ProductRow(rs.getInt("product_id"), rs.getString("name"),
                rs.getString("description"), rs.getString("image_url"), rs.getString("unit"),
                rs.getBigDecimal("selling_price"), (Integer) rs.getObject("category_id"),
                rs.getString("category_name"), rs.getBoolean("requires_expiry"), rs.getString("status"));
    }
}
