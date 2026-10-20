package vn.edu.fpt.paspm.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;
import vn.edu.fpt.paspm.utility.TransactionSource;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:f04imports;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=01234567890123456789012345678901",
        "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=", "paspm.store-time-zone=Asia/Ho_Chi_Minh"
})
@AutoConfigureMockMvc
class TransactionImportIntegrationTest {
    private static final String CSV = """
            source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status
            good-order,2026-10-19T12:00:00+07:00,MILK,2,10.00,6.00,COMPLETED
            good-order,2026-10-19T12:00:00+07:00,COFFEE,1,4.00,2.00,COMPLETED
            cancelled-order,2026-10-19T13:00:00+07:00,MILK,1,10.00,6.00,CANCELLED
            invalid-order,2026-10-19T14:00:00+07:00,UNKNOWN,1,10.00,6.00,COMPLETED
            """;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    @Autowired TransactionSource transactionSource;

    private int storeId;
    private int foreignStoreId;
    private int productId;
    private String adminToken;
    private String merchandiserToken;
    private String foreignAdminToken;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM utility_matrix_item");
        jdbc.update("DELETE FROM utility_snapshot");
        jdbc.update("DELETE FROM import_staging_row");
        jdbc.update("DELETE FROM transaction_item");
        jdbc.update("DELETE FROM sales_transaction");
        jdbc.update("DELETE FROM import_batch");
        jdbc.update("DELETE FROM api_connection");
        jdbc.update("DELETE FROM inventory_batch");
        jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM category");

        jdbc.update("INSERT INTO store(name) VALUES (?)", "F04 Store " + UUID.randomUUID());
        storeId = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        jdbc.update("INSERT INTO store(name) VALUES (?)", "F04 Foreign Store " + UUID.randomUUID());
        foreignStoreId = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,?,'each',FALSE,10,6,'ACTIVE')",
                storeId, "MILK", "F04 Milk");
        productId = jdbc.queryForObject("SELECT product_id FROM product WHERE store_id=? AND sku='MILK'", Integer.class, storeId);
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,?,'each',FALSE,4,2,'ACTIVE')",
                storeId, "COFFEE", "F04 Coffee");
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,?,'each',FALSE,5,3,'ACTIVE')",
                foreignStoreId, "PRIVATE", "Foreign Product");
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity,status) VALUES (?,'F04-SEED',CURRENT_TIMESTAMP,12,12,'ACTIVE')", productId);
        adminToken = token("STORE_ADMIN", storeId);
        merchandiserToken = token("MERCHANDISER", storeId);
        foreignAdminToken = token("STORE_ADMIN", foreignStoreId);
    }

    @Test
    void stagesRawRowsPersistsCompleteOrdersAndNeverMutatesInventory() throws Exception {
        String response = mvc.perform(multipart("/api/transactions/imports/csv")
                        .file(csvFile(CSV))
                        .param("storeId", String.valueOf(foreignStoreId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.validCount").value(3))
                .andExpect(jsonPath("$.rejectedCount").value(1))
                .andExpect(jsonPath("$.totalOrderCount").value(3))
                .andExpect(jsonPath("$.acceptedOrderCount").value(2))
                .andExpect(jsonPath("$.rejectedOrderCount").value(1))
                .andReturn().getResponse().getContentAsString();

        int batchId = json.readTree(response).get("importBatchId").asInt();
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM import_staging_row WHERE import_batch_id=?", Integer.class, batchId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?", Integer.class, storeId));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM transaction_item", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM transaction_item WHERE product_id=? AND quantity=1", Integer.class, productId));
        assertEquals(12, jdbc.queryForObject("SELECT remaining_quantity FROM inventory_batch WHERE product_id=?", Integer.class, productId));
        assertTrue(jdbc.queryForObject("SELECT f03_eligible FROM sales_transaction WHERE source_order_id='good-order'", Boolean.class));
        assertEquals(false, jdbc.queryForObject("SELECT f03_eligible FROM sales_transaction WHERE source_order_id='cancelled-order'", Boolean.class));
        assertEquals("PROCESSED", jdbc.queryForObject("SELECT status FROM import_staging_row WHERE import_batch_id=? AND `row_number`=2", String.class, batchId));
        var f03Rows = transactionSource.load(storeId, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-19"));
        assertEquals("F04_DATABASE_V1", transactionSource.sourceType());
        assertEquals(1, f03Rows.size());
        assertEquals("good-order", jdbc.queryForObject("SELECT source_order_id FROM sales_transaction WHERE transaction_id=?", String.class,
                f03Rows.get(0).transactionId()));
        assertEquals(2, f03Rows.get(0).items().size());
        assertTrue(f03Rows.get(0).items().stream().allMatch(item -> item.sourceUtility() == null));
        mvc.perform(get("/api/transactions/imports/{id}/errors", batchId)
                        .header("Authorization", "Bearer " + merchandiserToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].rowNumber").value(5));
    }

    @Test
    void duplicateFileIsIdempotentAndConflictingOrderDoesNotOverwriteHistory() throws Exception {
        upload(adminToken, CSV).andExpect(status().isCreated()).andExpect(jsonPath("$.acceptedOrderCount").value(2));
        upload(adminToken, CSV).andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedOrderCount").value(0))
                .andExpect(jsonPath("$.duplicateOrderCount").value(2))
                .andExpect(jsonPath("$.conflictOrderCount").value(0));
        String changed = """
                source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status
                good-order,2026-10-19T12:00:00+07:00,MILK,2,11.00,6.00,COMPLETED
                """;
        upload(adminToken, changed).andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedOrderCount").value(0))
                .andExpect(jsonPath("$.conflictOrderCount").value(1));

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?", Integer.class, storeId));
        assertEquals(new java.math.BigDecimal("10.00"), jdbc.queryForObject(
                "SELECT unit_price FROM transaction_item i JOIN sales_transaction t ON t.transaction_id=i.transaction_id WHERE t.source_order_id='good-order' AND i.product_id=?",
                java.math.BigDecimal.class, productId));
    }

    @Test
    void rejectsEntireOrderWhenAnyLineFailsButPersistsOtherOrdersInTheBatch() throws Exception {
        String csv = """
                source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status
                atomic-order,2026-10-19T12:00:00+07:00,MILK,2,10.00,6.00,COMPLETED
                atomic-order,2026-10-19T12:00:00+07:00,UNKNOWN,1,4.00,2.00,COMPLETED
                independent-order,2026-10-19T13:00:00+07:00,COFFEE,1,4.00,2.00,COMPLETED
                """;
        String response = upload(adminToken, csv).andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.validCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(2))
                .andExpect(jsonPath("$.acceptedOrderCount").value(1))
                .andExpect(jsonPath("$.rejectedOrderCount").value(1))
                .andReturn().getResponse().getContentAsString();

        int batchId = json.readTree(response).get("importBatchId").asInt();
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sales_transaction WHERE store_id=? AND source_order_id='atomic-order'", Integer.class, storeId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sales_transaction WHERE store_id=? AND source_order_id='independent-order'", Integer.class, storeId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM transaction_item i JOIN sales_transaction t ON t.transaction_id=i.transaction_id WHERE t.source_order_id='atomic-order'", Integer.class));
        assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM import_staging_row WHERE import_batch_id=? AND `row_number`=2", String.class, batchId));
        assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM import_staging_row WHERE import_batch_id=? AND `row_number`=3", String.class, batchId));
    }

    @Test
    void acceptsCsvContentTypeWithCharsetParameter() throws Exception {
        var file = new MockMultipartFile("file", "charset.csv", "text/csv; charset=UTF-8",
                ("source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status\n"
                        + "charset-order,2026-10-19T12:00:00+07:00,MILK,1,10.00,6.00,COMPLETED\n")
                        .getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/transactions/imports/csv").file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedOrderCount").value(1));
    }

    @Test
    void openApiAdvertisesCsvUploadAsMultipartBinary() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/transactions/imports/csv'].post.requestBody.content['multipart/form-data'].schema.properties.file.format")
                        .value("binary"));
    }

    @Test
    void importAndTransactionReadsAreRoleCheckedAndStoreScoped() throws Exception {
        mvc.perform(multipart("/api/transactions/imports/csv").file(csvFile(CSV)))
                .andExpect(status().isUnauthorized());
        upload(merchandiserToken, CSV).andExpect(status().isForbidden());

        String foreignCsv = """
                source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status
                foreign-order,2026-10-19T12:00:00+07:00,PRIVATE,1,5.00,3.00,COMPLETED
                """;
        String batchResponse = upload(foreignAdminToken, foreignCsv).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int foreignBatchId = json.readTree(batchResponse).get("importBatchId").asInt();
        int foreignTransactionId = jdbc.queryForObject(
                "SELECT transaction_id FROM sales_transaction WHERE store_id=? AND source_order_id='foreign-order'",
                Integer.class, foreignStoreId);

        mvc.perform(get("/api/transactions/imports/{id}", foreignBatchId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transactions/{id}", foreignTransactionId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer " + foreignAdminToken)
                        .param("status", "completed").param("page", "0").param("pageSize", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
        mvc.perform(get("/api/transactions/{id}", foreignTransactionId)
                        .header("Authorization", "Bearer " + foreignAdminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].unitPrice").value(5.0));
    }

    private MockMultipartFile csvFile(String contents) {
        return new MockMultipartFile("file", "transactions.csv", "text/csv", contents.getBytes(StandardCharsets.UTF_8));
    }

    private org.springframework.test.web.servlet.ResultActions upload(String token, String contents) throws Exception {
        return mvc.perform(multipart("/api/transactions/imports/csv").file(csvFile(contents))
                .header("Authorization", "Bearer " + token));
    }

    private String token(String role, Integer store) {
        User user = users.createPendingUser("F04 tester", UUID.randomUUID() + "@example.test", role, store, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?", java.sql.Timestamp.from(Instant.now()), user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }
}
