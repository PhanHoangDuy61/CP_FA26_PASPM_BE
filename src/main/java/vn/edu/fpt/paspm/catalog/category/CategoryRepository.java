package vn.edu.fpt.paspm.catalog.category;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import vn.edu.fpt.paspm.catalog.CatalogContracts.CategoryView;

@Repository
public class CategoryRepository {
    private final JdbcTemplate jdbc;

    public CategoryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<CategoryView> findAll(int storeId) {
        return jdbc.query("SELECT category_id, name, status FROM category WHERE store_id=? ORDER BY name, category_id",
                (rs, row) -> new CategoryView(rs.getInt("category_id"), rs.getString("name"), rs.getString("status")),
                storeId);
    }

    public CategoryView find(int storeId, int id) {
        return jdbc.query("SELECT category_id, name, status FROM category WHERE store_id=? AND category_id=?",
                (rs, row) -> new CategoryView(rs.getInt("category_id"), rs.getString("name"), rs.getString("status")),
                storeId, id).stream().findFirst().orElse(null);
    }

    public CategoryView create(int storeId, String name, String status) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO category(store_id, name, status) VALUES (?, ?, ?)", new String[] {"category_id"});
            statement.setInt(1, storeId); statement.setString(2, name.trim()); statement.setString(3, status);
            return statement;
        }, key);
        return find(storeId, key.getKey().intValue());
    }

    public boolean update(int storeId, int id, String name, String status) {
        return jdbc.update("UPDATE category SET name=?, status=? WHERE store_id=? AND category_id=?",
                name.trim(), status, storeId, id) == 1;
    }

    public boolean updateStatus(int storeId, int id, String status) {
        return jdbc.update("UPDATE category SET status=? WHERE store_id=? AND category_id=?", status, storeId, id) == 1;
    }
}
