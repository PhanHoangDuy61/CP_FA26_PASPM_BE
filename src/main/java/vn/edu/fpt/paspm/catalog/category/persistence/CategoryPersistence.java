package vn.edu.fpt.paspm.catalog.category.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.catalog.CatalogContracts.CategoryView;

/** Transaction and exception-translation boundary; only DTOs leave this package. */
@Repository
@Transactional(readOnly = true)
public class CategoryPersistence {
    private final CategoryJpaRepository categories;
    @PersistenceContext private EntityManager em;

    CategoryPersistence(CategoryJpaRepository categories) { this.categories = categories; }

    public List<CategoryView> findAll(int storeId) {
        return categories.list(storeId).stream().map(CategoryPersistence::view).toList();
    }

    public CategoryView find(int storeId, int id) {
        // Projections observe JDBC edits even if an entity is already managed and stale.
        return categories.findSummary(storeId, id).map(CategoryPersistence::view).orElse(null);
    }

    @Transactional
    public CategoryView create(int storeId, String name, String status) {
        var category = new CategoryEntity(storeId, name, status);
        em.persist(category);
        // JDBC consumers share the transaction but do not trigger JPA auto-flush.
        em.flush();
        return find(storeId, category.id());
    }

    @Transactional
    public boolean update(int storeId, int id, String name, String status) {
        var category = categories.findByStoreIdAndCategoryId(storeId, id).orElse(null);
        if (category == null) return false;
        em.refresh(category);
        category.replaceMetadata(name, status);
        em.flush();
        return true;
    }

    @Transactional
    public boolean updateStatus(int storeId, int id, String status) {
        // Flush before clear protects pending Product/Category edits in the shared context.
        return categories.updateStatus(storeId, id, status) == 1;
    }

    private static CategoryView view(CategoryJpaRepository.Summary category) {
        return new CategoryView(category.getId(), category.getName(), category.getStatus());
    }
}
