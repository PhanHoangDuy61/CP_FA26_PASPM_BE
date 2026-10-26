package vn.edu.fpt.paspm.transaction;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.fpt.paspm.transaction.importing.TransactionImportRepository;
import vn.edu.fpt.paspm.transaction.importing.TransactionOrderValidator.ValidatedLine;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:f04correctness;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=01234567890123456789012345678901",
        "paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=", "paspm.store-time-zone=Asia/Ho_Chi_Minh"
})
@AutoConfigureMockMvc
class TransactionImportCorrectnessIntegrationTest {
    static final String HEADER = "source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status\n";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    @MockitoSpyBean TransactionImportRepository repository;
    int storeId, otherStoreId, productId;
    String admin, merchandiser, foreignAdmin;

    @BeforeEach
    void fixture() {
        jdbc.update("DELETE FROM utility_matrix_item");
        jdbc.update("DELETE FROM utility_snapshot_storage");
        jdbc.update("DELETE FROM utility_snapshot");
        jdbc.update("DELETE FROM import_staging_row");
        jdbc.update("DELETE FROM transaction_item");
        jdbc.update("DELETE FROM sales_transaction");
        jdbc.update("DELETE FROM import_batch");
        jdbc.update("DELETE FROM api_connection");
        jdbc.update("DELETE FROM inventory_batch");
        jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM category");
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Correctness Store " + UUID.randomUUID());
        storeId = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        jdbc.update("INSERT INTO store(name) VALUES (?)", "Other Store " + UUID.randomUUID());
        otherStoreId = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        product(storeId, "MILK"); product(storeId, "COFFEE"); product(otherStoreId, "PRIVATE");
        productId = jdbc.queryForObject("SELECT product_id FROM product WHERE store_id=? AND sku='MILK'", Integer.class, storeId);
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity,status) VALUES (?,'PRESERVE',CURRENT_TIMESTAMP,12,12,'ACTIVE')", productId);
        admin = token("STORE_ADMIN", storeId); merchandiser = token("MERCHANDISER", storeId);
        foreignAdmin = token("STORE_ADMIN", otherStoreId);
    }
    void product(int store, String sku) {
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,?,?,'each',FALSE,10,6,'ACTIVE')", store, sku, sku);
    }
    String token(String role, int store) {
        User user = users.createPendingUser("Correctness tester", UUID.randomUUID()+"@example.test", role, store, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?", java.sql.Timestamp.from(Instant.now()), user.id());
        return auth.token(users.findById(user.id())).accessToken();
    }
    String row(String id) { return row(id, "MILK", 1); }
    String row(String id, String sku, int quantity) {
        return id+",2026-10-19T12:00:00+07:00,"+sku+","+quantity+",10.00,6.00,COMPLETED\n";
    }
    ResultActions upload(String token, String csv) throws Exception {
        return mvc.perform(multipart("/api/transactions/imports/csv")
                .file(new MockMultipartFile("file", "correctness.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer "+token));
    }
    JsonNode body(ResultActions action) throws Exception { return json.readTree(action.andReturn().getResponse().getContentAsString()); }
    int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    JsonNode raw(int batch, int row) throws Exception {
        JsonNode node = json.readTree(jdbc.queryForObject("SELECT raw_data FROM import_staging_row WHERE import_batch_id=? AND `row_number`=?", String.class, batch, row));
        return node.isTextual() ? json.readTree(node.asText()) : node;
    }

    @Test
    void validOrderSurvivesUnrelated151CharacterIdentifierAndRawEvidenceIsExact() throws Exception {
        String invalid = "x".repeat(151);
        JsonNode result = body(upload(admin, HEADER+row("valid")+row(invalid))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.totalCount").value(2)).andExpect(jsonPath("$.validCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(1)).andExpect(jsonPath("$.acceptedOrderCount").value(1))
                .andExpect(jsonPath("$.rejectedOrderCount").value(1)));
        int batch = result.get("importBatchId").asInt();
        assertEquals(1, count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?", storeId));
        assertEquals("valid", jdbc.queryForObject("SELECT source_order_id FROM sales_transaction WHERE store_id=?", String.class, storeId));
        assertEquals(invalid, raw(batch, 3).get("source_order_id").asText());
        assertNull(jdbc.queryForObject("SELECT source_order_id FROM import_staging_row WHERE import_batch_id=? AND `row_number`=3", String.class, batch));
        mvc.perform(get("/api/transactions/imports/{id}/errors", batch).header("Authorization", "Bearer "+admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].errorDetails").value(org.hamcrest.Matchers.containsString("Order ID is too long")));
        assertEquals(12, count("SELECT remaining_quantity FROM inventory_batch WHERE product_id=?", productId));
    }

    static Stream<Arguments> identifiers() {
        return Stream.of(
                Arguments.of("ascii150", "a".repeat(150), true),
                Arguments.of("ascii151", "a".repeat(151), false),
                Arguments.of("trimmed150", "  " + "a".repeat(150) + "  ", true),
                Arguments.of("unicode150", "ế".repeat(150), true),
                Arguments.of("unicode151", "ế".repeat(151), false),
                Arguments.of("supplementary150Utf16", "😀".repeat(75), true),
                Arguments.of("supplementary152Utf16", "😀".repeat(76), false),
                Arguments.of("empty", "", false),
                Arguments.of("whitespace", "   ", false));
    }

    @ParameterizedTest(name="{0}")
    @MethodSource("identifiers")
    void identifierBoundariesUseExistingTrimAndUtf16LimitWithoutLosingRawData(String label, String id, boolean accepted) throws Exception {
        JsonNode result = body(upload(admin, HEADER + row(id)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedOrderCount").value(accepted ? 1 : 0))
                .andExpect(jsonPath("$.rejectedOrderCount").value(accepted ? 0 : 1)));
        int batch = result.get("importBatchId").asInt();
        assertEquals(id, raw(batch, 2).get("source_order_id").asText());
        assertEquals(accepted ? 1 : 0, count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?", storeId));
        String staged = jdbc.queryForObject("SELECT source_order_id FROM import_staging_row WHERE import_batch_id=?", String.class, batch);
        if (accepted) assertEquals(id.trim(), staged); else assertNull(staged);
        assertEquals(12, count("SELECT remaining_quantity FROM inventory_batch WHERE product_id=?", productId));
    }

    @Test
    void multipleValidAndInvalidOrdersKeepCorrectCountersAndPerOrderAtomicity() throws Exception {
        String longId = "l".repeat(151);
        String csv = HEADER + row("valid-a") + row(longId) + row(longId, "COFFEE", 2)
                + row("valid-b", "COFFEE", 2) + row("invalid-sku") + row("invalid-sku", "UNKNOWN", 1) + row("");
        JsonNode result = body(upload(admin, csv).andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCount").value(7)).andExpect(jsonPath("$.validCount").value(2))
                .andExpect(jsonPath("$.rejectedCount").value(5)).andExpect(jsonPath("$.totalOrderCount").value(5))
                .andExpect(jsonPath("$.acceptedOrderCount").value(2)).andExpect(jsonPath("$.rejectedOrderCount").value(3)));
        int batch = result.get("importBatchId").asInt();
        assertEquals(2, count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?", storeId));
        assertEquals(2, count("SELECT COUNT(*) FROM transaction_item WHERE store_id=?", storeId));
        assertEquals(0, count("SELECT COUNT(*) FROM sales_transaction WHERE source_order_id IN (?,?)", longId, "invalid-sku"));
        assertEquals(5, count("SELECT COUNT(*) FROM import_staging_row WHERE import_batch_id=? AND status='REJECTED'", batch));
        assertEquals(longId, raw(batch, 3).get("source_order_id").asText());
    }

    @Test
    void retryAndConflictingDuplicateNeverOverwriteHistoricalFacts() throws Exception {
        String csv = HEADER + row("immutable") + row("x".repeat(151));
        upload(admin, csv).andExpect(status().isCreated()).andExpect(jsonPath("$.acceptedOrderCount").value(1));
        String hash = jdbc.queryForObject("SELECT content_sha256 FROM sales_transaction WHERE store_id=?", String.class, storeId);
        upload(admin, csv).andExpect(status().isCreated()).andExpect(jsonPath("$.duplicateOrderCount").value(1))
                .andExpect(jsonPath("$.acceptedOrderCount").value(0)).andExpect(jsonPath("$.rejectedOrderCount").value(1));
        upload(admin, HEADER + row("immutable", "MILK", 2)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.conflictOrderCount").value(1)).andExpect(jsonPath("$.rejectedCount").value(1));
        assertEquals(1, count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?", storeId));
        assertEquals(hash, jdbc.queryForObject("SELECT content_sha256 FROM sales_transaction WHERE store_id=?", String.class, storeId));
        assertEquals(1, count("SELECT quantity FROM transaction_item WHERE store_id=?", storeId));
    }

    @Test
    void concurrentImportsOfSameIdentityPersistOnlyOneOrderAndReportDuplicate() throws Exception {
        CountDownLatch bothLookedUp = new CountDownLatch(2);
        doAnswer(call -> {
            Object existing = call.callRealMethod();
            if (existing == null) {
                bothLookedUp.countDown();
                assertTrue(bothLookedUp.await(15, TimeUnit.SECONDS), "Both imports must race after identity lookup");
            }
            return existing;
        }).when(repository).findExisting(eq(storeId), eq("concurrent"));
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> body(upload(admin, HEADER+row("concurrent")).andExpect(status().isCreated())));
            var two = executor.submit(() -> body(upload(admin, HEADER+row("concurrent")).andExpect(status().isCreated())));
            JsonNode a = one.get(30, TimeUnit.SECONDS), b = two.get(30, TimeUnit.SECONDS);
            assertEquals(1, a.get("acceptedOrderCount").asInt()+b.get("acceptedOrderCount").asInt());
            assertEquals(1, a.get("duplicateOrderCount").asInt()+b.get("duplicateOrderCount").asInt());
            assertEquals(1, count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?", storeId));
            assertEquals(1, count("SELECT COUNT(*) FROM transaction_item WHERE store_id=?", storeId));
            assertEquals(2, count("SELECT COUNT(*) FROM import_staging_row WHERE store_id=? AND status='PROCESSED'", storeId));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void databaseFailureRollsBackFailedOrderAndFailsBatchWithoutInventingPartialSuccess() throws Exception {
        doAnswer(call -> {
            Object result = call.callRealMethod();
            List<ValidatedLine> lines = call.getArgument(2);
            if (lines.stream().anyMatch(line -> line.quantity() == 777))
                throw new DataIntegrityViolationException("injected failure after item insert");
            return result;
        }).when(repository).insertItems(anyInt(), anyInt(), anyList());
        upload(admin, HEADER+row("already-committed")+row("rollback", "COFFEE", 777))
                .andExpect(status().isConflict());
        assertEquals(1, count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=? AND source_order_id='already-committed'", storeId));
        assertEquals(0, count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=? AND source_order_id='rollback'", storeId));
        assertEquals(1, count("SELECT COUNT(*) FROM transaction_item WHERE store_id=?", storeId));
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM import_batch WHERE store_id=?", String.class, storeId));
        assertEquals(2, count("SELECT COUNT(*) FROM import_staging_row WHERE store_id=?", storeId));
        assertEquals(12, count("SELECT remaining_quantity FROM inventory_batch WHERE product_id=?", productId));
    }

    @Test
    void invalidIdentifierCannotBypassAuthorizationOrStoreIsolation() throws Exception {
        String csv = HEADER+row("valid")+row("x".repeat(151));
        mvc.perform(multipart("/api/transactions/imports/csv").file(new MockMultipartFile("file", "a.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnauthorized());
        upload("invalid-token", csv).andExpect(status().isUnauthorized());
        upload(merchandiser, csv).andExpect(status().isForbidden());
        assertEquals(0, count("SELECT COUNT(*) FROM import_batch"));
        JsonNode own = body(upload(admin, csv).andExpect(status().isCreated()));
        mvc.perform(get("/api/transactions/imports/{id}", own.get("importBatchId").asInt()).header("Authorization", "Bearer "+foreignAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transactions/imports/{id}/errors", own.get("importBatchId").asInt()).header("Authorization", "Bearer "+foreignAdmin))
                .andExpect(status().isNotFound());
        upload(foreignAdmin, HEADER+row("foreign", "PRIVATE", 1)).andExpect(status().isCreated()).andExpect(jsonPath("$.acceptedOrderCount").value(1));
        upload(admin, HEADER+row("unknown", "PRIVATE", 1)).andExpect(status().isCreated()).andExpect(jsonPath("$.rejectedOrderCount").value(1));
        mvc.perform(get("/api/transactions").header("Authorization", "Bearer "+admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertEquals(1, count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?", storeId));
        assertEquals(1, count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?", otherStoreId));
    }

    @Test
    void largeIdentifierBeyondMysqlTextCapacityDoesNotDiscardValidOrder() throws Exception {
        String invalid = "z".repeat(70_000);
        JsonNode result = body(upload(admin, HEADER+row("valid-before-large-row")+row(invalid))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.totalCount").value(2)).andExpect(jsonPath("$.validCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(1)).andExpect(jsonPath("$.acceptedOrderCount").value(1))
                .andExpect(jsonPath("$.rejectedOrderCount").value(1)));
        int batch = result.get("importBatchId").asInt();
        assertEquals(invalid, raw(batch, 3).get("source_order_id").asText());
        assertNull(jdbc.queryForObject("SELECT source_order_id FROM import_staging_row WHERE import_batch_id=? AND `row_number`=3", String.class, batch));
        assertEquals(1, count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?", storeId));
        assertEquals("valid-before-large-row", jdbc.queryForObject("SELECT source_order_id FROM sales_transaction WHERE store_id=?", String.class, storeId));
        assertEquals(1, count("SELECT COUNT(*) FROM transaction_item WHERE store_id=?", storeId));
        assertEquals(12, count("SELECT remaining_quantity FROM inventory_batch WHERE product_id=?", productId));
    }
}
