package vn.edu.fpt.paspm.catalog.product;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import vn.edu.fpt.paspm.catalog.CatalogContracts.CategoryView;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductPage;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductView;
import vn.edu.fpt.paspm.catalog.product.persistence.ProductMetadataPersistence;

@Repository
public class ProductRepository {
    private static final String SELECT_PRODUCT = """
            SELECT p.product_id, p.sku, p.name, p.unit, p.requires_expiry,
                   p.category_id, c.name AS category_name, c.status AS category_status,
                   p.selling_price, p.cost_price, p.status, p.image_url, p.description,
                   COALESCE(stock.stock_quantity, 0) AS stock_quantity
            FROM product p
            LEFT JOIN category c ON c.category_id=p.category_id AND c.store_id=p.store_id
            LEFT JOIN (SELECT product_id, SUM(remaining_quantity) AS stock_quantity
                       FROM inventory_batch GROUP BY product_id) stock ON stock.product_id=p.product_id
            """;

    private final JdbcTemplate jdbc;
    private final ProductMetadataPersistence metadata;
    private final RowMapper<ProductView> mapper = (rs, row) -> {
        Integer categoryId = rs.getObject("category_id", Integer.class);
        CategoryView category = categoryId == null ? null : new CategoryView(
                categoryId, rs.getString("category_name"), rs.getString("category_status"));
        return new ProductView(rs.getInt("product_id"), rs.getString("sku"), rs.getString("name"),
                rs.getString("unit"), rs.getBoolean("requires_expiry"), category,
                rs.getBigDecimal("selling_price"), rs.getBigDecimal("cost_price"),
                rs.getString("status"), rs.getLong("stock_quantity"),
                rs.getString("image_url"), rs.getString("description"));
    };

    public ProductRepository(JdbcTemplate jdbc, ProductMetadataPersistence metadata) {
        this.jdbc = jdbc;
        this.metadata = metadata;
    }

    /** Soft-deleted products (DEC-20261124-03) are not part of the catalogue: every catalogue read filters them here. */
    public ProductView find(int storeId, int productId) {
        return jdbc.query(SELECT_PRODUCT + " WHERE p.store_id=? AND p.product_id=? AND p.deleted_at IS NULL", mapper, storeId, productId)
                .stream().findFirst().orElse(null);
    }

    public List<String> findOtherNames(int storeId, int excludedProductId) {
        return jdbc.query("""
                SELECT name FROM product WHERE store_id=? AND product_id<>? AND deleted_at IS NULL
                """, (rs, row) -> rs.getString("name"), storeId, excludedProductId);
    }

    public ProductPage search(int storeId, String search, Integer categoryId, String status,
                              int page, int pageSize, String sortColumn, String direction) {
        // deleted products are excluded in the WHERE shared by the COUNT and the LIMIT/OFFSET query, i.e. before pagination
        StringBuilder where = new StringBuilder(" WHERE p.store_id=? AND p.deleted_at IS NULL");
        List<Object> parameters = new ArrayList<>();
        parameters.add(storeId);
        if (!search.isBlank()) {
            where.append(" AND (LOWER(p.name) LIKE ? OR LOWER(p.sku) LIKE ?)");
            String pattern = "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            parameters.add(pattern); parameters.add(pattern);
        }
        if (categoryId != null) { where.append(" AND p.category_id=?"); parameters.add(categoryId); }
        if (!status.isBlank()) { where.append(" AND p.status=?"); parameters.add(status); }
        String from = " FROM product p";
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from + where, Long.class, parameters.toArray());
        String sql = SELECT_PRODUCT + where + " ORDER BY " + sortColumn + " " + direction
                + ", p.product_id ASC LIMIT ? OFFSET ?";
        parameters.add(pageSize); parameters.add((long) page * pageSize);
        List<ProductView> content = jdbc.query(sql, mapper, parameters.toArray());
        long elements = total == null ? 0 : total;
        int pages = (int) ((elements + pageSize - 1) / pageSize);
        return new ProductPage(content, page, pageSize, elements, pages);
    }

    public ProductView create(int storeId, String name, Integer categoryId, String unit,
                              boolean requiresExpiry, java.math.BigDecimal sellingPrice,
                              java.math.BigDecimal costPrice, String status) {
        return create(storeId, name, categoryId, unit, requiresExpiry, sellingPrice, costPrice, status, null, null);
    }

    public ProductView create(int storeId, String name, Integer categoryId, String unit,
                              boolean requiresExpiry, java.math.BigDecimal sellingPrice,
                              java.math.BigDecimal costPrice, String status, String imageUrl, String description) {
        int id = metadata.create(storeId,name,categoryId,unit,requiresExpiry,sellingPrice,costPrice,status,imageUrl,description);
        return find(storeId,id);
    }

    public boolean update(int storeId, int id, String name, Integer categoryId, String unit,
                          boolean requiresExpiry, java.math.BigDecimal sellingPrice,
                          java.math.BigDecimal costPrice, String status) {
        return update(storeId, id, name, categoryId, unit, requiresExpiry, sellingPrice, costPrice, status, null, null);
    }

    public boolean update(int storeId, int id, String name, Integer categoryId, String unit,
                          boolean requiresExpiry, java.math.BigDecimal sellingPrice,
                          java.math.BigDecimal costPrice, String status, String imageUrl, String description) {
        return metadata.update(storeId,id,name,categoryId,unit,requiresExpiry,sellingPrice,costPrice,status,imageUrl,description);
    }

    public boolean hasExpirylessBatches(int productId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch WHERE product_id=? AND expiry_date IS NULL",
                Integer.class, productId);
        return count != null && count > 0;
    }

    public boolean updateStatus(int storeId, int id, String status) {
        return metadata.updateStatus(storeId,id,status);
    }

    /** Locks the live product row, then its batches, so a concurrent receive/edit cannot slip past the delete checks. */
    public boolean lockForDelete(int storeId, int productId) {
        boolean live = !jdbc.query("SELECT product_id FROM product WHERE store_id=? AND product_id=? AND deleted_at IS NULL FOR UPDATE",
                (rs, row) -> rs.getInt(1), storeId, productId).isEmpty();
        if (live) jdbc.query("SELECT batch_id FROM inventory_batch WHERE product_id=? FOR UPDATE", (rs, row) -> rs.getInt(1), productId);
        return live;
    }

    /** Sellable = stored ACTIVE, remaining &gt; 0 and not expired (a batch expiring today still counts). */
    public long sellableQuantity(int productId, java.time.LocalDate today) {
        Long quantity = jdbc.queryForObject("""
                SELECT COALESCE(SUM(remaining_quantity), 0) FROM inventory_batch
                WHERE product_id=? AND status='ACTIVE' AND remaining_quantity > 0
                  AND (expiry_date IS NULL OR expiry_date >= ?)
                """, Long.class, productId, today);
        return quantity == null ? 0 : quantity;
    }

    /** PUBLISHED or ACTIVE campaigns that hold the product in any placement of layout A or B. */
    public List<ProductDeleteBlockedException.LiveCampaign> liveCampaigns(int storeId, int productId) {
        return jdbc.query("""
                SELECT DISTINCT c.campaign_id, c.status
                FROM layout_placement p
                JOIN storefront_layout l ON l.store_id=p.store_id AND l.layout_id=p.layout_id
                JOIN campaign c ON c.store_id=l.store_id AND c.campaign_id=l.campaign_id
                WHERE p.store_id=? AND p.product_id=? AND c.status IN ('PUBLISHED','ACTIVE')
                ORDER BY c.campaign_id
                """, (rs, row) -> new ProductDeleteBlockedException.LiveCampaign(rs.getLong("campaign_id"), rs.getString("status")),
                storeId, productId);
    }

    /** The row stays. status=INACTIVE in the same statement keeps F03/F06/F07 treating it as not sellable. */
    public boolean softDelete(int storeId, int productId, int actorUserId) {
        return jdbc.update("""
                UPDATE product SET deleted_at=CURRENT_TIMESTAMP, deleted_by=?, status='INACTIVE'
                WHERE store_id=? AND product_id=? AND deleted_at IS NULL
                """, actorUserId, storeId, productId) == 1;
    }
}
