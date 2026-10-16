package vn.edu.fpt.paspm.catalog.product;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import vn.edu.fpt.paspm.catalog.CatalogContracts.CategoryView;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductPage;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductView;

@Repository
public class ProductRepository {
    private static final String SELECT_PRODUCT = """
            SELECT p.product_id, p.sku, p.name, p.unit, p.requires_expiry,
                   p.category_id, c.name AS category_name, c.status AS category_status,
                   p.selling_price, p.cost_price, p.status,
                   COALESCE(stock.stock_quantity, 0) AS stock_quantity
            FROM product p
            LEFT JOIN category c ON c.category_id=p.category_id AND c.store_id=p.store_id
            LEFT JOIN (SELECT product_id, SUM(remaining_quantity) AS stock_quantity
                       FROM inventory_batch GROUP BY product_id) stock ON stock.product_id=p.product_id
            """;

    private final JdbcTemplate jdbc;
    private final RowMapper<ProductView> mapper = (rs, row) -> {
        Integer categoryId = rs.getObject("category_id", Integer.class);
        CategoryView category = categoryId == null ? null : new CategoryView(
                categoryId, rs.getString("category_name"), rs.getString("category_status"));
        return new ProductView(rs.getInt("product_id"), rs.getString("sku"), rs.getString("name"),
                rs.getString("unit"), rs.getBoolean("requires_expiry"), category,
                rs.getBigDecimal("selling_price"), rs.getBigDecimal("cost_price"),
                rs.getString("status"), rs.getLong("stock_quantity"));
    };

    public ProductRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

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
                              java.math.BigDecimal costPrice, String status) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO product(store_id, category_id, sku, name, unit, requires_expiry,
                                        selling_price, cost_price, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[] {"product_id"});
            statement.setInt(1, storeId);
            if (categoryId == null) statement.setNull(2, java.sql.Types.INTEGER); else statement.setInt(2, categoryId);
            statement.setString(3, sku.trim()); statement.setString(4, name.trim()); statement.setString(5, unit.trim());
            statement.setBoolean(6, requiresExpiry); statement.setBigDecimal(7, sellingPrice);
            statement.setBigDecimal(8, costPrice); statement.setString(9, status);
            return statement;
        }, key);
        return find(storeId, key.getKey().intValue());
    }

    public boolean update(int storeId, int id, String sku, String name, Integer categoryId, String unit,
                          boolean requiresExpiry, java.math.BigDecimal sellingPrice,
                          java.math.BigDecimal costPrice, String status) {
        return jdbc.update("""
                UPDATE product SET category_id=?, sku=?, name=?, unit=?, requires_expiry=?,
                                   selling_price=?, cost_price=?, status=?
                WHERE store_id=? AND product_id=?
                """, categoryId, sku.trim(), name.trim(), unit.trim(), requiresExpiry,
                sellingPrice, costPrice, status, storeId, id) == 1;
    }

    public boolean hasExpirylessBatches(int productId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_batch WHERE product_id=? AND expiry_date IS NULL",
                Integer.class, productId);
        return count != null && count > 0;
    }

    public boolean updateStatus(int storeId, int id, String status) {
        return jdbc.update("UPDATE product SET status=? WHERE store_id=? AND product_id=?", status, storeId, id) == 1;
    }
}
