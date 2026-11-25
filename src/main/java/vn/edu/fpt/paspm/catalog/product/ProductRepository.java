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
                   p.selling_price, p.cost_price, p.status, p.expiry_push_days,
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
                rs.getString("status"), rs.getLong("stock_quantity"), rs.getInt("expiry_push_days"));
    };

    public ProductRepository(JdbcTemplate jdbc, ProductMetadataPersistence metadata) {
        this.jdbc = jdbc;
        this.metadata = metadata;
    }

    public ProductView find(int storeId, int productId) {
        return jdbc.query(SELECT_PRODUCT + " WHERE p.store_id=? AND p.product_id=?", mapper, storeId, productId)
                .stream().findFirst().orElse(null);
    }

    public ProductPage search(int storeId, String search, Integer categoryId, String status,
                              int page, int pageSize, String sortColumn, String direction) {
        StringBuilder where = new StringBuilder(" WHERE p.store_id=?");
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

    public ProductView create(int storeId, String sku, String name, Integer categoryId, String unit,
                              boolean requiresExpiry, java.math.BigDecimal sellingPrice,
                              java.math.BigDecimal costPrice, String status, int expiryPushDays) {
        int id = metadata.create(storeId,sku,name,categoryId,unit,requiresExpiry,sellingPrice,costPrice,status,expiryPushDays);
        return find(storeId,id);
    }

    public boolean update(int storeId, int id, String sku, String name, Integer categoryId, String unit,
                          boolean requiresExpiry, java.math.BigDecimal sellingPrice,
                          java.math.BigDecimal costPrice, String status, int expiryPushDays) {
        return metadata.update(storeId,id,sku,name,categoryId,unit,requiresExpiry,sellingPrice,costPrice,status,expiryPushDays);
    }

    public boolean hasExpirylessBatches(int productId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch WHERE product_id=? AND expiry_date IS NULL",
                Integer.class, productId);
        return count != null && count > 0;
    }

    public boolean updateStatus(int storeId, int id, String status) {
        return metadata.updateStatus(storeId,id,status);
    }
}
