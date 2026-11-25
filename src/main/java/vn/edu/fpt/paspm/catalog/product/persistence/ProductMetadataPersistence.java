package vn.edu.fpt.paspm.catalog.product.persistence;

import java.math.BigDecimal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Internal write boundary; callers keep their existing JDBC projections. */
@Repository
@Transactional
public class ProductMetadataPersistence {
    private final ProductJpaRepository products;
    @PersistenceContext private EntityManager em;

    ProductMetadataPersistence(ProductJpaRepository products) { this.products = products; }

    public int create(int storeId, String sku, String name, Integer categoryId, String unit,
            boolean requiresExpiry, BigDecimal sellingPrice, BigDecimal costPrice, String status, int expiryPushDays) {
        var product = new ProductEntity(storeId);
        product.replaceMetadata(sku,name,categoryId,unit,requiresExpiry,sellingPrice,costPrice,status,expiryPushDays);
        em.persist(product);
        // JDBC readbacks do not trigger JPA auto-flush. Surface constraints inside this translated boundary.
        em.flush();
        return product.id();
    }

    public boolean update(int storeId, int id, String sku, String name, Integer categoryId, String unit,
            boolean requiresExpiry, BigDecimal sellingPrice, BigDecimal costPrice, String status, int expiryPushDays) {
        var product = products.findByStoreIdAndProductId(storeId,id).orElse(null);
        if (product == null) return false;
        // A previous native/JDBC write in the same transaction may have made this managed instance stale.
        em.refresh(product);
        product.replaceMetadata(sku,name,categoryId,unit,requiresExpiry,sellingPrice,costPrice,status,expiryPushDays);
        em.flush();
        return true;
    }

    public boolean updateStatus(int storeId, int id, String status) {
        // A scoped column-only update preserves concurrent price writes; clear removes stale managed metadata.
        return products.updateStatus(storeId,id,status) == 1;
    }
}
