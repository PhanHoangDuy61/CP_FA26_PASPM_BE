package vn.edu.fpt.paspm.catalog.category.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** No unscoped reads, saves, deletes, or cascading relationships. */
interface CategoryJpaRepository extends Repository<CategoryEntity, Integer> {
    interface Summary {
        int getId();
        String getName();
        String getStatus();
    }

    Optional<CategoryEntity> findByStoreIdAndCategoryId(int storeId, int categoryId);

    @Query("select c.categoryId as id, c.name as name, c.status as status from CategoryEntity c "
            + "where c.storeId=:storeId order by c.name, c.categoryId")
    List<Summary> list(@Param("storeId") int storeId);

    @Query("select c.categoryId as id, c.name as name, c.status as status from CategoryEntity c "
            + "where c.storeId=:storeId and c.categoryId=:categoryId")
    Optional<Summary> findSummary(@Param("storeId") int storeId, @Param("categoryId") int categoryId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CategoryEntity c set c.status=:status where c.storeId=:storeId and c.categoryId=:categoryId")
    int updateStatus(@Param("storeId") int storeId, @Param("categoryId") int categoryId,
            @Param("status") String status);
}
