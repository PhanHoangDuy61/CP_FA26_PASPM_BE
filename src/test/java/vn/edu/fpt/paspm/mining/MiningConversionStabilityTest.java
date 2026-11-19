package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.MatrixItem;

/**
 * Opt-in measurement for DEC-20261119-05: mines one frozen matrix (TSV: transaction, product, weighted utility, transaction utility)
 * in the exact mode and in a forced whole-đồng mode with real EFIM, and compares the top-N patterns by utility in đồng.
 */
@EnabledIfSystemProperty(named="paspm.test.stability-matrix",matches=".+")
class MiningConversionStabilityTest {
    @Test void exactAndWholeDongInputsShareTheirTopPatterns() throws Exception {
        var rows=new ArrayList<MatrixItem>();BigDecimal total=BigDecimal.ZERO;
        for(String line:Files.readAllLines(Path.of(System.getProperty("paspm.test.stability-matrix")))){
            String[] f=line.split("\t");
            rows.add(MiningInputTest.row(Integer.parseInt(f[0]),Integer.parseInt(f[1]),f[2],f[3]));
            total=total.add(new BigDecimal(f[2]));
        }
        var snapshot=MiningInputTest.snapshot(total.toPlainString());
        var builder=new MiningInputBuilder();
        var percentage=new BigDecimal(System.getProperty("paspm.test.stability-percentage","0.12"));
        int top=Integer.getInteger("paspm.test.stability-top",50);
        var exact=builder.build(snapshot,rows);
        var whole=builder.buildWholeUnitForComparison(snapshot,rows);
        assertEquals(MiningInputBuilder.MODE_LOSSLESS,exact.mode());assertEquals(MiningInputBuilder.MODE_WHOLE_UNIT,whole.mode());
        int exactMin=builder.threshold(exact,MiningContracts.MinUtilMode.PERCENTAGE,percentage);
        int wholeMin=builder.threshold(whole,MiningContracts.MinUtilMode.PERCENTAGE,percentage);
        var engine=SpmfMiningEngineTest.engine(60000);
        var a=engine.execute(MiningEngine.Algorithm.EFIM,exact,exactMin).patterns();
        var b=engine.execute(MiningEngine.Algorithm.EFIM,whole,wholeMin).patterns();
        var topA=topKeys(a,exact,top);var topB=topKeys(b,whole,top);
        var shared=new HashSet<>(topA);shared.retainAll(topB);
        var all=new HashSet<>(a.keySet());all.retainAll(b.keySet());
        System.out.printf("F05_MODE_STABILITY rows=%d pct=%s exact[divisor=%d minUtilDong=%s patterns=%d] whole[minUtilDong=%s patterns=%d] top%d shared=%d sameOrder=%s allShared=%d%n",
            rows.size(),percentage.toPlainString(),exact.divisor(),exact.originalUtility(exactMin).toPlainString(),a.size(),
            whole.originalUtility(wholeMin).toPlainString(),b.size(),top,shared.size(),topA.equals(topB),all.size());
        assertFalse(a.isEmpty());
    }
    static List<String> topKeys(Map<String,Integer> patterns,MiningInputBuilder.Dataset input,int top){
        return patterns.entrySet().stream()
            .sorted(Comparator.comparing((Map.Entry<String,Integer> e)->input.originalUtility(e.getValue())).reversed().thenComparing(Map.Entry::getKey))
            .limit(top).map(Map.Entry::getKey).toList();
    }
}
