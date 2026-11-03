package vn.edu.fpt.paspm.catalog.product.persistence;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** F02 metadata only. F03 score columns and inventory remain owned by JDBC. */
@Entity
@Table(name = "product")
@DynamicUpdate
class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Integer productId;
    @Column(name = "store_id", nullable = false, updatable = false)
    private Integer storeId;
    @Column(name = "category_id")
    private Integer categoryId;
    @Column(nullable = false, length = 80)
    private String sku;
    @Column(nullable = false, length = 180)
    private String name;
    @Column(nullable = false, length = 40)
    private String unit;
    @Column(name = "requires_expiry", nullable = false)
    private boolean requiresExpiry;
    @Column(name = "selling_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal sellingPrice;
    @Column(name = "cost_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal costPrice;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "expiry_push_days", nullable = false)
    private Integer expiryPushDays;

    protected ProductEntity() {}

    ProductEntity(int storeId) { this.storeId = storeId; }

    Integer id() { return productId; }

    void replaceMetadata(String sku, String name, Integer categoryId, String unit, boolean requiresExpiry,
            BigDecimal sellingPrice, BigDecimal costPrice, String status, int expiryPushDays) {
        this.sku = sku.trim();
        this.name = name.trim();
        this.categoryId = categoryId;
        this.unit = unit.trim();
        this.requiresExpiry = requiresExpiry;
        this.sellingPrice = sellingPrice;
        this.costPrice = costPrice;
        this.status = status;
        this.expiryPushDays = expiryPushDays;
    }
}
