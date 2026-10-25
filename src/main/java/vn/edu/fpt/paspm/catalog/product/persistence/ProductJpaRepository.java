package vn.edu.fpt.paspm.catalog.product.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Deliberately has no unscoped find/save/delete API. */
interface ProductJpaRepository extends Repository<ProductEntity, Integer> {
    Optional<ProductEntity> findByStoreIdAndProductId(int storeId, int productId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ProductEntity p set p.status=:status where p.storeId=:storeId and p.productId=:productId")
    int updateStatus(@Param("storeId") int storeId, @Param("productId") int productId,
            @Param("status") String status);
}
