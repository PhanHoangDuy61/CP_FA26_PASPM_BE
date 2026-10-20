package vn.edu.fpt.paspm.recommendation;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.paspm.mining.*;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.SnapshotView;
import static vn.edu.fpt.paspm.recommendation.RecommendationContracts.*;

class RecommendationRulesTest {
    RecommendationRules rules=new RecommendationRules();
    FrozenEvidenceReader.Source source(TransactionEvidence evidence) {
        var run=new MiningContracts.RunView(9,4,7,3,MiningEngine.Algorithm.HUI_MINER,1,"test","COMPLETED",1,"a".repeat(64),MiningInputBuilder.VERSION,null,null,null,null,"test","test",null,1,null,null);
        var snapshot=new SnapshotView(3,7,1,LocalDate.now(),LocalDate.now(),LocalDate.now(),null,"a".repeat(64),"b".repeat(64),BigDecimal.TEN,6,"READY",Instant.EPOCH,List.of());
        return new FrozenEvidenceReader.Source(run,snapshot,"ABSOLUTE",BigDecimal.ONE,BigDecimal.ONE,"a".repeat(64),List.of(),evidence);
    }
    RecPolicy policy(int max,int count,String ratio,String confidence){return new RecPolicy(1,max,count,new BigDecimal(ratio),new BigDecimal(confidence),1,RecommendationPolicyService.RULE_VERSION);}
    FrozenEvidenceReader.SourcePattern pattern(List<Integer> ids){return new FrozenEvidenceReader.SourcePattern(1,ids,BigDecimal.TEN,10,ids.size());}
    TransactionEvidence fixture(){return new TransactionEvidence(List.of(Set.of(1,2),Set.of(1,2),Set.of(1),Set.of(2),Set.of(2),Set.of(3)));}
    @Test void confidenceQualificationDoesNotRoundTwoThirdsUpToThreshold() {
        var result=rules.apply(source(fixture()),pattern(List.of(1,2)),List.of(RecType.CROSS_SELL),policy(4,2,"0.05","0.666667"));
        assertTrue(result.candidates().isEmpty());assertEquals(2,result.skipped().size());
        var accepted=rules.apply(source(fixture()),pattern(List.of(1,2)),List.of(RecType.CROSS_SELL),policy(4,2,"0.05","0.666666"));
        assertEquals(1,accepted.candidates().size());assertEquals(1,accepted.candidates().get(0).anchor());
    }
    @Test void fbtUsesBothExactCountAndRatioAndInclusiveBoundary() {
        assertEquals(1,rules.apply(source(fixture()),pattern(List.of(1,2)),List.of(RecType.FBT),policy(4,2,"0.333333","0.5")).candidates().size());
        assertTrue(rules.apply(source(fixture()),pattern(List.of(1,2)),List.of(RecType.FBT),policy(4,2,"0.333334","0.5")).candidates().isEmpty());
        assertTrue(rules.apply(source(fixture()),pattern(List.of(1,2)),List.of(RecType.FBT),policy(4,3,"0","0.5")).candidates().isEmpty());
    }
    @Test void bundleUsesFullPatternAndApprovedMaximumWhileBannerAllowsSingleton() {
        var evidence=new TransactionEvidence(List.of(Set.of(1,2,3,4,5)));
        assertEquals(1,rules.apply(source(evidence),pattern(List.of(1,2,3,4)),List.of(RecType.BUNDLE),policy(4,2,"0.05","0.5")).candidates().size());
        var tooLarge=rules.apply(source(evidence),pattern(List.of(1,2,3,4,5)),List.of(RecType.BUNDLE),policy(4,2,"0.05","0.5"));
        assertTrue(tooLarge.candidates().isEmpty());assertEquals("BUNDLE_EXCEEDS_MAX_SIZE",tooLarge.skipped().get(0).reason());
        var single=rules.apply(source(evidence),pattern(List.of(1)),List.of(RecType.BANNER,RecType.BUNDLE,RecType.FBT,RecType.CROSS_SELL),policy(4,2,"0.05","0.5"));
        assertEquals(RecType.BANNER,single.candidates().get(0).type());assertEquals(1,single.candidates().size());assertEquals(3,single.skipped().size());
    }
    @Test void missingEvidenceNeverClaimsCrossSellOrFrequentlyBoughtTogether() {
        var result=rules.apply(source(new TransactionEvidence(List.of())),pattern(List.of(1,2)),List.of(RecType.CROSS_SELL,RecType.FBT),policy(4,2,"0.05","0.5"));
        assertTrue(result.candidates().isEmpty());assertTrue(result.skipped().stream().allMatch(s->s.reason().equals("TRANSACTION_EVIDENCE_UNAVAILABLE")));
    }
}
