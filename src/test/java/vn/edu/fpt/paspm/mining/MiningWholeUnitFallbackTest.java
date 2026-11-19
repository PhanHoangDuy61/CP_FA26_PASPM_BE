package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.fpt.paspm.mining.MiningInputTest.d;
import static vn.edu.fpt.paspm.mining.MiningInputTest.row;
import static vn.edu.fpt.paspm.mining.MiningInputTest.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.MiningContracts.MinUtilMode;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.MatrixItem;

/** DEC-20261119-05: exact SPMF input first; whole-đồng HALF_UP input only when an EFIM int32 bound overflows. */
class MiningWholeUnitFallbackTest {
    final MiningInputBuilder builder=new MiningInputBuilder();
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();

    @Test void losslessPathIsBitIdenticalAndKeepsTheFrozenJsonShape() throws Exception {
        var rows=List.of(row(2,99,"0.2","0.2"),row(1,99,"0.4","0.5"),row(1,10,"0.1","0.5"));
        var exact=builder.build(snapshot("0.7"),rows);

        var prepared=builder.prepare(snapshot("0.7"),rows,MinUtilMode.PERCENTAGE,d("30"));

        assertEquals(exact,prepared.input());
        assertEquals("1 2:5:1 4\n2:2:2\n",prepared.input().text());
        assertEquals(exact.hash(),prepared.input().hash());
        assertEquals(100000,prepared.input().divisor());
        assertEquals(MiningInputBuilder.VERSION,prepared.input().conversionVersion());
        assertEquals(MiningInputBuilder.MODE_LOSSLESS,prepared.input().mode());
        assertNull(prepared.input().roundingUnit());
        assertEquals(builder.threshold(exact,MinUtilMode.PERCENTAGE,d("30")),prepared.threshold());
        var stored=json.readTree(json.writeValueAsString(prepared.input()));
        assertFalse(stored.has("conversionMode"),"lossless frozen JSON keeps today's fields");
        assertFalse(stored.has("roundingUnit"));
    }

    @Test void subDongPrecisionOverflowFallsBackAndRecomputesTuAndTwuFromRoundedItems(){
        // Lossless: GCD 100,000 leaves product 7 at 10,737,418,234 per transaction (> int32).
        var rows=List.of(row(1,7,"1073741823.4","1073741824.6"),row(1,8,"0.6","1073741824.6"),row(1,9,"0.6","1073741824.6"),
            row(2,7,"1073741821.4","1073741821.4"));
        var lossless=assertThrows(ApiException.class,()->builder.build(snapshot("2147483646.0"),rows));
        assertEquals("Transaction utility exceeds SPMF int32 capacity",lossless.getMessage());

        var prepared=builder.prepare(snapshot("2147483646.0"),rows,MinUtilMode.ABSOLUTE,d("1"));
        var input=prepared.input();

        assertEquals(MiningInputBuilder.WHOLE_UNIT_VERSION,input.conversionVersion());
        assertEquals(MiningInputBuilder.MODE_WHOLE_UNIT,input.mode());
        assertEquals(0,new BigDecimal("1").compareTo(input.roundingUnit()));
        assertEquals(1,input.divisor());assertEquals(0,input.utilityScale());assertEquals("HALF_UP",input.roundingMode());
        // TU = 1,073,741,823 + 1 + 1 (sum of rounded items), not round(1,073,741,824.6) = 1,073,741,825.
        assertEquals("1 2 3:1073741825:1073741823 1 1\n1:1073741821:1073741821\n",input.text());
        assertEquals(2147483646L,input.totalUtility(),"product 7 TWU = 1,073,741,825 + 1,073,741,821");
        assertEquals(MiningInputBuilder.sha(input.text()),input.hash());
        assertEquals(1,prepared.threshold());
        assertEquals(0,new BigDecimal("1073741823").compareTo(input.originalUtility(1073741823)),"SPMF units are whole đồng");
    }

    @Test void productTwuOverflowOnlyBecauseOfSubDongPrecisionIsAccepted(){
        // Half-đồng precision: GCD 500,000 doubles every value, so each TU fits but product 7 TWU = 3 x 1,200,000,001.
        var rows=List.of(row(1,7,"600000000.5","600000000.5"),row(2,7,"600000000.5","600000000.5"),
            row(3,7,"600000000.5","600000000.5"),row(4,8,"0.5","0.5"));
        var lossless=assertThrows(ApiException.class,()->builder.build(snapshot("1800000002.0"),rows));
        assertEquals("Product 7 TWU exceeds SPMF int32 capacity",lossless.getMessage());

        var prepared=builder.prepare(snapshot("1800000002.0"),rows,MinUtilMode.ABSOLUTE,d("1"));

        assertEquals(MiningInputBuilder.MODE_WHOLE_UNIT,prepared.input().mode());
        assertEquals("1:600000001:600000001\n1:600000001:600000001\n1:600000001:600000001\n2:1:1\n",prepared.input().text(),"HALF_UP rounds .5 up");
        assertEquals(1800000004L,prepared.input().totalUtility(),"rounded total, not the exact 1,800,000,002");
    }

    @Test void itemRoundingToZeroStaysInTheTransactionWithZeroUtility(){
        var rows=List.of(row(1,7,"2147483646.4","2147483646.8"),row(1,8,"0.4","2147483646.8"),row(2,8,"0.000001","0.000001"));
        var prepared=builder.prepare(snapshot("2147483646.800001"),rows,MinUtilMode.ABSOLUTE,d("1"));

        assertEquals(MiningInputBuilder.MODE_WHOLE_UNIT,prepared.input().mode());
        assertEquals(Map.of(1,7,2,8),prepared.input().products());
        assertEquals("1 2:2147483646:2147483646 0\n2:0:0\n",prepared.input().text());
        assertEquals(2,prepared.input().transactionCount());
    }

    @Test void stillAboveInt32AfterRoundingIsRejectedWithTheRoundingModeNamed(){
        var tooLargeTu=List.of(row(1,7,"2147483648","2147483648.000001"),row(1,8,"0.000001","2147483648.000001"));
        var tu=assertThrows(ApiException.class,()->builder.prepare(snapshot("2147483648.000001"),tooLargeTu,MinUtilMode.ABSOLUTE,d("1")));
        assertEquals(400,tu.status());
        assertEquals("Transaction utility exceeds SPMF int32 capacity even after WHOLE_UNIT_HALF_UP rounding to 1 đồng",tu.getMessage());

        var tooLargeTwu=List.of(row(1,7,"1073741824","1073741824"),row(2,7,"1073741824","1073741824"),row(3,8,"0.000001","0.000001"));
        var twu=assertThrows(ApiException.class,()->builder.prepare(snapshot("2147483648.000001"),tooLargeTwu,MinUtilMode.ABSOLUTE,d("1")));
        assertEquals("Product 7 TWU exceeds SPMF int32 capacity even after WHOLE_UNIT_HALF_UP rounding to 1 đồng",twu.getMessage());

        var rows=List.of(row(1,7,"1073741823.4","1073741823.4"),row(2,8,"0.2","0.2"));
        var minUtil=assertThrows(ApiException.class,()->builder.prepare(snapshot("1073741823.6"),rows,MinUtilMode.ABSOLUTE,d("2147483648")));
        assertEquals("Converted minUtil exceeds SPMF int32 capacity even after WHOLE_UNIT_HALF_UP rounding to 1 đồng",minUtil.getMessage());
    }

    @Test void nonCapacityErrorsNeverTriggerTheFallback(){
        var mismatch=List.of(row(1,1,"1","2"));
        var error=assertThrows(ApiException.class,()->builder.prepare(snapshot("1"),mismatch,MinUtilMode.ABSOLUTE,d("1")));
        assertFalse(error.getMessage().contains("WHOLE_UNIT"),error.getMessage());
        var precision=List.of(row(1,1,"0.0000001","0.0000001"));
        assertThrows(ApiException.class,()->builder.prepare(snapshot("0.0000001"),precision,MinUtilMode.ABSOLUTE,d("1")));
        var ok=List.of(row(1,1,"1","1"));
        var badThreshold=assertThrows(ApiException.class,()->builder.prepare(snapshot("1"),ok,MinUtilMode.PERCENTAGE,d("100.000001")));
        assertEquals("Percentage minUtil must be in (0,100]",badThreshold.getMessage());
    }

    @Test void thresholdsAreConvertedInWholeDongWithCeilingAtTheBoundary(){
        var rows=List.of(row(1,7,"1073741823.4","1073741823.4"),row(2,7,"1073741823.4","1073741823.4"),row(3,8,"0.6","0.6"));
        var snapshot=snapshot("2147483647.4");
        // Rounded total = 1,073,741,823 x 2 + 1 = 2,147,483,647.
        assertEquals(1073741824,builder.prepare(snapshot,rows,MinUtilMode.PERCENTAGE,d("50")).threshold(),"ceil(1,073,741,823.5)");
        assertEquals(Integer.MAX_VALUE,builder.prepare(snapshot,rows,MinUtilMode.PERCENTAGE,d("100")).threshold());
        assertEquals(1000,builder.prepare(snapshot,rows,MinUtilMode.ABSOLUTE,d("1000")).threshold());
        assertEquals(1001,builder.prepare(snapshot,rows,MinUtilMode.ABSOLUTE,d("1000.000001")).threshold(),"ABSOLUTE = ceil(value)");
    }

    @Test void convertedMinUtilOverflowAloneAlsoTakesTheFallback(){
        var rows=new ArrayList<MatrixItem>();
        for(int tx=1;tx<=9;tx++)rows.add(row(tx,tx,"1000","1000"));
        rows.add(row(10,10,"0.000001","0.000001"));
        var exact=builder.build(snapshot("9000.000001"),rows);
        assertThrows(ApiException.class,()->builder.threshold(exact,MinUtilMode.PERCENTAGE,d("25")));

        var prepared=builder.prepare(snapshot("9000.000001"),rows,MinUtilMode.PERCENTAGE,d("25"));

        assertEquals(MiningInputBuilder.MODE_WHOLE_UNIT,prepared.input().mode());
        assertEquals(9000,prepared.input().totalUtility());
        assertEquals(2250,prepared.threshold());
    }

    @Test void frozenInputIsReadByVersion() throws Exception {
        String old="{\"text\":\"1 2:5:1 4\\n2:2:2\\n\",\"hash\":\"h\",\"products\":{\"1\":10,\"2\":99},\"divisor\":100000,\"totalUtility\":7,"
            +"\"transactionCount\":2,\"conversionVersion\":\"F03_SCALE6_GCD_INT32_DENSE_V1\",\"utilityScale\":6,\"roundingMode\":\"HALF_UP\"}";
        var legacy=json.readValue(old,MiningInputBuilder.Dataset.class);
        assertEquals(MiningInputBuilder.MODE_LOSSLESS,legacy.mode());
        assertNull(legacy.conversionMode());assertNull(legacy.roundingUnit());
        assertEquals(0,d("0.7").compareTo(legacy.originalUtility(7)));

        var rows=List.of(row(1,7,"1073741823.4","1073741823.4"),row(2,7,"1073741823.4","1073741823.4"),row(3,8,"0.2","0.2"));
        var whole=builder.prepare(snapshot("2147483647.0"),rows,MinUtilMode.ABSOLUTE,d("1")).input();
        var stored=json.writeValueAsString(whole);
        assertTrue(stored.contains("\"conversionMode\":\"WHOLE_UNIT_HALF_UP\""),stored);
        var reread=json.readValue(stored,MiningInputBuilder.Dataset.class);
        assertEquals(whole,reread);
        assertEquals(MiningInputBuilder.MODE_WHOLE_UNIT,reread.mode());
        assertEquals(0,d("2147483646").compareTo(reread.originalUtility(reread.totalUtility())));

        assertEquals(MiningInputBuilder.MODE_LOSSLESS,MiningInputBuilder.modeFor(MiningInputBuilder.VERSION));
        assertEquals(MiningInputBuilder.MODE_WHOLE_UNIT,MiningInputBuilder.modeFor(MiningInputBuilder.WHOLE_UNIT_VERSION));
        assertNull(MiningInputBuilder.modeFor("UNKNOWN_V9"));
    }
}
