package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import vn.edu.fpt.paspm.deployment.MySqlTestDatabase;
import vn.edu.fpt.paspm.recommendation.RecommendationContracts;
import vn.edu.fpt.paspm.recommendation.RecommendationJsonVersions;
import vn.edu.fpt.paspm.recommendation.RecommendationVersionedContracts.EligibilityEvidence;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.*;

/** V25: owner conversion rule of 2026-11-15 on live config only; frozen JSON stays byte-identical. */
class WeightV2MigrationTest {
    @Test void h2() { verify("jdbc:h2:mem:weight_v2_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""); }
    @Test @EnabledIfSystemProperty(named="paspm.test.mysql",matches="true")
    void mysql() { var db=MySqlTestDatabase.create();verify(db.url(),db.user(),db.password()); }

    private static final String LEGACY_FROZEN="{\"config\":{\"enableStock\":true,\"enableExpiry\":true,\"enableSeason\":true,\"alpha\":0.5,\"beta\":0.2,\"gamma\":0.3,\"lambda\":0.4,\"expiryWindowDays\":30,\"version\":7,\"scoringVersion\":\"EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1\"},\"lambdaPolicy\":{\"lambdaMin\":0,\"lambdaMax\":0.5,\"version\":1},\"effectiveCoefficients\":{\"alpha\":0.5,\"beta\":0.2,\"gamma\":0.3},\"analysisDate\":\"2026-10-19\",\"source\":\"F04_DATABASE_V1\",\"utilityScale\":6,\"roundingMode\":\"HALF_UP\",\"itemMapping\":\"PRODUCT_ID_V1\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}";
    private static final String LEGACY_ELIGIBILITY="{\"status\":\"ELIGIBLE\",\"products\":[{\"productId\":1,\"productStatus\":\"ACTIVE\",\"sellableQuantity\":5,\"status\":\"ELIGIBLE\",\"expiryPriorityScore\":1,\"expiryStatus\":\"AVAILABLE\",\"expiryPushDays\":7,\"warnings\":[]}],\"checkedAt\":\"2026-10-19T00:00:00Z\",\"asOfDate\":\"2026-10-19\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"warnings\":[],\"fingerprint\":\"frozen-fingerprint\"}";
    private static final String LEGACY_EXPIRY="{\"expiryPushDays\":7,\"earliestEligibleExpiryDate\":\"2026-10-22\",\"daysRemaining\":3,\"asOfDate\":\"2026-10-19\",\"reason\":\"WITHIN_THRESHOLD\",\"batches\":[]}";

    private void verify(String url,String user,String password) {
        Flyway.configure().dataSource(url,user,password).target("24").load().migrate();
        var db=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        // Raise the policy maximum first so a stored lambda above the later bound can exist.
        db.update("UPDATE system_configuration SET config_value='{\"lambdaMin\":0.1,\"lambdaMax\":0.5}' WHERE config_key='UTILITY_LAMBDA_POLICY'");
        long policyVersion=db.queryForObject("SELECT config_version FROM system_configuration WHERE config_key='UTILITY_LAMBDA_POLICY'",Long.class);
        int both=store(db,"{\"enableStock\":true,\"enableExpiry\":true,\"enableSeason\":true,\"alpha\":0.5,\"beta\":0.2,\"gamma\":0.3,\"lambda\":0.4,\"expiryWindowDays\":30,\"version\":7,\"scoringVersion\":\"EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1\"}");
        int stockOnly=store(db,"{\"enableStock\":true,\"enableExpiry\":false,\"enableSeason\":false,\"alpha\":1,\"beta\":0.6,\"gamma\":0.4,\"lambda\":0.2,\"expiryWindowDays\":null,\"version\":3,\"scoringVersion\":\"F03_LINEAR_V1\"}");
        int none=store(db,null);
        int clamped=store(db,"{\"enableStock\":false,\"enableExpiry\":true,\"enableSeason\":false,\"alpha\":0,\"beta\":1,\"gamma\":0,\"lambda\":0.9,\"expiryWindowDays\":null,\"version\":4,\"scoringVersion\":\"EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1\"}");
        int partial=store(db,"{\"expiryWindowDays\":30}");
        // Frozen artefacts at V24.
        db.update("INSERT INTO app_user(store_id,role_id,email,status) SELECT ?,role_id,?,'ACTIVE' FROM role WHERE code='MERCHANDISER'",both,UUID.randomUUID()+"@example.invalid");
        int actor=db.queryForObject("SELECT MAX(user_id) FROM app_user",Integer.class);
        db.update("INSERT INTO product(store_id,sku,name,unit,requires_expiry,selling_price,cost_price,status) VALUES (?,'MILK','Milk','each',TRUE,10,6,'ACTIVE')",both);
        int product=db.queryForObject("SELECT MAX(product_id) FROM product",Integer.class);
        db.update("INSERT INTO sales_transaction(store_id,source_system,source_order_id,occurred_at,status,f03_eligible,total_amount,content_sha256) VALUES (?,'TEST','V25-1',CURRENT_TIMESTAMP,'COMPLETED',TRUE,10,?)",both,"f".repeat(64));
        int tx=db.queryForObject("SELECT MAX(transaction_id) FROM sales_transaction",Integer.class);
        db.update("INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (?,?,'2026-10-01','2026-10-19','2026-10-19',?,?,?,4,6,'READY')",both,actor,LEGACY_FROZEN,"a".repeat(64),"b".repeat(64));
        int snapshot=db.queryForObject("SELECT MAX(snapshot_id) FROM utility_snapshot",Integer.class);
        db.update("INSERT INTO utility_matrix_item(snapshot_id,transaction_id,product_id,quantity,base_utility,stock_score,expiry_score,seasonal_score,expiry_status,weight_value,weighted_utility,expiry_evidence) VALUES (?,?,?,1,4,0,1,0,'AVAILABLE',1,4,?)",snapshot,tx,product,LEGACY_EXPIRY);
        String view="{\"snapshotId\":"+snapshot+",\"storeId\":"+both+",\"createdBy\":"+actor+",\"rangeStart\":\"2026-10-01\",\"rangeEnd\":\"2026-10-19\",\"asOfDate\":\"2026-10-19\",\"configuration\":"+LEGACY_FROZEN+",\"sourceHash\":\""+"a".repeat(64)+"\",\"matrixHash\":\""+"b".repeat(64)+"\",\"totalWeightedUtility\":4,\"roundingScale\":6,\"status\":\"READY\",\"createdAt\":\"2026-10-19T00:00:00Z\",\"warnings\":[]}";
        db.update("INSERT INTO mining_request(store_id,requested_by,utility_snapshot_id,request_key,payload_hash,algorithms,min_util_mode,min_util_value,min_util_spmf,input_snapshot_hash,frozen_input,utility_config_snapshot,status,comparison_status) VALUES (?,?,?,?,?,'EFIM','ABSOLUTE',1,1,?,?,?,'COMPLETED','NOT_REQUESTED')",both,actor,snapshot,UUID.randomUUID().toString(),"d".repeat(64),"c".repeat(64),"{\"frozen\":true}",view);
        long request=db.queryForObject("SELECT MAX(request_id) FROM mining_request",Long.class);
        db.update("INSERT INTO recommendation(store_id,utility_snapshot_id,identity_hash,canonical_key,type,utility,rule_version,policy_version,policy_snapshot,evidence,generation_eligibility,explanation,created_by) VALUES (?,?,?,'v25','BUNDLE',1,'F06_RULES_V1_1',1,'{}','{}',?,'[]',?)",both,snapshot,"e".repeat(64),LEGACY_ELIGIBILITY,actor);
        long recommendation=db.queryForObject("SELECT MAX(recommendation_id) FROM recommendation",Long.class);
        var frozenBefore=frozen(db,snapshot,request,recommendation);

        var flyway=Flyway.configure().dataSource(url,user,password).target("25").load();
        assertEquals(1,flyway.migrate().migrationsExecuted);flyway.validate();

        // Frozen JSON byte-identical and still readable by the version-aware readers.
        assertEquals(frozenBefore,frozen(db,snapshot,request,recommendation));
        var mapper=JsonMapper.builder().findAndAddModules().addModule(UtilityJsonVersions.module()).addModule(RecommendationJsonVersions.module())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        var json=new UtilitySettingsRepository(db,mapper);
        assertInstanceOf(UtilityMatrixContracts.FrozenConfiguration.class,json.read(frozenBefore.get(0),FrozenSettings.class));
        assertInstanceOf(UtilityMatrixContracts.FrozenConfiguration.class,json.read(frozenBefore.get(1),UtilityMatrixContracts.SnapshotView.class).configuration());
        assertInstanceOf(RecommendationContracts.RecEligibility.class,json.read(frozenBefore.get(3),EligibilityEvidence.class));
        assertInstanceOf(ExpiryScoreCalculator.Evidence.class,json.read(frozenBefore.get(4),ExpiryEvidence.class));
        assertEquals(LEGACY_FROZEN,json.write(json.read(LEGACY_FROZEN,FrozenSettings.class)));
        assertEquals(LEGACY_ELIGIBILITY,json.write(json.read(LEGACY_ELIGIBILITY,EligibilityEvidence.class)));

        // Policy: lower bound fixed at 0, maximum kept, version bumped.
        var policy=json.versionedPolicy(false);
        assertInstanceOf(LambdaV2.class,policy);assertEquals(0,new BigDecimal("0.5").compareTo(policy.lambdaMax()));
        assertEquals(BigDecimal.ZERO,policy.lambdaMin());assertEquals(policyVersion+1,policy.version());

        // Owner conversion rule.
        assertConfig(json,both,"0.4","0.6","0.4",8);      // expiry 0.2 + season 0.3 -> 0.4/0.6, lambda 0.4 kept
        assertConfig(json,stockOnly,"0.5","0.5","0.2",4); // only stock enabled -> 0.5/0.5
        assertConfig(json,none,"0.5","0.5","0",2);        // no config -> 0.5/0.5, lambda 0, defaults version 1 -> 2
        assertConfig(json,clamped,"1","0","0.5",5);       // lambda 0.9 > lambdaMax 0.5 -> clamped
        assertConfig(json,partial,"0.5","0.5","0",1);
        assertFalse(evidence(db,both,"lambda_clamped",Boolean.class));
        assertTrue(evidence(db,clamped,"lambda_clamped",Boolean.class));
        assertEquals(0,new BigDecimal("0.9").compareTo(evidence(db,clamped,"old_lambda",BigDecimal.class)));
        assertTrue(evidence(db,stockOnly,"default_coefficients",Boolean.class));
        assertFalse(evidence(db,none,"had_config",Boolean.class));
        assertNull(evidence(db,none,"old_value",String.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM utility_weight_v2_conversion WHERE subject='POLICY'",Integer.class));
        assertEquals(0,new BigDecimal("0.1").compareTo(db.queryForObject("SELECT old_lambda FROM utility_weight_v2_conversion WHERE subject='POLICY'",BigDecimal.class)));
        assertEquals(db.queryForObject("SELECT COUNT(*) FROM store",Integer.class),db.queryForObject("SELECT COUNT(*) FROM utility_weight_v2_conversion WHERE subject='STORE'",Integer.class));

        // Schema rules.
        db.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status,push_day) VALUES (?,'B1',CURRENT_TIMESTAMP,'2026-12-10',5,5,'ACTIVE','2026-12-08')",product);
        assertEquals(java.time.LocalDate.of(2026,12,8),db.queryForObject("SELECT push_day FROM inventory_batch WHERE batch_code='B1' AND product_id=?",java.time.LocalDate.class,product));
        assertThrows(Exception.class,()->db.update("INSERT INTO inventory_batch(product_id,batch_code,received_at,expiry_date,initial_quantity,remaining_quantity,status,push_day) VALUES (?,'B2',CURRENT_TIMESTAMP,'2026-12-10',5,5,'ACTIVE','2026-12-11')",product));
        db.update("INSERT INTO season(store_id,name,start_date,end_date,weight,created_by,updated_by) VALUES (?,'Tet','2027-01-20','2027-02-20',0.9,?,?)",both,actor,actor);
        assertThrows(Exception.class,()->db.update("INSERT INTO season(store_id,name,start_date,end_date,weight,created_by,updated_by) VALUES (?,'Tet','2027-03-01','2027-03-02',0.1,?,?)",both,actor,actor));
        assertThrows(Exception.class,()->db.update("INSERT INTO season(store_id,name,start_date,end_date,weight,created_by,updated_by) VALUES (?,'Bad dates','2027-03-02','2027-03-01',0.1,?,?)",both,actor,actor));
        assertThrows(Exception.class,()->db.update("INSERT INTO season(store_id,name,start_date,end_date,weight,created_by,updated_by) VALUES (?,'Too heavy','2027-03-01','2027-03-02',1.5,?,?)",both,actor,actor));
        int season=db.queryForObject("SELECT MAX(season_id) FROM season",Integer.class);
        db.update("INSERT INTO product_season(store_id,season_id,product_id,assigned_by) VALUES (?,?,?,?)",both,season,product,actor);
        assertThrows(Exception.class,()->db.update("INSERT INTO product_season(store_id,season_id,product_id,assigned_by) VALUES (?,?,?,?)",none,season,product,actor),"composite FK blocks a cross-Store assignment");
        assertThrows(Exception.class,()->db.update("UPDATE utility_matrix_item SET expiry_status='UNKNOWN' WHERE snapshot_id=?",snapshot));
        assertEquals(0,flyway.migrate().migrationsExecuted);
    }
    private static int store(JdbcTemplate db,String config) {
        db.update("INSERT INTO store(name,utility_config) VALUES (?,?)","V25 fixture "+UUID.randomUUID(),config);
        return db.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
    }
    private static List<String> frozen(JdbcTemplate db,int snapshot,long request,long recommendation) {
        return List.of(db.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,snapshot),
            db.queryForObject("SELECT utility_config_snapshot FROM mining_request WHERE request_id=?",String.class,request),
            db.queryForObject("SELECT frozen_input FROM mining_request WHERE request_id=?",String.class,request),
            db.queryForObject("SELECT generation_eligibility FROM recommendation WHERE recommendation_id=?",String.class,recommendation),
            db.queryForObject("SELECT expiry_evidence FROM utility_matrix_item WHERE snapshot_id=?",String.class,snapshot));
    }
    private static void assertConfig(UtilitySettingsRepository json,int store,String alpha,String beta,String lambda,long version) {
        var value=assertInstanceOf(WeightV2.class,json.versionedConfig(store,false));
        assertEquals(0,new BigDecimal(alpha).compareTo(value.alpha()),"alpha of Store "+store);
        assertEquals(0,new BigDecimal(beta).compareTo(value.beta()),"beta of Store "+store);
        assertEquals(0,value.alpha().add(value.beta()).compareTo(BigDecimal.ONE));
        assertEquals(0,new BigDecimal(lambda).compareTo(value.lambda()),"lambda of Store "+store);
        assertEquals(30,value.salesWindowDays());assertEquals(version,value.version());assertEquals(UtilityVersionedContracts.V2,value.scoringVersion());
    }
    private static <T> T evidence(JdbcTemplate db,int store,String column,Class<T> type) {
        return db.queryForObject("SELECT "+column+" FROM utility_weight_v2_conversion WHERE subject='STORE' AND store_id=?",type,store);
    }
}
