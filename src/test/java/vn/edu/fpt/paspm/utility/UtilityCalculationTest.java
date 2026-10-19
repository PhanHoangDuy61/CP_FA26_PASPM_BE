package vn.edu.fpt.paspm.utility;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityContracts.WeightConfig;

class UtilityCalculationTest {
    final LocalDate day=LocalDate.of(2026,10,17);
    @Test void expiryUsesEarliestEligibleBatchAndBinaryProductThreshold() {
        var calc=new ExpiryScoreCalculator();
        var batches=List.of(new ExpiryScoreCalculator.Batch(day.plusDays(5),20,"ACTIVE"),new ExpiryScoreCalculator.Batch(day.plusDays(40),80,"ACTIVE"));
        assertEquals(BigDecimal.ONE,calc.calculate(true,batches,day,7).score());
        assertEquals(BigDecimal.ZERO,calc.calculate(true,batches,day,3).score());
    }
    @Test void excludesBlockedExpiredTodayAndEmptyBatches() {
        var result=new ExpiryScoreCalculator().calculate(true,List.of(
            new ExpiryScoreCalculator.Batch(day.minusDays(1),4,"ACTIVE"),new ExpiryScoreCalculator.Batch(day,6,"ACTIVE"),
            new ExpiryScoreCalculator.Batch(day.plusDays(2),5,"BLOCKED"),new ExpiryScoreCalculator.Batch(day.plusDays(3),0,"ACTIVE")),day,30);
        assertEquals("NO_SELLABLE_BATCH",result.status());assertEquals(0,result.score().signum());
    }
    @Test void reportsMissingExpiryAndNonExpiringProductsWithoutInventingDates() {
        var batches=List.of(new ExpiryScoreCalculator.Batch(null,5,"ACTIVE"));
        var calc=new ExpiryScoreCalculator();
        assertEquals("MISSING_METADATA",calc.calculate(true,batches,day,30).status());
        assertNull(calc.calculate(true,batches,day,30).score());
        assertEquals("NOT_APPLICABLE",calc.calculate(false,batches,day,30).status());
        assertEquals(0,calc.calculate(false,batches,day,30).score().signum());
    }
    @Test void supportsFrozenHistoricalDecimalScoresWithoutChangingMath() {
        BigDecimal base=UtilityMath.base(3,new BigDecimal("10000"),new BigDecimal("6000"),null);
        var cfg=new WeightConfig(true,true,true,new BigDecimal("0.5"),new BigDecimal("0.2"),new BigDecimal("0.3"),new BigDecimal("0.5"),30,1,"F03_LINEAR_V1");
        BigDecimal weight=UtilityMath.weight(cfg,new BigDecimal("0.8"),new BigDecimal("0.6"),new BigDecimal("0.9"));
        assertEquals(0,weight.compareTo(new BigDecimal("1.395")));
        assertEquals(new BigDecimal("16740.000000"),UtilityMath.weighted(base,weight));
    }
    @Test void disabledAndSingleCriteriaHaveCorrectContributions() {
        assertEquals(0,UtilityMath.weight(WeightConfig.defaults(),null,null,null).compareTo(BigDecimal.ONE));
        for(int c=0;c<3;c++) {
            var cfg=new WeightConfig(c==0,c==1,c==2,c==0?BigDecimal.ONE:BigDecimal.ZERO,c==1?BigDecimal.ONE:BigDecimal.ZERO,c==2?BigDecimal.ONE:BigDecimal.ZERO,new BigDecimal("0.5"),30,1,"v1");
            assertEquals(0,UtilityMath.weight(cfg,new BigDecimal("0.8"),new BigDecimal("0.8"),new BigDecimal("0.8")).compareTo(new BigDecimal("1.4")));
        }
        var stockAndSeason=new WeightConfig(true,false,true,new BigDecimal("0.25"),BigDecimal.ZERO,new BigDecimal("0.75"),new BigDecimal("0.5"),30,1,"v1");
        assertEquals(0,UtilityMath.weight(stockAndSeason,new BigDecimal("0.8"),null,new BigDecimal("0.4")).compareTo(new BigDecimal("1.25")));
    }
    @Test void rejectsUnsafeNegativeOrMissingBusinessValues() {
        assertThrows(ApiException.class,()->UtilityMath.base(1,BigDecimal.ONE,BigDecimal.TEN,null));
        assertThrows(ApiException.class,()->UtilityMath.base(0,BigDecimal.ONE,BigDecimal.ZERO,null));
        assertThrows(ApiException.class,()->UtilityMath.base(1,null,BigDecimal.ZERO,null));
        assertThrows(ApiException.class,()->UtilityMath.base(1,null,null,new BigDecimal("100000000000000000000")));
        assertEquals(new BigDecimal("5.000000"),UtilityMath.base(2,null,null,new BigDecimal("5")));
        var cfg=new WeightConfig(true,false,false,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal("0.5"),30,1,"v1");
        assertThrows(ApiException.class,()->UtilityMath.weight(cfg,null,null,null));
    }
}
