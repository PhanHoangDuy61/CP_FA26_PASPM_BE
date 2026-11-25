package vn.edu.fpt.paspm.mining;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.*;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;

class MiningInputTest {
    final MiningInputBuilder builder=new MiningInputBuilder();
    static BigDecimal d(String x){return new BigDecimal(x);}
    static SnapshotView snapshot(String total){
        var date=LocalDate.of(2026,10,19);
        return new SnapshotView(1,1,1,date,date,date,new FrozenConfiguration(WeightConfig.defaults(),new LambdaPolicy(d("0"),d("0.5"),1),new EffectiveCoefficients(d("0"),d("0"),d("0")),date,"F04_DATABASE_V1",6,"HALF_UP","PRODUCT_ID_V1","Asia/Ho_Chi_Minh"),"a".repeat(64),"b".repeat(64),d(total),6,"READY",Instant.now(),List.of());
    }
    static MatrixItem row(int tx,int product,String u,String total){return new MatrixItem(tx,product,product,1,d(u),null,null,null,"NOT_APPLICABLE",BigDecimal.ONE,d(u),d(total),null,null,null,null);}
    @Test void convertsExactlyAndMapsSortedProductsDeterministically(){
        var rows=List.of(row(2,99,"0.2","0.2"),row(1,99,"0.4","0.5"),row(1,10,"0.1","0.5"));
        var a=builder.build(snapshot("0.7"),rows);
        assertEquals(100000,a.divisor());assertEquals(7,a.totalUtility());
        assertEquals(Map.of(1,10,2,99),a.products());assertEquals("1 2:5:1 4\n2:2:2\n",a.text());
        var reversed=new ArrayList<>(rows);Collections.reverse(reversed);
        assertEquals(a.hash(),builder.build(snapshot("0.7"),reversed).hash());
        assertEquals(3,builder.threshold(a,MiningContracts.MinUtilMode.PERCENTAGE,d("30")));
        assertEquals(4,builder.threshold(a,MiningContracts.MinUtilMode.ABSOLUTE,d("0.300001")));
    }
    @Test void rejectsEmptyNegativeDuplicatePrecisionOverflowAndBadTotals(){
        assertThrows(RuntimeException.class,()->builder.build(snapshot("0"),List.of()));
        assertThrows(RuntimeException.class,()->builder.build(snapshot("1"),List.of(row(1,1,"-1","-1"))));
        assertThrows(RuntimeException.class,()->builder.build(snapshot("2"),List.of(row(1,1,"1","2"),row(1,1,"1","2"))));
        assertThrows(RuntimeException.class,()->builder.build(snapshot("1"),List.of(row(1,1,"0.0000001","0.0000001"))));
        assertThrows(RuntimeException.class,()->builder.build(snapshot("2147.483649"),List.of(row(1,1,"2147.483648","2147.483649"),row(1,2,"0.000001","2147.483649"))));
        assertThrows(RuntimeException.class,()->builder.build(snapshot("1"),List.of(row(1,1,"2","2"))));
        assertThrows(RuntimeException.class,()->builder.build(snapshot("1"),List.of(row(1,1,"1","2"))));
        var bad=row(1,1,"1","1");
        var mapping=new MatrixItem(1,1,2,1,bad.baseUtility(),null,null,null,"AVAILABLE",BigDecimal.ONE,d("1"),d("1"),null,null,null,null);
        assertThrows(RuntimeException.class,()->builder.build(snapshot("1"),List.of(mapping)));
    }
    @Test void validatesThresholdsAndZeroPolicy(){
        var a=builder.build(snapshot("0"),List.of(row(1,1,"0","0")));
        assertEquals(1,builder.threshold(a,MiningContracts.MinUtilMode.ABSOLUTE,d("0.000001")));
        assertThrows(RuntimeException.class,()->builder.threshold(a,MiningContracts.MinUtilMode.PERCENTAGE,d("1")));
        var b=builder.build(snapshot("1"),List.of(row(1,1,"1","1")));
        for(String value:List.of("0","-1","100.000001"))assertThrows(RuntimeException.class,()->builder.threshold(b,MiningContracts.MinUtilMode.PERCENTAGE,d(value)));
        assertThrows(RuntimeException.class,()->builder.threshold(b,MiningContracts.MinUtilMode.ABSOLUTE,d("0.0000001")));
        assertThrows(RuntimeException.class,()->builder.threshold(b,MiningContracts.MinUtilMode.ABSOLUTE,d("2147483648")));
    }
    @Test void acceptsProductCountAtFoodMartScaleAndRejectsOneAboveConfiguredLimit(){
        int supported=MiningInputBuilder.MAX_ITEMS;
        assertEquals(1600,supported);
        var rows=new ArrayList<MatrixItem>();
        for(int product=1;product<=supported;product++)rows.add(row(1,product,"1",Integer.toString(supported)));

        var dataset=builder.build(snapshot(Integer.toString(supported)),rows);

        assertEquals(supported,dataset.products().size());
        assertEquals(1,dataset.transactionCount());
        assertEquals(1,dataset.products().get(1));
        assertEquals(supported,dataset.products().get(supported));
        rows.add(row(1,supported+1,"1",Integer.toString(supported+1)));
        assertThrows(RuntimeException.class,()->builder.build(snapshot(Integer.toString(supported+1)),rows));
    }

    @Test void efimInt32GuardAcceptsMaxTransactionUtilityAndRejectsOneAbove(){
        var atMax=List.of(row(1,1,"2147.483646","2147.483647"),row(1,2,"0.000001","2147.483647"));
        var accepted=builder.build(snapshot("2147.483647"),atMax);
        assertEquals(Integer.MAX_VALUE,accepted.totalUtility());

        var aboveMax=List.of(row(1,1,"2147.483647","2147.483648"),row(1,2,"0.000001","2147.483648"));
        var failure=assertThrows(ApiException.class,()->builder.build(snapshot("2147.483648"),aboveMax));
        assertEquals(400,failure.status());
        assertEquals("Transaction utility exceeds SPMF int32 capacity",failure.getMessage());
    }

    @Test void efimInt32GuardChecksProductTwuAtExactBoundary(){
        var atMax=List.of(row(1,7,"1073.741823","1073.741823"),
            row(2,7,"1073.741824","1073.741824"),row(3,8,"0.000001","0.000001"));
        var accepted=builder.build(snapshot("2147.483648"),atMax);
        assertEquals(2147483648L,accepted.totalUtility());

        var aboveMax=List.of(row(1,7,"1073.741824","1073.741824"),
            row(2,7,"1073.741824","1073.741824"),row(3,8,"0.000001","0.000001"));
        var failure=assertThrows(ApiException.class,()->builder.build(snapshot("2147.483649"),aboveMax));
        assertEquals(400,failure.status());
        assertEquals("Product 7 TWU exceeds SPMF int32 capacity",failure.getMessage());
    }

    @Test void globalTotalMayExceedInt32WhenEveryProductTwuFits(){
        var rows=List.of(row(1,11,"1500","1500"),row(2,22,"1500","1500"),row(3,33,"0.000001","0.000001"));
        var dataset=builder.build(snapshot("3000.000001"),rows);
        assertEquals(3000000001L,dataset.totalUtility());
        assertEquals(750000001,builder.threshold(dataset,MiningContracts.MinUtilMode.PERCENTAGE,d("25")));
    }

    @Test void convertedPercentageMinUtilAboveInt32IsRejectedClearly(){
        var rows=new ArrayList<MatrixItem>();
        for(int tx=1;tx<=9;tx++)rows.add(row(tx,tx,"1000","1000"));
        rows.add(row(10,10,"0.000001","0.000001"));
        var dataset=builder.build(snapshot("9000.000001"),rows);
        assertTrue(dataset.totalUtility()>Integer.MAX_VALUE);
        var failure=assertThrows(ApiException.class,()->builder.threshold(dataset,MiningContracts.MinUtilMode.PERCENTAGE,d("25")));
        assertEquals(400,failure.status());
        assertEquals("Converted minUtil exceeds SPMF int32 capacity",failure.getMessage());
    }
}
