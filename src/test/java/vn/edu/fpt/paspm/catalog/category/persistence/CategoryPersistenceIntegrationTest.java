package vn.edu.fpt.paspm.catalog.category.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.catalog.category.CategoryRepository;
import vn.edu.fpt.paspm.catalog.product.ProductRepository;
import vn.edu.fpt.paspm.catalog.inventory.InventoryBatchRepository;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:categorypersistence;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true",
    "paspm.jwt-secret=category-test-signing-key-at-least-thirty-two-characters",
    "paspm.otp-secret=category-test-otp-key-at-least-thirty-two-characters",
    "paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.avatar.directory=${java.io.tmpdir}/category-avatars",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureMockMvc
class CategoryPersistenceIntegrationTest {
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired InventoryBatchRepository batches;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory emf;
    @Autowired PlatformTransactionManager manager;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @PersistenceContext EntityManager em;
    int store,foreign;

    @BeforeEach void stores(){store=newStore();foreign=newStore();}
    int newStore(){jdbc.update("INSERT INTO store(name) VALUES (?)",UUID.randomUUID().toString());return jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);}
    TransactionTemplate transaction(){return new TransactionTemplate(manager);}
    Class<?> categoryClass(){return emf.getMetamodel().getEntities().stream().filter(e->e.getName().equals("CategoryEntity")).findFirst().orElseThrow().getJavaType();}
    int product(int category){return products.create(store,"CAT-"+UUID.randomUUID(),"Product",category,"each",false,BigDecimal.TEN,BigDecimal.ONE,"ACTIVE",0).id();}
    String token(int owner,String role){var u=users.createPendingUser("Synthetic category",UUID.randomUUID()+"@example.test",role,owner,null,null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",u.id());return auth.token(users.findById(u.id())).accessToken();}

    @Test void categoryMappingOwnsOnlyScalarMetadataWithoutCascade(){
        var entity=emf.getMetamodel().getEntities().stream().filter(e->e.getName().equals("CategoryEntity")).findFirst();
        assertTrue(entity.isPresent(),"Category must be a mapped JPA metadata entity");
        assertEquals(Set.of("categoryId","storeId","name","status"),entity.get().getAttributes().stream().map(a->a.getName()).collect(Collectors.toSet()));
        assertTrue(entity.get().getAttributes().stream().noneMatch(a->a.isAssociation()||a.isCollection()));
        assertEquals("validate",emf.getProperties().get("hibernate.hbm2ddl.auto"));
    }
    @Test void categoryFacadeUsesJpaWritesAndDtoQueries(){
        var statistics=emf.unwrap(SessionFactory.class).getStatistics();
        long inserted=statistics.getEntityInsertCount();
        int id=categories.create(store,"  Dairy  ","ACTIVE").id();
        assertEquals(inserted+1,statistics.getEntityInsertCount());
        long updated=statistics.getEntityUpdateCount();
        assertTrue(categories.update(store,id," Edited ","ACTIVE"));
        assertEquals(updated+1,statistics.getEntityUpdateCount());
        long queries=statistics.getQueryExecutionCount();
        assertEquals("Edited",categories.find(store,id).name());
        assertEquals(queries+1,statistics.getQueryExecutionCount());
        assertTrue(categories.updateStatus(store,id,"INACTIVE"));
        assertEquals("INACTIVE",categories.find(store,id).status());
    }
    @Test void scopedListsAndMissingWritesPreserveOrderAndIsolation(){
        int z=categories.create(store,"Z","ACTIVE").id();
        int a=categories.create(store,"A","ACTIVE").id();
        int other=categories.create(foreign,"Foreign","ACTIVE").id();
        assertEquals(List.of(a,z),categories.findAll(store).stream().map(c->c.id()).toList());
        assertNull(categories.find(store,other));
        assertFalse(categories.update(store,other,"Steal","INACTIVE"));
        assertFalse(categories.updateStatus(store,other,"INACTIVE"));
        assertFalse(categories.update(store,Integer.MAX_VALUE,"Missing","ACTIVE"));
        assertFalse(categories.updateStatus(store,Integer.MAX_VALUE,"ACTIVE"));
        assertEquals("Foreign",categories.find(foreign,other).name());
    }
    @Test void duplicateNamesRemainAllowedByExistingSchema(){
        int first=categories.create(store,"Duplicate","ACTIVE").id();
        int second=categories.create(store,"Duplicate","ACTIVE").id();
        assertNotEquals(first,second);
        assertEquals(2,categories.findAll(store).size());
        assertNotNull(categories.create(foreign,"Duplicate","ACTIVE"));
    }
    @Test void categoryJpaProductJpaAndInventoryJdbcCommitTogether()throws Exception{
        int[] ids=transaction().execute(ignored->{
            int category=categories.create(store,"Committed","ACTIVE").id();int product=product(category);
            int batch=batches.create(product,"Committed batch",Instant.now(),null,7).id();
            assertEquals("Committed",jdbc.queryForObject("SELECT name FROM category WHERE category_id=?",String.class,category));
            assertEquals(7,products.find(store,product).stockQuantity());return new int[]{category,product,batch};
        });
        try(var connection=dataSource.getConnection();var statement=connection.prepareStatement(
                "SELECT c.name,p.category_id,b.remaining_quantity FROM category c JOIN product p ON p.category_id=c.category_id JOIN inventory_batch b ON b.product_id=p.product_id WHERE c.category_id=?")){
            statement.setInt(1,ids[0]);try(var rows=statement.executeQuery()){assertTrue(rows.next());assertEquals("Committed",rows.getString(1));assertEquals(ids[0],rows.getInt(2));assertEquals(7,rows.getInt(3));}
        }
    }
    @Test void rollbackRemovesNewCategoryProductAndJdbcBatch()throws Exception{
        var category=new AtomicInteger();var product=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->transaction().executeWithoutResult(ignored->{
            category.set(categories.create(store,"Rollback new","ACTIVE").id());product.set(product(category.get()));
            batches.create(product.get(),"Rollback batch",Instant.now(),null,3);throw new IllegalStateException("Controlled rollback");
        }));
        try(var connection=dataSource.getConnection();var statement=connection.prepareStatement(
                "SELECT (SELECT COUNT(*) FROM category WHERE category_id=?)+(SELECT COUNT(*) FROM product WHERE product_id=?)+(SELECT COUNT(*) FROM inventory_batch WHERE product_id=?)")){
            statement.setInt(1,category.get());statement.setInt(2,product.get());statement.setInt(3,product.get());
            try(var rows=statement.executeQuery()){assertTrue(rows.next());assertEquals(0,rows.getInt(1));}
        }
    }
    @Test void rollbackRestoresExistingCategoryProductInventoryAndScores(){
        int category=categories.create(store,"Before","ACTIVE").id();int product=product(category);
        int batch=batches.create(product,"Existing batch",Instant.now(),null,8).id();
        jdbc.update("UPDATE product SET stock_priority_score=.8,seasonal_priority_score=.9 WHERE product_id=?",product);
        transaction().executeWithoutResult(status->{
            categories.update(store,category,"Transient","INACTIVE");
            products.updateStatus(store,product,"INACTIVE");batches.update(batch,2,null);status.setRollbackOnly();
        });
        assertEquals("Before",categories.find(store,category).name());assertEquals("ACTIVE",products.find(store,product).status());
        assertEquals(8,products.find(store,product).stockQuantity());
        assertEquals(0,new BigDecimal("0.8").compareTo(jdbc.queryForObject("SELECT stock_priority_score FROM product WHERE product_id=?",BigDecimal.class,product)));
        assertEquals(0,new BigDecimal("0.9").compareTo(jdbc.queryForObject("SELECT seasonal_priority_score FROM product WHERE product_id=?",BigDecimal.class,product)));
    }
    @Test void dtoReadsObserveNativeCategoryEditsEvenWithStaleManagedEntity(){
        int category=categories.create(store,"Original","ACTIVE").id();
        transaction().executeWithoutResult(ignored->{
            var managed=em.find(categoryClass(),category);
            jdbc.update("UPDATE category SET name='Native edit' WHERE category_id=?",category);
            assertEquals("Original",ReflectionTestUtils.getField(managed,"name"));
            assertEquals("Native edit",categories.find(store,category).name());
            assertEquals("Native edit",categories.findAll(store).get(0).name());
            assertTrue(categories.update(store,category,"Adapter edit","INACTIVE"));
        });
        assertEquals("Adapter edit",categories.find(store,category).name());
    }
    @Test void statusWritePreservesNativeNameAndClearsStaleManagedState(){
        int category=categories.create(store,"Original","ACTIVE").id();
        transaction().executeWithoutResult(ignored->{
            var managed=em.find(categoryClass(),category);
            jdbc.update("UPDATE category SET name='Native name' WHERE category_id=?",category);
            assertTrue(categories.updateStatus(store,category,"INACTIVE"));
            assertFalse(em.contains(managed));assertEquals("Native name",categories.find(store,category).name());
        });
        assertEquals("INACTIVE",categories.find(store,category).status());
    }
    @Test void productStatusContextClearDoesNotLoseCategoryEdits(){
        int category=categories.create(store,"Original","ACTIVE").id();int product=product(category);
        transaction().executeWithoutResult(ignored->{
            var managed=em.find(categoryClass(),category);ReflectionTestUtils.setField(managed,"name","Pending managed change");
            assertTrue(products.updateStatus(store,product,"INACTIVE"));assertFalse(em.contains(managed));
            assertEquals("Pending managed change",jdbc.queryForObject("SELECT name FROM category WHERE category_id=?",String.class,category));
            assertTrue(categories.update(store,category,"Through adapter after clear","ACTIVE"));
        });
        assertEquals("Through adapter after clear",categories.find(store,category).name());assertEquals("INACTIVE",products.find(store,product).status());
    }
    @Test void dirtyManagedCategoryStoreCannotTransferOwnership(){
        int category=categories.create(store,"Immutable","ACTIVE").id();
        transaction().executeWithoutResult(ignored->{
            var managed=em.find(categoryClass(),category);ReflectionTestUtils.setField(managed,"storeId",foreign);
            em.flush();em.clear();assertEquals(store,ReflectionTestUtils.getField(em.find(categoryClass(),category),"storeId"));
        });
        assertEquals(store,jdbc.queryForObject("SELECT store_id FROM category WHERE category_id=?",Integer.class,category));assertNull(categories.find(foreign,category));
    }
    @Test void referencedCategoryStatusAndNamePreserveProductAndInventory(){
        int category=categories.create(store,"Referenced","ACTIVE").id();int product=product(category);
        batches.create(product,"Referenced batch",Instant.now(),null,4);
        categories.update(store,category,"Renamed referenced","INACTIVE");
        assertEquals(category,products.find(store,product).category().id());assertEquals("Renamed referenced",products.find(store,product).category().name());
        assertEquals("ACTIVE",products.find(store,product).status());assertEquals(4,products.find(store,product).stockQuantity());
        int otherCategory=categories.create(foreign,"Foreign","ACTIVE").id();
        assertThrows(DataIntegrityViolationException.class,()->products.update(store,product,"NEW-SKU","Product",otherCategory,"each",false,BigDecimal.TEN,BigDecimal.ONE,"ACTIVE",0));
    }
    @Test void invalidPersistenceValuesStillFailConstraints(){
        assertThrows(org.springframework.dao.DataAccessException.class,()->categories.create(store,"Bad status","ARCHIVED"));
        assertThrows(NullPointerException.class,()->categories.create(store,null,"ACTIVE"));
    }
    @Test void existingCategoryApiContractValidationAndRolesRemain()throws Exception{
        String admin=token(store,"STORE_ADMIN"),other=token(foreign,"STORE_ADMIN"),merchant=token(store,"MERCHANDISER");
        mvc.perform(post("/api/categories").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Forged\",\"status\":\"ACTIVE\",\"storeId\":"+foreign+"}"))
                .andExpect(status().isBadRequest());
        var result=mvc.perform(post("/api/categories").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  API category  \",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("API category")).andReturn();
        int id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).path("id").asInt();
        assertEquals(store,jdbc.queryForObject("SELECT store_id FROM category WHERE category_id=?",Integer.class,id));
        mvc.perform(get("/api/categories/{id}",id).header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
        mvc.perform(put("/api/categories/{id}",id).header("Authorization","Bearer "+other).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Steal\",\"status\":\"ACTIVE\"}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/categories/{id}/status",id).header("Authorization","Bearer "+other).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVE\"}")).andExpect(status().isNotFound());
        mvc.perform(get("/api/categories").header("Authorization","Bearer "+other)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/categories/{id}",id).header("Authorization","Bearer "+merchant)).andExpect(status().isOk());
        mvc.perform(post("/api/categories").header("Authorization","Bearer "+merchant).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\",\"status\":\"ACTIVE\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/categories/{id}",id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/categories").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"status\":\"ACTIVE\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/categories").header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Invalid\",\"status\":\"ARCHIVED\"}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/categories/{id}",id).header("Authorization","Bearer "+admin)).andExpect(status().isMethodNotAllowed());
        assertNotNull(categories.find(store,id));
    }
}
