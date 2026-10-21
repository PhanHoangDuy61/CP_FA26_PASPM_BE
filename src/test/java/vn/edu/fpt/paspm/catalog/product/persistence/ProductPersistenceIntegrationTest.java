package vn.edu.fpt.paspm.catalog.product.persistence;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.fpt.paspm.catalog.product.ProductRepository;
import vn.edu.fpt.paspm.catalog.inventory.InventoryBatchRepository;
import vn.edu.fpt.paspm.transaction.importing.TransactionImportService;
import vn.edu.fpt.paspm.utility.ProductScoreRepository;
import vn.edu.fpt.paspm.utility.UtilityContracts.ScorePatch;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:productpersistence;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=test-only-jwt-key-with-at-least-thirty-two-bytes",
        "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=",
        "paspm.avatar.directory=/tmp/paspm-test-avatars",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
class ProductPersistenceIntegrationTest {
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory emf;
    @Autowired PlatformTransactionManager manager;
    @Autowired ProductRepository products;
    @Autowired InventoryBatchRepository batches;
    @Autowired TransactionImportService imports;
    @Autowired ProductScoreRepository scores;
    @PersistenceContext EntityManager em;
    int store;

    @BeforeEach void fixture() {
        jdbc.update("INSERT INTO store(name) VALUES (?)", UUID.randomUUID().toString());
        store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
    }

    @Test void sharedPersistenceUsesOneJpaTransactionManager() {
        var managers = context.getBeansOfType(PlatformTransactionManager.class);
        assertEquals(1, managers.size());
        assertEquals("org.springframework.orm.jpa.JpaTransactionManager",
                managers.values().iterator().next().getClass().getName());
        assertTrue(context.containsBean("entityManagerFactory"));
        assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("false", context.getEnvironment().getProperty("spring.jpa.open-in-view"));
        var jpaManager = (JpaTransactionManager) manager;
        assertSame(dataSource, jpaManager.getDataSource());
        assertSame(emf, jpaManager.getEntityManagerFactory());
        assertInstanceOf(HibernateJpaDialect.class, jpaManager.getJpaDialect());
        assertEquals("validate", emf.getProperties().get("hibernate.hbm2ddl.auto"));
    }

    @Test void productMappingContainsOnlyOwnedMetadata() {
        var entities = emf.getMetamodel().getEntities();
        assertEquals(Set.of("ProductEntity"), entities.stream().map(e -> e.getName()).collect(Collectors.toSet()));
        var entity = entities.iterator().next();
        assertEquals(Set.of("productId", "storeId", "categoryId", "sku", "name", "unit", "requiresExpiry",
                "sellingPrice", "costPrice", "status", "expiryPushDays"),
                entity.getAttributes().stream().map(a -> a.getName()).collect(Collectors.toSet()));
        assertTrue(entity.getAttributes().stream().noneMatch(a -> a.isAssociation() || a.isCollection()));
    }

    @Test void productFacadeActuallyUsesJpaForCreateAndMetadataUpdate() {
        var statistics = emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        long inserts = statistics.getEntityInsertCount();
        int id = create("JPA-WRITER").id();
        assertEquals(inserts + 1, statistics.getEntityInsertCount());
        long updates = statistics.getEntityUpdateCount();
        assertTrue(products.update(store,id,"JPA-WRITER","Updated",null,"unit",false,
                BigDecimal.TEN,BigDecimal.ONE,"ACTIVE",0));
        assertEquals(updates + 1, statistics.getEntityUpdateCount());
    }

    @Test void jdbcAndJpaShareTheActualTransactionalConnectionAndIsolation() {
        for (int isolation : new int[] {TransactionDefinition.ISOLATION_READ_COMMITTED,
                TransactionDefinition.ISOLATION_REPEATABLE_READ}) {
            var tx = transaction();
            tx.setIsolationLevel(isolation);
            tx.executeWithoutResult(ignored -> {
                var jdbcConnection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
                em.unwrap(Session.class).doWork(connection -> {
                    assertSame(jdbcConnection, connection);
                    assertEquals(isolation, connection.getTransactionIsolation());
                });
            });
        }
    }

    @Test void productJpaAndInventoryJdbcCommitTogetherWithReadYourWrites() throws Exception {
        int id = transaction().execute(ignored -> {
            var created = create(" COMMIT ");
            assertEquals("COMMIT", created.sku());
            assertEquals("Product", created.name());
            assertNull(created.category());
            var batch = batches.create(created.id(), " batch ", Instant.now(), null, 7);
            assertEquals(7, products.find(store, created.id()).stockQuantity());
            assertEquals("batch", batch.batchCode());
            return created.id();
        });
        try (var c = dataSource.getConnection(); var statement = c.prepareStatement(
                "SELECT p.selling_price,b.remaining_quantity FROM product p JOIN inventory_batch b ON b.product_id=p.product_id WHERE p.product_id=?")) {
            statement.setInt(1, id);
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(new BigDecimal("12.34"), rows.getBigDecimal(1));
                assertEquals(7, rows.getInt(2));
            }
        }
    }

    @Test void forcedFailureRollsBackProductJpaAndInventoryJdbcFromIndependentConnection() throws Exception {
        var id = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(ignored -> {
            id.set(create("ROLLBACK").id());
            batches.create(id.get(), "rollback", Instant.now(), null, 3);
            throw new IllegalStateException("forced rollback after both writers");
        }));
        try (var c = dataSource.getConnection(); var statement = c.prepareStatement(
                "SELECT (SELECT COUNT(*) FROM product WHERE product_id=?)+(SELECT COUNT(*) FROM inventory_batch WHERE product_id=?)")) {
            statement.setInt(1, id.get()); statement.setInt(2, id.get());
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); assertEquals(0, rows.getInt(1)); }
        }
    }

    @Test void existingManagedProductAndExistingJdbcBatchUpdatesCommitTogether() throws Exception {
        int id = create("EXISTING-COMMIT").id();
        int batch = batches.create(id,"existing-commit",Instant.now(),null,9).id();
        transaction().executeWithoutResult(ignored -> {
            managed(id);
            assertTrue(products.update(store,id,"EXISTING-COMMIT","Committed name",null,"unit",false,
                    new BigDecimal("12.34"),new BigDecimal("2.00"),"ACTIVE",0));
            batches.update(batch,4,null);
            assertEquals("Committed name",products.find(store,id).name());
            assertEquals(4,batches.find(store,batch).remainingQuantity());
        });
        assertCommittedProductAndBatch(id,batch,"Committed name",4);
    }

    @Test void controlledFailureRollsBackExistingManagedProductAndExistingJdbcBatchUpdates() throws Exception {
        int id = create("EXISTING-ROLLBACK").id();
        int batch = batches.create(id,"existing-rollback",Instant.now(),null,9).id();
        assertThrows(IllegalStateException.class,() -> transaction().executeWithoutResult(ignored -> {
            managed(id);
            assertTrue(products.update(store,id,"EXISTING-ROLLBACK","Must roll back",null,"unit",false,
                    new BigDecimal("12.34"),new BigDecimal("2.00"),"ACTIVE",0));
            batches.update(batch,4,null);
            assertEquals("Must roll back",products.find(store,id).name());
            assertEquals(4,batches.find(store,batch).remainingQuantity());
            throw new IllegalStateException("forced failure after both existing rows changed");
        }));
        assertCommittedProductAndBatch(id,batch,"Product",9);
    }

    @Test void metadataUpdatesFlushBeforeJdbcReadbackAndPreserveNativeScoresAndInventory() {
        int id = create("SCORES").id();
        batches.create(id, "scores", Instant.now(), null, 8);
        transaction().executeWithoutResult(ignored -> {
            managed(id);
            jdbc.update("UPDATE product SET stock_priority_score=0.123456,seasonal_priority_score=0.654321 WHERE product_id=?", id);
            assertTrue(products.update(store,id," SCORES "," Changed ",null," item ",false,
                    new BigDecimal("99.99"),new BigDecimal("1.25"),"ACTIVE",5));
            assertEquals("Changed", products.find(store,id).name());
            assertEquals(5, products.find(store,id).expiryPushDays());
        });
        assertEquals(new BigDecimal("0.123456"), jdbc.queryForObject("SELECT stock_priority_score FROM product WHERE product_id=?", BigDecimal.class,id));
        assertEquals(new BigDecimal("0.654321"), jdbc.queryForObject("SELECT seasonal_priority_score FROM product WHERE product_id=?", BigDecimal.class,id));
        assertEquals(8, products.find(store,id).stockQuantity());
    }

    @Test void statusOnlyWriteDoesNotOverwriteNativePriceOnPreviouslyManagedProduct() {
        int id = create("STATUS").id();
        transaction().executeWithoutResult(ignored -> {
            managed(id);
            jdbc.update("UPDATE product SET selling_price=456.78 WHERE product_id=?",id);
            assertTrue(products.updateStatus(store,id,"INACTIVE"));
            assertEquals(new BigDecimal("456.78"),products.find(store,id).sellingPrice());
            assertEquals("INACTIVE",products.find(store,id).status());
            assertEquals(new BigDecimal("456.78"),em.createQuery("select p.sellingPrice from ProductEntity p where p.storeId=:store and p.productId=:id",BigDecimal.class)
                    .setParameter("store",store).setParameter("id",id).getSingleResult());
        });
    }

    @Test void repeatedConcurrentNativeScoresAndPriceSurviveStatusWritesFromStaleJpaState() {
        for (int attempt = 0; attempt < 5; attempt++) {
            int id = create("CONCURRENT-STATUS-" + attempt).id();
            BigDecimal price = new BigDecimal("456.78");
            transaction().executeWithoutResult(ignored -> {
                managed(id);
                CompletableFuture.runAsync(() -> {
                    var patch = new ScorePatch();
                    patch.setStockPriorityScore(new BigDecimal("0.123456"));
                    patch.setSeasonalPriorityScore(new BigDecimal("0.654321"));
                    assertTrue(scores.update(store,id,patch));
                    jdbc.update("UPDATE product SET selling_price=? WHERE product_id=?",price,id);
                }).orTimeout(5,TimeUnit.SECONDS).join();
                assertTrue(products.updateStatus(store,id,"INACTIVE"));
            });
            assertEquals(price,products.find(store,id).sellingPrice());
            assertEquals("INACTIVE",products.find(store,id).status());
            assertEquals(new BigDecimal("0.123456"),jdbc.queryForObject("SELECT stock_priority_score FROM product WHERE product_id=?",BigDecimal.class,id));
            assertEquals(new BigDecimal("0.654321"),jdbc.queryForObject("SELECT seasonal_priority_score FROM product WHERE product_id=?",BigDecimal.class,id));
        }
    }

    @Test void repeatedConcurrentF03ScoreWritesSurviveJpaMetadataUpdates() {
        for (int attempt = 0; attempt < 5; attempt++) {
            int id = create("CONCURRENT-METADATA-" + attempt).id();
            transaction().executeWithoutResult(ignored -> {
                managed(id);
                CompletableFuture.runAsync(() -> {
                    var patch = new ScorePatch();
                    patch.setStockPriorityScore(new BigDecimal("0.777777"));
                    patch.setSeasonalPriorityScore(new BigDecimal("0.222222"));
                    assertTrue(scores.update(store,id,patch));
                }).orTimeout(5,TimeUnit.SECONDS).join();
                assertTrue(products.update(store,id,"CONCURRENT-METADATA-UPDATED-"+id,"Updated",null,"unit",false,
                        BigDecimal.TEN,BigDecimal.ONE,"ACTIVE",7));
                assertEquals("Updated",products.find(store,id).name());
            });
            assertEquals(new BigDecimal("0.777777"),jdbc.queryForObject("SELECT stock_priority_score FROM product WHERE product_id=?",BigDecimal.class,id));
            assertEquals(new BigDecimal("0.222222"),jdbc.queryForObject("SELECT seasonal_priority_score FROM product WHERE product_id=?",BigDecimal.class,id));
        }
    }

    @Test void nativeUpdateIsStaleUntilExplicitRefreshAndMetadataAdapterRefreshesItsBoundary() {
        int id = create("STALE").id();
        transaction().executeWithoutResult(ignored -> {
            Object entity = managed(id);
            jdbc.update("UPDATE product SET name='Native name' WHERE product_id=?", id);
            assertEquals("Product", ReflectionTestUtils.getField(entity,"name"));
            em.refresh(entity);
            assertEquals("Native name", ReflectionTestUtils.getField(entity,"name"));
            jdbc.update("UPDATE product SET name='Second native name',unit='box' WHERE product_id=?",id);
            // These requested values equal the stale managed snapshot. Refresh must still restore them in SQL.
            assertTrue(products.update(store,id,"STALE","Native name",null,"unit",false,
                    new BigDecimal("12.34"),new BigDecimal("2.00"),"ACTIVE",0));
            assertEquals("Native name",products.find(store,id).name());
            assertEquals("unit",products.find(store,id).unit());
        });
    }

    @Test void writesAreStoreScopedAndDuplicateConstraintsRemainTranslated() {
        int id = create("UNIQUE").id();
        assertFalse(products.updateStatus(store+1,id,"INACTIVE"));
        assertFalse(products.update(store+1,id,"UNIQUE","Foreign",null,"unit",false,
                BigDecimal.ONE,BigDecimal.ONE,"ACTIVE",0));
        assertEquals("Product",products.find(store,id).name());
        assertThrows(DataIntegrityViolationException.class, () -> create("UNIQUE"));
        assertEquals("ACTIVE",products.find(store,id).status());
    }

    @Test void directForeignStoreCategoryViolatesCompositeForeignKeyAndRemainsTranslated() {
        String foreignStoreName = "Foreign " + UUID.randomUUID();
        jdbc.update("INSERT INTO store(name) VALUES (?)",foreignStoreName);
        int foreignStore = jdbc.queryForObject("SELECT store_id FROM store WHERE name=?",Integer.class,foreignStoreName);
        jdbc.update("INSERT INTO category(store_id,name,status) VALUES (?,'Foreign category','ACTIVE')",foreignStore);
        int foreignCategory = jdbc.queryForObject("SELECT category_id FROM category WHERE store_id=?",Integer.class,foreignStore);
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE store_id=?",Integer.class,store);
        assertThrows(DataIntegrityViolationException.class,() -> products.create(store,"FOREIGN-CATEGORY",
                "Rejected product",foreignCategory,"unit",false,BigDecimal.TEN,BigDecimal.ONE,"ACTIVE",0));
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE store_id=?",Integer.class,store));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE store_id=? AND sku='FOREIGN-CATEGORY'",Integer.class,store));
    }

    @Test void f04RequiresNewTemplateKeepsInnerJdbcCommitAfterOuterJpaRollback() {
        var inner = (TransactionTemplate) ReflectionTestUtils.getField(imports,"orderTransaction");
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,inner.getPropagationBehavior());
        assertSame(manager,inner.getTransactionManager());
        String category = UUID.randomUUID().toString();
        var id = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> transaction().executeWithoutResult(ignored -> {
            id.set(create("OUTER").id());
            inner.executeWithoutResult(order -> jdbc.update("INSERT INTO category(store_id,name,status) VALUES (?,?,'ACTIVE')",store,category));
            throw new IllegalStateException("outer failed after per-order commit");
        }));
        assertNull(products.find(store,id.get()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE store_id=? AND name=?",Integer.class,store,category));
    }

    private Object managed(int id) {
        return em.createQuery("select p from ProductEntity p where p.storeId=:store and p.productId=:id")
                .setParameter("store",store).setParameter("id",id).getSingleResult();
    }

    private vn.edu.fpt.paspm.catalog.CatalogContracts.ProductView create(String sku) {
        return products.create(store,sku," Product ",null," unit ",false,new BigDecimal("12.34"),
                new BigDecimal("2.00"),"ACTIVE",0);
    }

    private TransactionTemplate transaction() { return new TransactionTemplate(manager); }

    private void assertCommittedProductAndBatch(int productId,int batchId,String name,int quantity) throws Exception {
        try (var connection=dataSource.getConnection();var statement=connection.prepareStatement(
                "SELECT p.name,b.remaining_quantity FROM product p JOIN inventory_batch b ON b.product_id=p.product_id WHERE p.product_id=? AND b.batch_id=?")) {
            statement.setInt(1,productId); statement.setInt(2,batchId);
            try (var rows=statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(name,rows.getString(1));
                assertEquals(quantity,rows.getInt(2));
                assertFalse(rows.next());
            }
        }
    }
}
