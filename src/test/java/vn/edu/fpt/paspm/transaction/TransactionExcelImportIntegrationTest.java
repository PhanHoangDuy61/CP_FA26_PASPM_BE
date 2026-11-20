package vn.edu.fpt.paspm.transaction;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import vn.edu.fpt.paspm.transaction.importing.TransactionImportRepository;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;
import vn.edu.fpt.paspm.transaction.importing.TransactionCsvParser;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:f04excel;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER",
    "spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true",
    "paspm.jwt-secret=01234567890123456789012345678901","paspm.otp-secret=test-only-otp-key-with-at-least-thirty-two-bytes",
    "paspm.bootstrap-email=","paspm.bootstrap-password=","paspm.store-time-zone=Asia/Ho_Chi_Minh",
    "paspm.transaction.import.max-rows=100","paspm.transaction.import.max-file-bytes=65536"})
@AutoConfigureMockMvc
class TransactionExcelImportIntegrationTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired UserRepository users;@Autowired AuthService auth;@Autowired ObjectMapper json;
    @MockitoSpyBean TransactionImportRepository repository;
    int store,foreignStore,product;String admin,merchant,foreign;
    static final String TIME="2026-10-19T12:00:00+07:00";
    @BeforeEach void fixture(){
        for(String table:List.of("utility_matrix_item","utility_snapshot_storage","utility_snapshot","import_staging_row","transaction_item","sales_transaction","import_batch","api_connection","inventory_batch","product","category"))jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO store(name) VALUES ('Excel synthetic store')");store=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        jdbc.update("INSERT INTO store(name) VALUES ('Excel other store')");foreignStore=jdbc.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,'00123','Synthetic zero-prefixed SKU','each',FALSE,10,6,'ACTIVE')",store);
        product=jdbc.queryForObject("SELECT MAX(product_id) FROM product",Integer.class);
        jdbc.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,'PRIVATE','Other SKU','each',FALSE,10,6,'ACTIVE')",foreignStore);
        jdbc.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,initial_quantity,remaining_quantity,status) VALUES (?,'EXCEL-FIXTURE',CURRENT_TIMESTAMP,12,12,'ACTIVE')",product);
        admin=token("STORE_ADMIN",store);merchant=token("MERCHANDISER",store);foreign=token("STORE_ADMIN",foreignStore);
    }
    String token(String role,int owner){var u=users.createPendingUser("Synthetic Excel",UUID.randomUUID()+"@example.test",role,owner,null,null);jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?",u.id());return auth.token(users.findById(u.id())).accessToken();}
    Object[] row(String id){return new Object[]{id,TIME,"00123",1d,10d,6d,"COMPLETED"};}
    byte[] workbook(String extension,List<Object[]> rows,int emptySheets,boolean secondDataSheet)throws Exception{
        try(Workbook book=extension.equals("xls")?new HSSFWorkbook():new XSSFWorkbook();var out=new ByteArrayOutputStream()){
            Sheet sheet=book.createSheet("Transactions");fill(sheet.createRow(0),TransactionCsvParser.HEADERS.toArray());
            int i=1;for(Object[] row:rows)fill(sheet.createRow(i++),row);
            for(int n=0;n<emptySheets;n++)book.createSheet("Empty"+n);
            if(secondDataSheet)fill(book.createSheet("Second").createRow(0),TransactionCsvParser.HEADERS.toArray());
            book.write(out);return out.toByteArray();
        }
    }
    void fill(Row row,Object[] fields){for(int c=0;c<fields.length;c++){Object v=fields[c];if(v==null)continue;Cell cell=row.createCell(c);if(v instanceof Number n)cell.setCellValue(n.doubleValue());else if(v instanceof Boolean b)cell.setCellValue(b);else cell.setCellValue(v.toString());}}
    MockMultipartFile file(String ext,byte[] bytes){return new MockMultipartFile("file","transactions."+ext,ext.equals("xls")?"application/vnd.ms-excel":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes);}
    ResultActions upload(String token,String ext,byte[] bytes)throws Exception{return mvc.perform(multipart("/api/transactions/imports/excel").file(file(ext,bytes)).header("Authorization","Bearer "+token));}
    JsonNode body(ResultActions action)throws Exception{return json.readTree(action.andReturn().getResponse().getContentAsString());}
    int count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
    @Test void validXlsxUsesExistingSummaryAndPreservesLeadingZeros()throws Exception{
        JsonNode result=body(upload(admin,"xlsx",workbook("xlsx",List.<Object[]>of(row("0001")),0,false)).andExpect(status().isCreated()).andExpect(jsonPath("$.acceptedOrderCount").value(1)));
        assertEquals("COMPLETED",result.get("status").asText());
        assertEquals("0001",jdbc.queryForObject("SELECT source_order_id FROM sales_transaction WHERE store_id=?",String.class,store));
        assertEquals("CSV_UPLOAD",jdbc.queryForObject("SELECT source_system FROM sales_transaction WHERE store_id=?",String.class,store));
        assertEquals(12,count("SELECT remaining_quantity FROM inventory_batch WHERE product_id=?",product));
    }

    byte[] edited(String ext,java.util.function.Consumer<Workbook> change)throws Exception{
        try(Workbook book=WorkbookFactory.create(new ByteArrayInputStream(workbook(ext,List.<Object[]>of(row("0001")),0,false)));var out=new ByteArrayOutputStream()){
            change.accept(book);book.write(out);return out.toByteArray();
        }
    }
    ResultActions csv(String token,String id,String time,int quantity)throws Exception{
        String data=String.join(",",TransactionCsvParser.HEADERS)+"\n"+id+","+time+",00123,"+quantity+",10.00,6.00,COMPLETED\n";
        return mvc.perform(multipart("/api/transactions/imports/csv").file(new MockMultipartFile("file","historical.csv","text/csv",data.getBytes(StandardCharsets.UTF_8))).header("Authorization","Bearer "+token));
    }
    @ParameterizedTest @ValueSource(strings={"xlsx","xls"})
    void bothExcelFormatsKeepOrderAtomicityAndRawInvalidIdentifiers(String ext)throws Exception{
        String invalid="x".repeat(151);
        JsonNode result=body(upload(admin,ext,workbook(ext,List.of(row("0001"),row(invalid)),1,false)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.acceptedOrderCount").value(1)).andExpect(jsonPath("$.rejectedOrderCount").value(1))
            .andExpect(jsonPath("$.totalCount").value(2)).andExpect(jsonPath("$.rejectedCount").value(1)));
        int batch=result.get("importBatchId").asInt();
        String raw=jdbc.queryForObject("SELECT raw_data FROM import_staging_row WHERE import_batch_id=? AND `row_number`=3",String.class,batch);
        assertEquals(invalid,json.readTree(raw).get("source_order_id").asText());
        assertEquals(1,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));
        assertEquals(12,count("SELECT remaining_quantity FROM inventory_batch WHERE product_id=?",product));
    }
    @ParameterizedTest @ValueSource(strings={"xlsx","xls"})
    void csvThenExcelIsDuplicateAcrossOffsetsAndNeverRelabelsHistory(String ext)throws Exception{
        csv(admin,"0001",TIME,1).andExpect(status().isCreated());
        String before=jdbc.queryForObject("SELECT content_sha256 FROM sales_transaction WHERE store_id=?",String.class,store);
        Object[] same=row("0001");same[1]="2026-10-19T05:00:00Z";
        upload(admin,ext,workbook(ext,List.<Object[]>of(same),0,false)).andExpect(status().isCreated()).andExpect(jsonPath("$.duplicateOrderCount").value(1));
        Object[] changed=row("0001");changed[3]=2d;
        upload(admin,ext,workbook(ext,List.<Object[]>of(changed),0,false)).andExpect(status().isCreated()).andExpect(jsonPath("$.conflictOrderCount").value(1));
        assertEquals(1,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));
        assertEquals(before,jdbc.queryForObject("SELECT content_sha256 FROM sales_transaction WHERE store_id=?",String.class,store));
        assertEquals("CSV_UPLOAD",jdbc.queryForObject("SELECT source_system FROM sales_transaction WHERE store_id=?",String.class,store));
        assertEquals(1,count("SELECT quantity FROM transaction_item WHERE store_id=?",store));
    }
    @ParameterizedTest @ValueSource(strings={"xlsx","xls"})
    void excelThenCsvAndRepeatedExcelShareTheExistingIdentity(String ext)throws Exception{
        byte[] file=workbook(ext,List.<Object[]>of(row("0001")),0,false);
        upload(admin,ext,file).andExpect(status().isCreated()).andExpect(jsonPath("$.acceptedOrderCount").value(1));
        upload(admin,ext,file).andExpect(status().isCreated()).andExpect(jsonPath("$.duplicateOrderCount").value(1));
        csv(admin,"0001",TIME,1).andExpect(status().isCreated()).andExpect(jsonPath("$.duplicateOrderCount").value(1));
        csv(admin,"0001",TIME,2).andExpect(status().isCreated()).andExpect(jsonPath("$.conflictOrderCount").value(1));
        assertEquals(1,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));
    }
    @ParameterizedTest @ValueSource(strings={"xlsx","xls"})
    void numericIdentitySkuTimestampAndFormulasAreStructuralErrors(String ext)throws Exception{
        for(int column:new int[]{0,1,2}){
            byte[] bad=edited(ext,b->b.getSheetAt(0).getRow(1).getCell(column).setCellValue(123d));
            upload(admin,ext,bad).andExpect(status().isBadRequest());
        }
        upload(admin,ext,edited(ext,b->b.getSheetAt(0).getRow(1).getCell(3).setCellFormula("1+1"))).andExpect(status().isBadRequest());
        assertEquals(0,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));
    }
    @ParameterizedTest @ValueSource(strings={"xlsx","xls"})
    void dateCellsAndBooleanCellsAreNotCoercedIntoApprovedFields(String ext)throws Exception{
        upload(admin,ext,edited(ext,b->{var cell=b.getSheetAt(0).getRow(1).getCell(1);cell.setCellValue(Date.from(Instant.parse("2026-10-19T05:00:00Z")));var style=b.createCellStyle();style.setDataFormat(b.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));cell.setCellStyle(style);})).andExpect(status().isBadRequest());
        upload(admin,ext,edited(ext,b->b.getSheetAt(0).getRow(1).getCell(6).setCellValue(true))).andExpect(status().isBadRequest());
        assertEquals(0,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));
    }
    @ParameterizedTest @ValueSource(strings={"xlsx","xls"})
    void invalidTextDateAndFractionalNumericQuantityUseSharedOrderValidation(String ext)throws Exception{
        Object[] date=row("bad-date");date[1]="2026-10-19T12:00:00";
        Object[] quantity=row("fractional");quantity[3]=1.5d;
        upload(admin,ext,workbook(ext,List.of(row("good"),date,quantity),0,false)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.acceptedOrderCount").value(1)).andExpect(jsonPath("$.rejectedOrderCount").value(2));
        assertEquals(1,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));
    }
    @ParameterizedTest @ValueSource(strings={"xlsx","xls"})
    void blankSheetsAndBlankRowsAreAllowedButMultipleNonemptySheetsRejectFile(String ext)throws Exception{
        upload(admin,ext,workbook(ext,List.of(row("good"),new Object[7]),2,false)).andExpect(status().isCreated()).andExpect(jsonPath("$.totalCount").value(1));
        upload(admin,ext,workbook(ext,List.<Object[]>of(row("other")),0,true)).andExpect(status().isBadRequest());
        assertEquals(1,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));
    }
    @ParameterizedTest @ValueSource(strings={"xlsx","xls"})
    void headersMustMatchExactly(String ext)throws Exception{
        for(String value:new String[]{"sku","","extra_header"})
            upload(admin,ext,edited(ext,b->b.getSheetAt(0).getRow(0).getCell(0).setCellValue(value))).andExpect(status().isBadRequest());
        upload(admin,ext,edited(ext,b->b.getSheetAt(0).getRow(0).createCell(7).setCellValue("extra"))).andExpect(status().isBadRequest());
        assertEquals(0,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));
    }
    @ParameterizedTest @ValueSource(strings={"xlsx","xls"})
    void malformedEmptyAndConfiguredLimitsReturnExistingErrorEnvelope(String ext)throws Exception{
        upload(admin,ext,"not-a-workbook".getBytes(StandardCharsets.UTF_8)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isString());
        upload(admin,ext,new byte[0]).andExpect(status().isBadRequest());
        upload(admin,ext,workbook(ext,List.of(),0,false)).andExpect(status().isBadRequest());
        upload(admin,ext,new byte[65537]).andExpect(status().isPayloadTooLarge());
        var rows=new ArrayList<Object[]>();for(int i=0;i<101;i++)rows.add(row("row-"+i));
        upload(admin,ext,workbook(ext,rows,0,false)).andExpect(status().isBadRequest());
        assertEquals(0,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));
    }
    @ParameterizedTest @ValueSource(strings={"xlsx","xls"})
    void excelUploadEnforcesRoleScopeAndIgnoresForgedStore(String ext)throws Exception{
        byte[] data=workbook(ext,List.<Object[]>of(row("0001")),0,false);
        mvc.perform(multipart("/api/transactions/imports/excel").file(file(ext,data))).andExpect(status().isUnauthorized());
        upload("bad-token",ext,data).andExpect(status().isUnauthorized());upload(merchant,ext,data).andExpect(status().isForbidden());
        JsonNode response=body(mvc.perform(multipart("/api/transactions/imports/excel").file(file(ext,data)).param("storeId",String.valueOf(foreignStore)).header("Authorization","Bearer "+admin)).andExpect(status().isCreated()));
        int batch=response.get("importBatchId").asInt();
        assertEquals(store,count("SELECT store_id FROM import_batch WHERE import_batch_id=?",batch));
        mvc.perform(get("/api/transactions/imports/{id}",batch).header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
        mvc.perform(get("/api/transactions/imports/{id}/errors",batch).header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
        assertEquals(0,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",foreignStore));
        assertEquals(12,count("SELECT remaining_quantity FROM inventory_batch WHERE product_id=?",product));
    }
    @ParameterizedTest @ValueSource(strings={"xlsx","xls"})
    void excelDatabaseFailureRollsBackOrderAndDoesNotMutateInventory(String ext)throws Exception{
        doAnswer(call->{Object result=call.callRealMethod();throw new DataIntegrityViolationException("synthetic Excel post-write failure");}).when(repository).insertItems(anyInt(),anyInt(),anyList());
        upload(admin,ext,workbook(ext,List.<Object[]>of(row("rollback")),0,false)).andExpect(status().isConflict());
        assertEquals(0,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));assertEquals(0,count("SELECT COUNT(*) FROM transaction_item WHERE store_id=?",store));
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM import_batch WHERE store_id=?",String.class,store));
        assertEquals(12,count("SELECT remaining_quantity FROM inventory_batch WHERE product_id=?",product));
    }
    @Test void openApiDocumentsBothMultipartImportRoutes()throws Exception{
        var result=mvc.perform(get("/v3/api-docs").with(request->{request.setServerPort(8080);return request;})).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/transactions/imports/csv'].post.requestBody.content['multipart/form-data'].schema.properties.file.format").value("binary"))
            .andExpect(jsonPath("$.paths['/api/transactions/imports/excel'].post.requestBody.content['multipart/form-data'].schema.properties.file.format").value("binary")).andReturn();
        if(Boolean.getBoolean("paspm.test.export-openapi") && getClass()==TransactionExcelImportIntegrationTest.class)
            java.nio.file.Files.writeString(java.nio.file.Path.of("docs/api/openapi.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(json.readTree(result.getResponse().getContentAsString()))+"\n");
    }
    @Test void publishedTemplatesUseTheActualRouteAndSharedIdentity()throws Exception{
        for(String ext:List.of("xlsx","xls")){
            byte[] bytes=java.nio.file.Files.readAllBytes(java.nio.file.Path.of("docs/features/F04/examples/transactions-template."+ext));
            upload(admin,ext,bytes).andExpect(status().isCreated());
        }
        assertEquals(1,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));
        assertEquals("0001",jdbc.queryForObject("SELECT source_order_id FROM sales_transaction WHERE store_id=?",String.class,store));
    }
    @Test void extremeMoneyExponentsArePerOrderErrorsAcrossBothFormats()throws Exception{
        for(String ext:List.of("xlsx","xls")){
            Object[] bad=row("unsafe-"+ext);bad[4]="1E100000000";
            upload(admin,ext,workbook(ext,List.of(row("safe-"+ext),bad),0,false)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.acceptedOrderCount").value(1)).andExpect(jsonPath("$.rejectedOrderCount").value(1));
        }
        assertEquals(2,count("SELECT COUNT(*) FROM sales_transaction WHERE store_id=?",store));
    }
}
