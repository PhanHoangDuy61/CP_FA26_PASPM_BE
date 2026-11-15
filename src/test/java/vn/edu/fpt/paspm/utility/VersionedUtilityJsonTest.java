package vn.edu.fpt.paspm.utility;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.*;
import vn.edu.fpt.paspm.recommendation.RecommendationJsonVersions;
import vn.edu.fpt.paspm.recommendation.RecommendationVersionedContracts.*;

class VersionedUtilityJsonTest {
    private final ObjectMapper json=new ObjectMapper().registerModule(new JavaTimeModule())
        .registerModule(UtilityJsonVersions.module()).registerModule(RecommendationJsonVersions.module())
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private String legacyConfig(String version) {
        return "{\"enableStock\":true,\"enableExpiry\":true,\"enableSeason\":true,\"alpha\":0.5,\"beta\":0.2,\"gamma\":0.3,\"lambda\":0.4,\"expiryWindowDays\":30,\"version\":7,\"scoringVersion\":\""+version+"\"}";
    }
    private String frozen(String config) {
        return "{\"config\":"+config+",\"lambdaPolicy\":{\"lambdaMin\":0.1,\"lambdaMax\":0.5,\"version\":2},\"effectiveCoefficients\":{\"alpha\":0.5,\"beta\":0.2,\"gamma\":0.3},\"analysisDate\":\"2026-10-19\",\"source\":\"F04_DATABASE_V1\",\"utilityScale\":6,\"roundingMode\":\"HALF_UP\",\"itemMapping\":\"PRODUCT_ID_V1\",\"timeZone\":\"Asia/Ho_Chi_Minh\"}";
    }
    @Test void bothLegacyScoringVersionsKeepTheirExactJsonShape() throws Exception {
        for(String version:new String[]{"F03_LINEAR_V1","EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1"}) {
            String raw=frozen(legacyConfig(version));
            var value=json.readValue(raw,FrozenSettings.class);
            assertInstanceOf(UtilityMatrixContracts.FrozenConfiguration.class,value);
            assertEquals(raw,json.writeValueAsString(value));
            assertEquals(version,value.config().scoringVersion());
            assertEquals(0,new java.math.BigDecimal("0.1").compareTo(value.lambdaPolicy().lambdaMin()));
        }
    }
    @Test void newWeightAndFrozenConfigurationUseNewRecordsWithoutLegacyFields() throws Exception {
        String config="{\"alpha\":0.6,\"beta\":0.4,\"lambda\":0.3,\"salesWindowDays\":30,\"version\":8,\"scoringVersion\":\"W2_EXPIRY_SEASON_V1\"}";
        assertInstanceOf(WeightV2.class,json.readValue(config,WeightSettings.class));
        String raw="{\"config\":"+config+",\"lambdaPolicy\":{\"scoringVersion\":\"W2_EXPIRY_SEASON_V1\",\"lambdaMax\":0.5,\"version\":2},\"effectiveCoefficients\":{\"alpha\":0.6,\"beta\":0.4},\"analysisDate\":\"2026-11-15\",\"source\":\"F04_DATABASE_V1\",\"utilityScale\":6,\"roundingMode\":\"HALF_UP\",\"weightScale\":2,\"weightRoundingMode\":\"HALF_UP\",\"itemMapping\":\"PRODUCT_ID_V1\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"seasons\":[{\"seasonId\":3,\"name\":\"Tet\",\"startDate\":\"2026-11-01\",\"endDate\":\"2026-11-30\",\"weight\":0.9,\"version\":1,\"productIds\":[1,2]}]}";
        var value=json.readValue(raw,FrozenSettings.class);
        assertInstanceOf(FrozenV2.class,value);
        assertEquals(raw,json.writeValueAsString(value));
        assertEquals(java.math.BigDecimal.ZERO,value.lambdaPolicy().lambdaMin());
    }
    @Test void unknownVersionAndUnknownFieldsRemainRejected() {
        assertThrows(Exception.class,()->json.readValue(legacyConfig("UNRECOGNIZED"),WeightSettings.class));
        assertThrows(Exception.class,()->json.readValue(legacyConfig("F03_LINEAR_V1").replace("\"version\":7","\"injected\":true,\"version\":7"),WeightSettings.class));
        assertTrue(json.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }
    @Test void everyVersionedReaderRejectsAnUnknownVersionAndAnUnknownField() {
        String bad="\"scoringVersion\":\"W9_UNKNOWN\"";
        String frozen=frozen(legacyConfig("F03_LINEAR_V1"));
        assertThrows(Exception.class,()->json.readValue(frozen.replace("\"scoringVersion\":\"F03_LINEAR_V1\"",bad),FrozenSettings.class));
        assertThrows(Exception.class,()->json.readValue(frozen.replace("\"source\":","\"injected\":1,\"source\":"),FrozenSettings.class));
        assertThrows(Exception.class,()->json.readValue("{"+bad+",\"lambdaMax\":0.5}",PolicyPayload.class));
        assertThrows(Exception.class,()->json.readValue("{\"lambdaMin\":0,\"lambdaMax\":0.5,\"injected\":1}",PolicyPayload.class));
        assertThrows(Exception.class,()->json.readValue("{"+bad+",\"lambdaMax\":0.5,\"version\":1}",PolicyValues.class));
        assertThrows(Exception.class,()->json.readValue("{"+bad+",\"asOfDate\":\"2026-11-15\",\"reason\":\"X\"}",ExpiryEvidence.class));
        assertThrows(Exception.class,()->json.readValue("{\"expiryPushDays\":7,\"earliestEligibleExpiryDate\":null,\"daysRemaining\":null,\"asOfDate\":\"2026-10-19\",\"reason\":\"NO_ELIGIBLE_BATCH\",\"batches\":[],\"injected\":1}",ExpiryEvidence.class));
        assertThrows(Exception.class,()->json.readValue("{"+bad+",\"status\":\"ELIGIBLE\",\"products\":[],\"warnings\":[],\"fingerprint\":\"x\"}",EligibilityEvidence.class));
        assertThrows(Exception.class,()->json.readValue("{\"status\":\"ELIGIBLE\",\"products\":[],\"checkedAt\":\"2026-10-19T00:00:00Z\",\"asOfDate\":\"2026-10-19\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"warnings\":[],\"fingerprint\":\"f\",\"injected\":1}",EligibilityEvidence.class));
        assertThrows(Exception.class,()->json.readValue("{\"scoringVersion\":7,\"lambdaMax\":0.5}",PolicyPayload.class),"non-text version");
    }
    @Test void oldExpiryAndRecommendationEvidenceKeepTheirShape() throws Exception {
        String expiry="{\"expiryPushDays\":7,\"earliestEligibleExpiryDate\":null,\"daysRemaining\":null,\"asOfDate\":\"2026-10-19\",\"reason\":\"NO_ELIGIBLE_BATCH\",\"batches\":[]}";
        var e=json.readValue(expiry,ExpiryEvidence.class);
        assertInstanceOf(ExpiryScoreCalculator.Evidence.class,e);
        assertEquals(expiry,json.writeValueAsString(e));
        String eligibility="{\"status\":\"ELIGIBLE\",\"products\":[],\"checkedAt\":\"2026-10-19T00:00:00Z\",\"asOfDate\":\"2026-10-19\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"warnings\":[],\"fingerprint\":\"frozen\"}";
        var r=json.readValue(eligibility,EligibilityEvidence.class);
        assertEquals(eligibility,json.writeValueAsString(r));
        assertEquals("frozen",r.fingerprint());
    }
    @Test void newEvidenceAndPolicySelectTheirOwnRecordsAndRejectLegacyFields() throws Exception {
        String expiry="{\"scoringVersion\":\"W2_EXPIRY_SEASON_V1\",\"asOfDate\":\"2026-11-15\",\"reason\":\"NO_ELIGIBLE_BATCH\",\"salesWindowDays\":30,\"unitsSold\":0,\"dailySalesRate\":0,\"riskQuantity\":0,\"eligibleQuantity\":0,\"score\":0,\"batches\":[],\"warnings\":[]}";
        var evidence=json.readValue(expiry,ExpiryEvidence.class);
        assertInstanceOf(FefoEvidence.class,evidence);
        assertEquals(expiry,json.writeValueAsString(evidence));
        String policy="{\"scoringVersion\":\"W2_EXPIRY_SEASON_V1\",\"lambdaMax\":0.5}";
        assertInstanceOf(PolicyPayloadV2.class,json.readValue(policy,PolicyPayload.class));
        assertThrows(Exception.class,()->json.readValue(policy.replace("\"lambdaMax\"","\"lambdaMin\":0,\"lambdaMax\""),PolicyPayload.class));
        assertThrows(Exception.class,()->json.readValue(expiry.replace("NO_ELIGIBLE_BATCH","NO_ELIGIBLE_BATCH").replace("\"score\":0","\"expiryPushDays\":7,\"score\":0"),ExpiryEvidence.class));
    }
    @Test void newRecommendationEvidenceDoesNotAcceptRemovedProductThreshold() throws Exception {
        String raw="{\"scoringVersion\":\"W2_EXPIRY_SEASON_V1\",\"status\":\"ELIGIBLE\",\"products\":[{\"productId\":1,\"productStatus\":\"ACTIVE\",\"sellableQuantity\":5,\"status\":\"ELIGIBLE\",\"expiryPriorityScore\":0.2,\"expiryStatus\":\"AVAILABLE\",\"warnings\":[]}],\"checkedAt\":\"2026-11-15T00:00:00Z\",\"asOfDate\":\"2026-11-15\",\"timeZone\":\"Asia/Ho_Chi_Minh\",\"warnings\":[],\"fingerprint\":\"v2-fingerprint\"}";
        var value=json.readValue(raw,EligibilityEvidence.class);
        assertInstanceOf(EligibilityV2.class,value);
        assertEquals(raw,json.writeValueAsString(value));
        assertThrows(Exception.class,()->json.readValue(raw.replace("\"productId\":1","\"productId\":1,\"expiryPushDays\":7"),EligibilityEvidence.class));
    }
}
