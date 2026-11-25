package vn.edu.fpt.paspm.catalog.product.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.mining.MiningInputBuilder;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts;
import vn.edu.fpt.paspm.recommendation.RecommendationJsonVersions;
import vn.edu.fpt.paspm.recommendation.RecommendationVersionedContracts.EligibilityEvidence;
import vn.edu.fpt.paspm.recommendation.RecommendationVersionedContracts.EligibilityV2;
import vn.edu.fpt.paspm.utility.ExpiryScoreCalculator;
import vn.edu.fpt.paspm.utility.UtilityJsonVersions;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts;
import vn.edu.fpt.paspm.utility.UtilitySettingsRepository;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.*;

/** V26 (DEC-20261116-02): drop the three legacy product columns; nothing else changes and frozen JSON still reads back. */
class LegacyProductColumnsV26MigrationTest {
    static final List<String> DROPPED=List.of("expiry_push_days","stock_priority_score","seasonal_priority_score");
    @Test void h2() { upgrade("jdbc:h2:mem:v26_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""); }
    @Test void h2Fresh() { fresh("jdbc:h2:mem:v26f_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""); }
    @Test @EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
    void mysql() { var db=MySqlTestDatabase.create();upgrade(db.url(),db.user(),db.password()); }
    @Test @EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
    void mysqlFresh() { var db=MySqlTestDatabase.create();fresh(db.url(),db.user(),db.password()); }

    // Frozen payloads that still contain the legacy field names.
    static final String LINEAR="{\"config\":{\"enableStock\":true,\"enableExpiry\":false,\"enableSeason\":true,\"alpha\":0.7,\"beta\":0,\"gamma\":0.3,\"lambda\":0.5,\"expiryWindowDays\":null,\"version\":3,\"scoringVersion\":\"F03_LINEAR_V1\"},\"lambdaPolicy\":{\"lambdaMin\":0,\"lambdaMax\":0.5,\"version\":1},\"effectiveCoefficients\":{\"alpha\":0.7,\"beta\":0,\"gamma\":0.3},\"analysisDate\":\"2026-10-17\",\"source\":\"F04_DATABASE_V1\",\"utilityScale\":6,\"roundingMode\":\"HALF_UP\",\"itemMapping\":\"PRODUCT_ID_V1\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}";
    static final String BINARY=LINEAR.replace("F03_LINEAR_V1","EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1").replace("\"analysisDate\":\"2026-10-17\"","\"analysisDate\":\"2026-10-19\"");
    static final String W2="{\"config\":{\"alpha\":0.6,\"beta\":0.4,\"lambda\":0.3,\"salesWindowDays\":30,\"version\":8,\"scoringVersion\":\"W2_EXPIRY_SEASON_V1\"},\"lambdaPolicy\":{\"scoringVersion\":\"W2_EXPIRY_SEASON_V1\",\"lambdaMax\":0.5,\"version\":2},\"effectiveCoefficients\":{\"alpha\":0.6,\"beta\":0.4},\"analysisDate\":\"2026-11-15\",\"source\":\"F04_DATABASE_V1\",\"utilityScale\":6,\"roundingMode\":\"HALF_UP\",\"weightScale\":2,\"weightRoundingMode\":\"HALF_UP\",\"itemMapping\":\"PRODUCT_ID_V1\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"seasons\":[]}";
    static final String EXPIRY_V1="{\"expiryPushDays\":7,\"earliestEligibleExpiryDate\":\"2026-10-22\",\"daysRemaining\":3,\"asOfDate\":\"2026-10-19\",\"reason\":\"WITHIN_THRESHOLD\",\"batches\":[{\"productId\":1,\"batchId\":1,\"expiryDate\":\"2026-10-22\",\"remainingQuantity\":5,\"expiryPushDays\":7,\"daysRemaining\":3,\"pushRequired\":true,\"pushRequiredQuantity\":5,\"asOfDate\":\"2026-10-19\"}]}";
    static final String ELIGIBILITY_V1="{\"status\":\"ELIGIBLE\",\"products\":[{\"productId\":1,\"productStatus\":\"ACTIVE\",\"sellableQuantity\":5,\"status\":\"ELIGIBLE\",\"expiryPriorityScore\":1,\"expiryStatus\":\"AVAILABLE\",\"expiryPushDays\":7,\"warnings\":[]}],\"checkedAt\":\"2026-10-19T00:00:00Z\",\"asOfDate\":\"2026-10-19\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"warnings\":[],\"fingerprint\":\"v1-fingerprint\"}";
    static final String ELIGIBILITY_V2="{\"scoringVersion\":\"W2_EXPIRY_SEASON_V1\",\"status\":\"ELIGIBLE\",\"products\":[{\"productId\":1,\"productStatus\":\"ACTIVE\",\"sellableQuantity\":5,\"status\":\"ELIGIBLE\",\"expiryPriorityScore\":0.2,\"expiryStatus\":\"AVAILABLE\",\"warnings\":[]}],\"checkedAt\":\"2026-11-15T00:00:00Z\",\"asOfDate\":\"2026-11-15\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"warnings\":[],\"fingerprint\":\"v2-fingerprint\"}";
    static final String F06_EVIDENCE="{\"supportCount\":2,\"antecedentSupportCount\":3,\"confidence\":0.666667,\"supportRatio\":0.5,\"anchorSupportCount\":null,\"status\":\"AVAILABLE\"}";

    private void upgrade(String url,String user,String password) {
        Flyway.configure().dataSource(url,user,password).target("25").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        db.update("INSERT INTO store(name) VALUES ('V26 fixture')");
        int store=db.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        db.update("INSERT INTO app_user(store_id,role_id,email,status) SELECT ?,role_id,?,'ACTIVE' FROM role WHERE code='MERCHANDISER'",store,UUID.randomUUID()+"@example.invalid");
        int actor=db.queryForObject("SELECT MAX(user_id) FROM app_user",Integer.class);
        db.update("INSERT INTO category(store_id,name,status) VALUES (?,'Dairy','ACTIVE')",store);
        int category=db.queryForObject("SELECT MAX(category_id) FROM category",Integer.class);
        // Real-looking products, including non-default legacy values that V26 discards.
        db.update("INSERT INTO product(store_id,category_id,sku,name,unit,requires_expiry,selling_price,cost_price,status,expiry_push_days,stock_priority_score,seasonal_priority_score) VALUES (?,?,'MILK-1','Fresh milk','bottle',TRUE,12.50,8.25,'ACTIVE',7,0.8,0.9)",store,category);
        db.update("INSERT INTO product(store_id,category_id,sku,name,unit,requires_expiry,selling_price,cost_price,status,expiry_push_days,stock_priority_score,seasonal_priority_score) VALUES (?,NULL,'SOAP-1','Soap','each',FALSE,3,1.5,'INACTIVE',0,NULL,NULL)",store);
        int product=db.queryForObject("SELECT MIN(product_id) FROM product WHERE store_id=?",Integer.class,store);
        db.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status,push_day) VALUES (?,'B1',CURRENT_TIMESTAMP,'2026-12-10',10,6,'ACTIVE','2026-12-08')",product);
        db.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST','V26-1',CURRENT_TIMESTAMP,'COMPLETED',TRUE,25,?)",store,"f".repeat(64));
        int tx=db.queryForObject("SELECT MAX(transaction_id) FROM sales_transaction",Integer.class);
        var snapshots=new ArrayList<Integer>();
        for(String config:List.of(LINEAR,BINARY,W2)) {
            db.update("INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (?,?,'2026-10-01','2026-10-31','2026-10-19',?,?,?,4,6,'READY')",store,actor,config,"a".repeat(64),"b".repeat(64));
            snapshots.add(db.queryForObject("SELECT MAX(snapshot_id) FROM utility_snapshot",Integer.class));
        }
        db.update("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,quantity,base_utility,stock_score,expiry_score,seasonal_score,expiry_status,weight_value,weighted_utility,expiry_evidence) VALUES (?,?,?,1,4,0.8,1,0.9,'AVAILABLE',1,4,?)",snapshots.get(1),tx,product,EXPIRY_V1);
        String view="{\"snapshotId\":"+snapshots.get(1)+",\"storeId\":"+store+",\"createdBy\":"+actor+",\"rangeStart\":\"2026-10-01\",\"rangeEnd\":\"2026-10-31\",\"asOfDate\":\"2026-10-19\",\"configuration\":"+BINARY+",\"sourceHash\":\""+"a".repeat(64)+"\",\"matrixHash\":\""+"b".repeat(64)+"\",\"totalWeightedUtility\":4,\"roundingScale\":6,\"status\":\"READY\",\"createdAt\":\"2026-10-19T00:00:00Z\",\"warnings\":[]}";
        db.update("INSERT INTO mining_request(store_id,requested_by,utility_snapshot_id,request_key,payload_hash,algorithms,min_util_mode,min_util_value,min_util_spmf,input_snapshot_hash,frozen_input,utility_config_snapshot,status,comparison_status) VALUES (?,?,?,?,?,'EFIM','ABSOLUTE',1,1,?,?,?,'COMPLETED','NOT_REQUESTED')",store,actor,snapshots.get(1),UUID.randomUUID().toString(),"d".repeat(64),"c".repeat(64),"{\"frozen\":true}",view);
        long request=db.queryForObject("SELECT MAX(request_id) FROM mining_request",Long.class);
        var recommendations=new ArrayList<Long>();
        for(String eligibility:List.of(ELIGIBILITY_V1,ELIGIBILITY_V2)) {
            db.update("INSERT INTO recommendation(store_id,utility_snapshot_id,identity_hash,canonical_key,type,utility,rule_version,policy_version,policy_snapshot,evidence,generation_eligibility,explanation,created_by) VALUES (?,?,?,?,'BUNDLE',1,'F06_RULES_V1_1',1,'{}',?,?,'[]',?)",store,snapshots.get(1),MiningInputBuilder.sha(eligibility),"v26-"+recommendations.size(),F06_EVIDENCE,eligibility,actor);
            long rec=db.queryForObject("SELECT MAX(recommendation_id) FROM recommendation",Long.class);recommendations.add(rec);
            db.update("INSERT INTO recommendation_review_history(store_id,recommendation_id,old_status,new_status,reviewed_by,reason,resulting_version,eligibility_check) VALUES (?,?,'PENDING_REVIEW','REJECTED',?,'fixture',2,?)",store,rec,actor,eligibility);
        }
        var productBefore=productRows(db,true);
        var frozenBefore=frozen(db,snapshots,request,recommendations);
        assertEquals(List.of(),missing(url,user,password),"V25 has all three legacy columns");

        var flyway=Flyway.configure().dataSource(url,user,password).target("26").load();
        assertEquals(1,flyway.migrate().migrationsExecuted);flyway.validate();

        assertEquals(DROPPED,missing(url,user,password),"V26 drops exactly the three legacy columns");
        assertEquals(productBefore,productRows(db,false),"every other product column is unchanged");
        assertEquals(frozenBefore,frozen(db,snapshots,request,recommendations),"frozen JSON is byte-identical");
        assertEquals("2026-12-08",db.queryForObject("SELECT push_day FROM inventory_batch WHERE product_id=?",String.class,product));
        // Readers still accept the payloads that contain the legacy names.
        var json=new UtilitySettingsRepository(db,JsonMapper.builder().findAndAddModules().addModule(UtilityJsonVersions.module()).addModule(RecommendationJsonVersions.module())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build());
        assertInstanceOf(UtilityMatrixContracts.FrozenConfiguration.class,json.read(frozenBefore.get(0),FrozenSettings.class));
        assertInstanceOf(UtilityMatrixContracts.FrozenConfiguration.class,json.read(frozenBefore.get(1),FrozenSettings.class));
        assertInstanceOf(FrozenV2.class,json.read(frozenBefore.get(2),FrozenSettings.class));
        assertInstanceOf(UtilityMatrixContracts.FrozenConfiguration.class,json.read(frozenBefore.get(3),UtilityMatrixContracts.SnapshotView.class).configuration());
        var expiry=assertInstanceOf(ExpiryScoreCalculator.Evidence.class,json.read(frozenBefore.get(4),ExpiryEvidence.class));
        assertEquals(7,expiry.expiryPushDays());
        var v1=assertInstanceOf(RecommendationContracts.RecEligibility.class,json.read(frozenBefore.get(5),EligibilityEvidence.class));
        assertEquals(7,v1.products().get(0).expiryPushDays());
        assertInstanceOf(EligibilityV2.class,json.read(frozenBefore.get(6),EligibilityEvidence.class));
        assertInstanceOf(EligibilityV2.class,json.read(frozenBefore.get(8),EligibilityEvidence.class),"review history v2");
        for(String raw:List.of(LINEAR,BINARY,W2))assertEquals(raw,json.write(json.read(raw,FrozenSettings.class)));
        assertEquals(EXPIRY_V1,json.write(json.read(EXPIRY_V1,ExpiryEvidence.class)));
        assertEquals(ELIGIBILITY_V1,json.write(json.read(ELIGIBILITY_V1,EligibilityEvidence.class)));
        assertEquals(ELIGIBILITY_V2,json.write(json.read(ELIGIBILITY_V2,EligibilityEvidence.class)));
        // The dropped CHECK constraints went with the columns: a plain insert still works.
        db.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,'NEW-1','New','each',FALSE,1,1,'ACTIVE')",store);
        assertEquals(0,flyway.migrate().migrationsExecuted);
    }
    private void fresh(String url,String user,String password) {
        var flyway=Flyway.configure().dataSource(url,user,password).load();
        flyway.migrate();flyway.validate();
        // V26 is applied on a fresh database; later migrations (V27+) may follow it.
        assertTrue(java.util.Arrays.stream(flyway.info().applied()).anyMatch(m->m.getVersion()!=null&&"26".equals(m.getVersion().getVersion())&&m.getState().isApplied()),"V26 applied");
        assertEquals(DROPPED,missing(url,user,password));
    }
    /** Which of the three legacy columns are absent from product. */
    private static List<String> missing(String url,String user,String password) {
        try(var c=java.sql.DriverManager.getConnection(url,user,password)) {
            var present=new HashSet<String>();
            try(var r=c.getMetaData().getColumns(c.getCatalog(),c.getSchema(),"product",null)){while(r.next())present.add(r.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));}
            if(present.isEmpty())try(var r=c.getMetaData().getColumns(c.getCatalog(),c.getSchema(),"PRODUCT",null)){while(r.next())present.add(r.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));}
            assertTrue(present.contains("sku"),"product table found");
            return DROPPED.stream().filter(col->!present.contains(col)).toList();
        } catch(java.sql.SQLException e) { throw new IllegalStateException(e); }
    }
    private static List<Map<String,Object>> productRows(JdbcTemplate db,boolean before) {
        return db.queryForList("SELECT product_id,store_id,category_id,sku,name,unit,requires_expiry,selling_price,cost_price,status FROM product ORDER BY product_id");
    }
    private static List<String> frozen(JdbcTemplate db,List<Integer> snapshots,long request,List<Long> recs) {
        var out=new ArrayList<String>();
        for(int s:snapshots)out.add(db.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,s));
        out.add(db.queryForObject("SELECT utility_config_snapshot FROM mining_request WHERE request_id=?",String.class,request));
        out.add(db.queryForObject("SELECT expiry_evidence FROM utility_matrix_item WHERE snapshot_id=?",String.class,snapshots.get(1)));
        for(long r:recs)out.add(db.queryForObject("SELECT generation_eligibility FROM recommendation WHERE recommendation_id=?",String.class,r));
        for(long r:recs)out.add(db.queryForObject("SELECT eligibility_check FROM recommendation_review_history WHERE recommendation_id=?",String.class,r));
        for(long r:recs)out.add(db.queryForObject("SELECT evidence FROM recommendation WHERE recommendation_id=?",String.class,r));
        out.add(db.queryForObject("SELECT frozen_input FROM mining_request WHERE request_id=?",String.class,request));
        return out;
    }
}
