package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.paspm.utility.UtilityContracts.WeightConfig;

class ExpiryBinaryRulesTest {
    final LocalDate day=LocalDate.of(2026,10,19);
    final ExpiryScoreCalculator calculator=new ExpiryScoreCalculator();
    List<ExpiryScoreCalculator.Batch> example() {
        return List.of(new ExpiryScoreCalculator.Batch(day.plusDays(5),20,"ACTIVE"),
            new ExpiryScoreCalculator.Batch(day.plusDays(40),80,"ACTIVE"),
            new ExpiryScoreCalculator.Batch(day.minusDays(1),10,"ACTIVE"));
    }
    @Test void officialSevenDayThresholdProduces17220ForTheWholeProduct() {
        var score=calculator.calculate(true,example(),day,7).score();
        assertEquals(0,BigDecimal.ONE.compareTo(score));
        var weight=UtilityMath.weight(config(),new BigDecimal("0.8"),score,new BigDecimal("0.9"));
        assertEquals(0,new BigDecimal("1.435").compareTo(weight));
        assertEquals(new BigDecimal("17220.000000"),UtilityMath.weighted(UtilityMath.base(3,new BigDecimal("10000"),new BigDecimal("6000"),null),weight));
    }
    @Test void threeDayAndDisabledThresholdProduce16020() {
        for(int threshold:new int[]{3,0}) {
            var score=calculator.calculate(true,example(),day,threshold).score();
            assertEquals(0,score.signum());
            var weight=UtilityMath.weight(config(),new BigDecimal("0.8"),score,new BigDecimal("0.9"));
            assertEquals(0,new BigDecimal("1.335").compareTo(weight));
            assertEquals(new BigDecimal("16020.000000"),UtilityMath.weighted(new BigDecimal("12000"),weight));
        }
    }
    @Test void thresholdBoundaryIsInclusiveAndOneDayAfterIsOutside() {
        for(int days:new int[]{1,7,8}) {
            var result=calculator.calculate(true,List.of(new ExpiryScoreCalculator.Batch(day.plusDays(days),1,"ACTIVE")),day,7);
            assertEquals(days<=7?1:0,result.score().intValueExact());
        }
    }
    @Test void negativeThresholdIsInvalid() {
        assertThrows(IllegalArgumentException.class,()->calculator.calculate(true,example(),day,-1));
    }
    @Test void nullThresholdIsDisabledWithoutInventingADefault() {
        var result=calculator.calculate(true,example(),day,null);
        assertEquals(BigDecimal.ZERO,result.score());
        assertEquals(0,result.evidence().expiryPushDays());
        assertEquals("THRESHOLD_DISABLED",result.evidence().reason());
        assertTrue(result.evidence().batches().stream().noneMatch(ExpiryScoreCalculator.PushBatch::pushRequired));
    }
    @Test void fefoEvidenceIncludesEveryPushBatchAndIgnoresQuantityRatios() {
        var result=calculator.calculate(123,true,List.of(
            new ExpiryScoreCalculator.Batch(9,day.plusDays(40),9999,"ACTIVE"),
            new ExpiryScoreCalculator.Batch(8,day.plusDays(7),20,"ACTIVE"),
            new ExpiryScoreCalculator.Batch(7,day.plusDays(1),1,"ACTIVE"),
            new ExpiryScoreCalculator.Batch(6,day,10,"ACTIVE"),
            new ExpiryScoreCalculator.Batch(5,day.plusDays(1),10,"BLOCKED"),
            new ExpiryScoreCalculator.Batch(4,day.plusDays(1),0,"ACTIVE"),
            new ExpiryScoreCalculator.Batch(3,day.plusDays(1),10,"INACTIVE")),day,7);
        assertEquals(BigDecimal.ONE,result.score());
        assertEquals(day.plusDays(1),result.evidence().earliestEligibleExpiryDate());
        assertEquals(List.of(7,8,9),result.evidence().batches().stream().map(ExpiryScoreCalculator.PushBatch::batchId).toList());
        assertEquals(List.of(1L,20L,0L),result.evidence().batches().stream().map(ExpiryScoreCalculator.PushBatch::pushRequiredQuantity).toList());
        assertTrue(result.evidence().batches().stream().allMatch(b->b.productId()==123&&b.asOfDate().equals(day)));
    }
    @Test void noSellableMissingAndNonExpiringStatesRemainExplicitEvenWithZeroThreshold() {
        assertEquals("NO_SELLABLE_BATCH",calculator.calculate(true,List.of(),day,0).status());
        var missing=List.of(new ExpiryScoreCalculator.Batch(null,5,"ACTIVE"));
        assertEquals("MISSING_METADATA",calculator.calculate(true,missing,day,0).status());
        assertEquals(BigDecimal.ZERO,calculator.calculate(true,missing,day,0).score());
        assertNull(calculator.calculate(true,missing,day,7).score());
        assertEquals("NOT_APPLICABLE",calculator.calculate(false,example(),day,7).status());
        assertTrue(calculator.calculate(false,example(),day,7).evidence().batches().isEmpty());
    }
    WeightConfig config() {
        return new WeightConfig(true,true,true,new BigDecimal("0.5"),new BigDecimal("0.2"),new BigDecimal("0.3"),new BigDecimal("0.5"),30,1,"EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1");
    }
}
