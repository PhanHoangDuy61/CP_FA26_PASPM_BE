package vn.edu.fpt.paspm.transaction.importing;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class TransactionMoneyBoundsTest {
    static boolean moneyError(String value) {
        // A missing SKU deliberately avoids a product lookup; inspect only the money-field error.
        var row=new TransactionCsvParser.ParsedCsvRow(2,Map.of("source_order_id","bounds", "occurred_at","2026-10-19T05:00:00Z",
            "sku","", "quantity","1", "unit_price",value, "unit_cost","0", "status","COMPLETED"));
        return new TransactionOrderValidator(null).validateAndGroup(1,List.of(row)).get(0).errors().stream().anyMatch(e->e.field().equals("unit_price"));
    }
    @ParameterizedTest @ValueSource(strings={"0","-0.00","1E2","9999999999.99","12.3000"})
    void validDecimalRepresentationsKeepExistingAcceptance(String value){assertFalse(moneyError(value));}
    @ParameterizedTest @ValueSource(strings={"-0.01","1E10","0.001","10000000000","1.001"})
    void valuesOutsideDecimalTwelveTwoRemainBusinessErrors(String value){assertTrue(moneyError(value));}
    @Test void hostileExponentsAreRejectedInsideABoundedWorkerHeap()throws Exception{
        Process child=new ProcessBuilder(System.getProperty("java.home")+"/bin/java","-Xmx32m","-cp",System.getProperty("java.class.path"),BoundedProbe.class.getName())
            .redirectErrorStream(true).start();
        try{
            assertTrue(child.waitFor(20,TimeUnit.SECONDS),"Bounded decimal probe must complete promptly");
            String safeOutput=new String(child.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0,child.exitValue(),"Expected bounded validation without expansion: "+safeOutput);
        }finally{if(child.isAlive())child.destroyForcibly();}
    }
    public static class BoundedProbe {
        public static void main(String[] args){
            try{
                if(!moneyError("1E100000000")||!moneyError("1E-100000000")||moneyError("0E100000000"))System.exit(3);
                System.out.println("BOUNDED_VALIDATION_OK");
            }catch(OutOfMemoryError e){System.out.println("EXPONENT_EXPANSION_OOM");System.exit(2);}
            catch(Throwable e){System.out.println("PROBE_FAILED_"+e.getClass().getSimpleName());System.exit(4);}
        }
    }
}
