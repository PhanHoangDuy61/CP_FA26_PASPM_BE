package vn.edu.fpt.paspm.catalog.category;

import java.util.List;
import org.springframework.stereotype.Repository;
import vn.edu.fpt.paspm.catalog.CatalogContracts.CategoryView;
import vn.edu.fpt.paspm.catalog.category.persistence.CategoryPersistence;

/** Existing application-facing contract backed by Store-scoped JPA metadata operations. */
@Repository
public class CategoryRepository {
    private final CategoryPersistence persistence;

    public CategoryRepository(CategoryPersistence persistence) { this.persistence = persistence; }

    public List<CategoryView> findAll(int storeId) { return persistence.findAll(storeId); }

    public CategoryView find(int storeId, int id) { return persistence.find(storeId, id); }

    public CategoryView create(int storeId, String name, String status) {
        return persistence.create(storeId, name, status);
    }

    public boolean update(int storeId, int id, String name, String status) {
        return persistence.update(storeId, id, name, status);
    }

    public boolean updateStatus(int storeId, int id, String status) {
        return persistence.updateStatus(storeId, id, status);
    }
}
