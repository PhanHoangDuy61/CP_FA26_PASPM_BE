package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.ExpiryRiskCalculator.Batch;
import vn.edu.fpt.paspm.utility.UtilityVersionedContracts.SeasonCopy;

class WeightV2CalculationTest {
    private static final LocalDate AS_OF=LocalDate.of(2026,11,15);
    private final ExpiryRiskCalculator calculator=new ExpiryRiskCalculator();
    private static Batch batch(int id,long qty,int daysToPush) { return new Batch(id,AS_OF.plusDays(daysToPush),null,qty,"ACTIVE"); }
    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test void goldenSingleBatchIs0375() {
        var r=calculator.calculate(true,List.of(batch(1,120,3)),AS_OF,30,750); // 25 per day
        assertEquals(0,d("0.375").compareTo(r.score()));assertEquals("AVAILABLE",r.status());
        assertEquals(0,d("45").compareTo(r.evidence().riskQuantity()));assertEquals(120,r.evidence().eligibleQuantity());
        assertEquals(0,d("25").compareTo(r.evidence().dailySalesRate()));
    }
    @Test void goldenTwoBatchesIs025InFefoOrderWhateverTheInputOrder() {
        var r=calculator.calculate(true,List.of(batch(2,60,5),batch(1,40,2)),AS_OF,30,450); // 15 per day
        assertEquals(0,d("0.25").compareTo(r.score()));
        assertEquals(List.of(1,2),r.evidence().batches().stream().map(UtilityVersionedContracts.RiskBatch::batchId).toList());
        assertEquals(0,d("10").compareTo(r.evidence().batches().get(0).riskQuantity()));
        assertEquals(0,d("15").compareTo(r.evidence().batches().get(1).riskQuantity()));
    }
    @Test void pushDayTiesAreOrderedByIntegerBatchId() {
        var r=calculator.calculate(true,List.of(batch(10,40,2),batch(9,25,2)),AS_OF,30,600); // 20 per day -> 40 sellable
        assertEquals(List.of(9,10),r.evidence().batches().stream().map(UtilityVersionedContracts.RiskBatch::batchId).toList());
        assertEquals(0,d("0").compareTo(r.evidence().batches().get(0).riskQuantity()));
        assertEquals(0,d("25").compareTo(r.evidence().batches().get(1).riskQuantity()));
    }
    @Test void noEligibleQuantityGivesZero() {
        var r=calculator.calculate(true,List.of(),AS_OF,30,10);
        assertEquals(0,r.score().signum());assertEquals("NO_SELLABLE_BATCH",r.status());assertEquals(BigInteger.ONE,r.eligibleScaled());
    }
    @Test void missingPushDayOnlyWarnsAndIsExcludedFromRiskAndEligible() {
        var r=calculator.calculate(true,List.of(batch(1,120,3),new Batch(2,null,null,500,"ACTIVE")),AS_OF,30,750);
        assertEquals(0,d("0.375").compareTo(r.score()));assertEquals("MISSING_PUSH_DAY",r.status());
        assertEquals(List.of(2),r.batchesMissingPushDay());assertTrue(r.evidence().warnings().contains("MISSING_PUSH_DAY"));
        assertEquals(120,r.evidence().eligibleQuantity());
        var only=calculator.calculate(true,List.of(new Batch(2,null,null,500,"ACTIVE")),AS_OF,30,750);
        assertEquals("MISSING_PUSH_DAY",only.status());assertEquals(0,only.score().signum());
    }
    @Test void noRecentSalesGivesZeroAndSlowMovingWarning() {
        var r=calculator.calculate(true,List.of(batch(1,120,3)),AS_OF,30,0);
        assertEquals(0,r.score().signum());assertEquals("SLOW_MOVING",r.status());
        assertEquals(List.of("SLOW_MOVING_NO_RECENT_SALES"),r.evidence().warnings());
    }
    @Test void pushDayOnOrBeforeAnalysisDatePutsTheWholeBatchAtRisk() {
        var r=calculator.calculate(true,List.of(batch(1,30,0),batch(2,20,-2)),AS_OF,30,3000);
        assertEquals(0,BigDecimal.ONE.compareTo(r.score()));
    }
    @Test void expiredBlockedEmptyAndNonExpiryProductsAreNotEligible() {
        var expired=new Batch(1,AS_OF.minusDays(3),AS_OF.minusDays(1),10,"ACTIVE"); // DEC-20261124-01: expired only when the date is AFTER expiry
        var r=calculator.calculate(true,List.of(expired,new Batch(2,AS_OF.plusDays(1),null,10,"BLOCKED"),new Batch(3,AS_OF.plusDays(1),null,0,"ACTIVE")),AS_OF,30,30);
        assertEquals("NO_SELLABLE_BATCH",r.status());assertEquals(List.of("EXPIRED"),r.evidence().batches().get(0).warnings());
        var na=calculator.calculate(false,List.of(batch(1,10,1)),AS_OF,30,30);
        assertEquals("NOT_APPLICABLE",na.status());assertEquals(0,na.score().signum());
    }
    @Test void weightIsRoundedHalfUpToTwoDecimalsAndBounded() {
        assertEquals(d("1.03"),UtilityMath.weightV2(d("0.25"),d("1"),d("0"),BigInteger.ONE,BigInteger.TEN,d("0")));
        assertEquals(d("1.00"),UtilityMath.weightV2(d("0"),d("0.5"),d("0.5"),BigInteger.ONE,BigInteger.ONE,d("1")));
        assertEquals(d("1.50"),UtilityMath.weightV2(d("0.5"),d("0.5"),d("0.5"),BigInteger.ONE,BigInteger.ONE,d("1")));
        assertEquals(d("1.12"),UtilityMath.weightV2(d("0.23"),d("0.5"),d("0.5"),BigInteger.ZERO,BigInteger.ONE,d("1")),"Report 4 example 1.115 -> 1.12");
        for(var lambda:List.of("0","0.1","0.125","0.333333","0.5")) {
            var w=UtilityMath.weightV2(d(lambda),d("0.5"),d("0.5"),BigInteger.ONE,BigInteger.ONE,d("1"));
            assertTrue(w.compareTo(BigDecimal.ONE)>=0);
            assertTrue(w.compareTo(BigDecimal.ONE.add(d(lambda)).setScale(2,java.math.RoundingMode.HALF_UP))<=0);
        }
    }
    @Test void weightRejectsInvalidInputs() {
        assertThrows(ApiException.class,()->UtilityMath.weightV2(d("0.5"),d("0.6"),d("0.6"),BigInteger.ONE,BigInteger.ONE,d("0")));
        assertThrows(ApiException.class,()->UtilityMath.weightV2(d("-0.1"),d("0.5"),d("0.5"),BigInteger.ONE,BigInteger.ONE,d("0")));
        assertThrows(ApiException.class,()->UtilityMath.weightV2(d("0.5"),d("0.5"),d("0.5"),BigInteger.ONE,BigInteger.ONE,d("1.1")));
        assertThrows(ApiException.class,()->UtilityMath.weightV2(d("0.5"),d("0.5"),d("0.5"),BigInteger.TWO,BigInteger.ONE,d("0")));
    }
    @Test void productInSeveralActiveSeasonsGetsTheHighestWeightP2() {
        var seasons=List.of(new SeasonCopy(5,"A",AS_OF,AS_OF,d("0.4"),1,List.of(1,2)),new SeasonCopy(3,"B",AS_OF,AS_OF,d("0.9"),1,List.of(2)),
            new SeasonCopy(4,"C",AS_OF,AS_OF,d("0.9"),1,List.of(2,3)));
        var scores=WeightV2Calculator.seasonScores(seasons);
        assertEquals(0,d("0.4").compareTo(scores.get(1).weight()));assertEquals(5,scores.get(1).seasonId());
        assertEquals(0,d("0.9").compareTo(scores.get(2).weight()));assertEquals(3,scores.get(2).seasonId(),"tie goes to the lowest seasonId");
        assertNull(scores.get(99));
    }
    /** Backend W(i) must equal paspm-experiments src/weighting.py on the same fixture (scripts/f03/generate_w2_crosscheck.py). */
    @Test void backendMatchesTheExperimentReferenceImplementation() throws Exception {
        var root=new ObjectMapper().readTree(getClass().getResourceAsStream("/f03/w2_crosscheck.json"));
        int cases=0;
        for(var c:root.path("cases")) {
            var batches=new ArrayList<Batch>();
            for(var b:c.path("batches"))batches.add(batch(b.path("batchId").asInt(),b.path("quantity").asLong(),b.path("daysToPush").asInt()));
            BigInteger num,den;
            if(batches.isEmpty()) { num=BigInteger.valueOf(c.path("uExpiryNumerator").asLong());den=BigInteger.valueOf(c.path("uExpiryDenominator").asLong()); }
            else {
                var r=calculator.calculate(true,batches,AS_OF,c.path("salesWindowDays").asInt(),c.path("unitsSold").asLong());
                num=r.riskScaled();den=r.eligibleScaled();
                assertEquals(num.multiply(BigInteger.valueOf(c.path("uExpiryDenominator").asLong())),
                    den.multiply(BigInteger.valueOf(c.path("uExpiryNumerator").asLong())),"U_expiry of "+c.path("name").asText());
            }
            var w=UtilityMath.weightV2(d(c.path("lambda").asText()),d(c.path("alpha").asText()),d(c.path("beta").asText()),num,den,d(c.path("seasonWeight").asText()));
            assertEquals(d(c.path("weight").asText()),w,"W of "+c.path("name").asText());
            cases++;
        }
        assertEquals(9,cases);
    }
}
