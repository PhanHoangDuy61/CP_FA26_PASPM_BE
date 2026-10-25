package vn.edu.fpt.paspm.catalog.category.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

/** Scalar metadata only; Product ownership remains enforced by the existing composite foreign key. */
@Entity
@Table(name = "category")
@DynamicUpdate
class CategoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Integer categoryId;
    @Column(name = "store_id", nullable = false, updatable = false)
    private Integer storeId;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(nullable = false, length = 20)
    private String status;

    protected CategoryEntity() {}

    CategoryEntity(int storeId, String name, String status) {
        this.storeId = storeId;
        replaceMetadata(name, status);
    }

    Integer id() { return categoryId; }

    void replaceMetadata(String name, String status) {
        this.name = name.trim();
        this.status = status;
    }
}
