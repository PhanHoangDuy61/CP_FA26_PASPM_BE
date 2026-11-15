package vn.edu.fpt.paspm.utility;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.fpt.paspm.mining.*;
import vn.edu.fpt.paspm.recommendation.*;
import vn.edu.fpt.paspm.common.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.fpt.paspm.utility.UtilityContracts.*;
import static vn.edu.fpt.paspm.utility.UtilityMatrixContracts.*;
import static vn.edu.fpt.paspm.utility.UtilityVersionedContracts.*;
import static vn.edu.fpt.paspm.recommendation.RecommendationContracts.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:versioned_json;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=true",
    "paspm.jwt-secret=version-test-only-signing-key-at-least-thirty-two-bytes",
    "paspm.otp-secret=version-test-only-otp-key-at-least-thirty-two-bytes",
    "paspm.avatar.directory=${java.io.tmpdir}/paspm-version-test-avatars",
    "paspm.bootstrap-email=","paspm.bootstrap-password="})
class VersionedUtilityPersistenceTest {
    @Autowired JdbcTemplate db;
    @Autowired UtilitySettingsRepository json;
    @Autowired UtilitySnapshotRepository snapshots;
    @Autowired MiningRepository mining;
    @Autowired RecommendationRepository recommendations;

    @Test void legacySnapshotMiningAndRecommendationReadsNeverRewriteFrozenJson() {
        for(String version:List.of("F03_LINEAR_V1",ExpiryScoreCalculator.SCORING_VERSION)) {
            db.update("INSERT INTO store(name) VALUES ('Synthetic compatibility fixture')");
            int store=db.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
            db.update("INSERT INTO app_user(store_id,role_id,email,status) SELECT ?,role_id,?,'ACTIVE' FROM role WHERE code='MERCHANDISER'",store,UUID.randomUUID()+"@example.invalid");
            int actor=db.queryForObject("SELECT MAX(user_id) FROM app_user",Integer.class);
            var config=new WeightConfig(true,true,true,new BigDecimal("0.5"),new BigDecimal("0.2"),new BigDecimal("0.3"),new BigDecimal("0.4"),30,7,version);
            var frozen=new FrozenConfiguration(config,new LambdaPolicy(new BigDecimal("0.1"),new BigDecimal("0.5"),2),
                new EffectiveCoefficients(config.alpha(),config.beta(),config.gamma()),LocalDate.of(2026,10,19),"F04_DATABASE_V1",6,"HALF_UP","PRODUCT_ID_V1","Asia/Ho_Chi_Minh");
            db.update("INSERT INTO utility_snapshot(store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (?,?, '2026-10-01','2026-10-19','2026-10-19',?,?,?,1,6,'READY')",store,actor,json.write(frozen),"a".repeat(64),"b".repeat(64));
            int snapshot=db.queryForObject("SELECT MAX(snapshot_id) FROM utility_snapshot",Integer.class);
            var view=snapshots.find(store,snapshot);
            var input=new MiningInputBuilder.Dataset("", "c".repeat(64),Map.of(),1,1,1);
            db.update("INSERT INTO mining_request(store_id,requested_by,utility_snapshot_id,request_key,payload_hash,algorithms,min_util_mode,min_util_value,min_util_spmf,input_snapshot_hash,frozen_input,utility_config_snapshot,status,comparison_status) VALUES (?,?,?,?,?,'EFIM','ABSOLUTE',1,1,?,?,?,'COMPLETED','NOT_REQUESTED')",store,actor,snapshot,UUID.randomUUID().toString(),"d".repeat(64),input.hash(),json.write(input),json.write(view));
            long request=db.queryForObject("SELECT MAX(request_id) FROM mining_request",Long.class);
            var eligibility=new RecEligibility(EligibilityStatus.ELIGIBLE,List.of(),Instant.parse("2026-10-19T00:00:00Z"),frozen.analysisDate(),frozen.timeZone(),List.of(),"immutable-fingerprint");
            var policy=new RecPolicy(1,4,2,new BigDecimal("0.05"),new BigDecimal("0.5"),1,"F06_RULES_V1_1");
            var evidence=new TransactionEvidence.Evidence(1,1,BigDecimal.ONE,null,null,"AVAILABLE");
            db.update("INSERT INTO recommendation(store_id,utility_snapshot_id,identity_hash,canonical_key,type,utility,rule_version,policy_version,policy_snapshot,evidence,generation_eligibility,explanation,created_by) VALUES (?,?,?,'synthetic','BUNDLE',1,'F06_RULES_V1_1',1,?,?,?,'[]',?)",store,snapshot,"e".repeat(64),json.write(policy),json.write(evidence),json.write(eligibility),actor);
            long recommendation=db.queryForObject("SELECT MAX(recommendation_id) FROM recommendation",Long.class);
            var before=frozenBytes(snapshot,request,recommendation);
            assertEquals(frozen,snapshots.find(store,snapshot).configuration());
            assertEquals(frozen,mining.requests(store,0,10).items().get(0).snapshot().configuration());
            assertEquals(eligibility,recommendations.find(store,recommendation,false).eligibility());
            assertNull(snapshots.find(store+10000,snapshot));
            assertTrue(mining.requests(store+10000,0,10).items().isEmpty());
            assertThrows(ApiException.class,()->recommendations.find(store+10000,recommendation,false));
            assertEquals(before,frozenBytes(snapshot,request,recommendation));
        }
    }
    private List<String> frozenBytes(int snapshot,long request,long recommendation) {
        return List.of(db.queryForObject("SELECT config_snapshot FROM utility_snapshot WHERE snapshot_id=?",String.class,snapshot),
            db.queryForObject("SELECT utility_config_snapshot FROM mining_request WHERE request_id=?",String.class,request),
            db.queryForObject("SELECT frozen_input FROM mining_request WHERE request_id=?",String.class,request),
            db.queryForObject("SELECT generation_eligibility FROM recommendation WHERE recommendation_id=?",String.class,recommendation));
    }
    @Test void newLiveJsonIsReadAsWeightV2WithoutRewritingIt() {
        var config=new WeightV2(new BigDecimal("0.6"),new BigDecimal("0.4"),new BigDecimal("0.3"),30,8,V2);
        db.update("INSERT INTO store(name,utility_config) VALUES ('Synthetic future config',?)",json.write(config));
        int store=db.queryForObject("SELECT MAX(store_id) FROM store",Integer.class);
        String before=db.queryForObject("SELECT utility_config FROM store WHERE store_id=?",String.class,store);
        assertEquals(config,json.versionedConfig(store,false));
        assertEquals(config,json.config(store,false),"Phase 4: the live reader returns the W2 record");
        assertEquals(before,db.queryForObject("SELECT utility_config FROM store WHERE store_id=?",String.class,store));
    }
    @Test @org.springframework.transaction.annotation.Transactional
    void policyPayloadVersionIsReadWithoutTouchingTheStoredRow() {
        var payload=new PolicyPayloadV2(V2,new BigDecimal("0.25"));
        db.update("UPDATE system_configuration SET config_value=?,config_version=17 WHERE config_key='UTILITY_LAMBDA_POLICY'",json.write(payload));
        String before=db.queryForObject("SELECT config_value FROM system_configuration WHERE config_key='UTILITY_LAMBDA_POLICY'",String.class);
        var value=json.versionedPolicy(false);
        assertInstanceOf(LambdaV2.class,value);
        assertEquals(17,value.version());assertEquals(BigDecimal.ZERO,value.lambdaMin());
        assertEquals(0,new BigDecimal("0.25").compareTo(value.lambdaMax()));
        assertEquals(before,db.queryForObject("SELECT config_value FROM system_configuration WHERE config_key='UTILITY_LAMBDA_POLICY'",String.class));
    }
}
