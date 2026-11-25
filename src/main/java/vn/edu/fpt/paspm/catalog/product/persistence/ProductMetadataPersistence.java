package vn.edu.fpt.paspm.catalog.product.persistence;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
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

    public int create(int storeId, String name, Integer categoryId, String unit,
            boolean requiresExpiry, BigDecimal sellingPrice, BigDecimal costPrice, String status) {
        return create(storeId,name,categoryId,unit,requiresExpiry,sellingPrice,costPrice,status,null,null);
    }

    public int create(int storeId, String name, Integer categoryId, String unit,
            boolean requiresExpiry, BigDecimal sellingPrice, BigDecimal costPrice, String status,
            String imageUrl, String description) {
        var product = new ProductEntity(storeId);
        // Persist once with a private temporary value to obtain the database-generated ID.
        product.assignSku("TMP-" + UUID.randomUUID());
        product.replaceMetadata(name,categoryId,unit,requiresExpiry,sellingPrice,costPrice,status,imageUrl,description);
        em.persist(product);
        // JDBC readbacks do not trigger JPA auto-flush. Surface constraints inside this translated boundary.
        em.flush();
        String sku = generatedSku(product.id());
        product.assignSku(sku);
        em.flush();
        return product.id();
    }

    private String generatedSku(int productId) {
        String candidate = "SKU-" + String.format(Locale.ROOT, "%011d", productId);
        while (skuAlreadyExists(candidate)) {
            candidate = "SKU-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 11).toUpperCase(Locale.ROOT);
        }
        return candidate;
    }

    private boolean skuAlreadyExists(String sku) {
        Number count = (Number) em.createNativeQuery("SELECT COUNT(*) FROM product WHERE UPPER(TRIM(sku)) = :sku")
                .setParameter("sku", sku).getSingleResult();
        return count.longValue() > 0;
    }

    public boolean update(int storeId, int id, String name, Integer categoryId, String unit,
            boolean requiresExpiry, BigDecimal sellingPrice, BigDecimal costPrice, String status) {
        return update(storeId,id,name,categoryId,unit,requiresExpiry,sellingPrice,costPrice,status,null,null);
    }

    public boolean update(int storeId, int id, String name, Integer categoryId, String unit,
            boolean requiresExpiry, BigDecimal sellingPrice, BigDecimal costPrice, String status,
            String imageUrl, String description) {
        var product = products.findByStoreIdAndProductId(storeId,id).orElse(null);
        if (product == null) return false;
        // A previous native/JDBC write in the same transaction may have made this managed instance stale.
        em.refresh(product);
        product.replaceMetadata(name,categoryId,unit,requiresExpiry,sellingPrice,costPrice,status,imageUrl,description);
        em.flush();
        return true;
    }

    public boolean updateStatus(int storeId, int id, String status) {
        // A scoped column-only update preserves concurrent price writes; clear removes stale managed metadata.
        return products.updateStatus(storeId,id,status) == 1;
    }
}
