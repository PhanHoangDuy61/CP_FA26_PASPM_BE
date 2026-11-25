package vn.edu.fpt.paspm.mining;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.time.Duration;

class SpmfMiningEngineTest {
    static final Path JAR=Path.of(System.getProperty("paspm.test.spmf-jar",System.getProperty("paspm.mining.spmf-jar","../spmf_run/spmf.jar"))).toAbsolutePath().normalize();
    static final String JAVA=System.getProperty("paspm.test.spmf-java",System.getProperty("paspm.mining.java-executable",Path.of(System.getProperty("java.home"),"bin","java").toString()));
    static final String INPUT="1 2 3 4 5 6:30:5 10 1 6 3 5\n2 3 4 5:20:8 3 6 3\n1 3 4:8:5 1 2\n1 3 5 7:27:10 6 6 5\n2 3 5 7:11:4 2 3 2\n";
    static MiningInputBuilder.Dataset data(String input){return new MiningInputBuilder.Dataset(input,MiningInputBuilder.sha(input),Map.of(1,1,2,2,3,3,4,4,5,5,6,6,7,7),1000000,96,5);}
    static SpmfMiningEngine engine(long timeout){return new SpmfMiningEngine(JAR.toString(),JAVA,timeout,128,2000);}
    @Test void actualAlgorithmsProduceKnownEightPatternsAndEqualUtilities() throws Exception {
        var runner=engine(10000);var a=runner.execute(MiningEngine.Algorithm.HUI_MINER,data(INPUT),30);var b=runner.execute(MiningEngine.Algorithm.EFIM,data(INPUT),30);
        Map<String,Integer> expected=Map.of("2,4",30,"2,5",31,"1,3,5",31,"2,3,4",34,"2,3,5",37,"2,4,5",36,"2,3,4,5",40,"1,2,3,4,5,6",30);
        assertEquals(expected,a.patterns());assertEquals(expected,b.patterns());assertTrue(a.memoryMiB()>0);assertTrue(b.memoryMiB()>0);
        assertTrue(a.runtimeMillis()>0);
        assertEquals(a.environment().replaceAll(";outputBytes=[0-9]+$",""),b.environment().replaceAll(";outputBytes=[0-9]+$",""));
        assertTrue(a.environment().matches(".*;outputBytes=[1-9][0-9]*$"));assertTrue(b.environment().matches(".*;outputBytes=[1-9][0-9]*$"));
        System.out.println("F05_REAL_SPMF HUI="+a+" EFIM="+b);
    }
    @Test void actualEfimAcceptsGlobalLongTotalWhenTransactionAndTwuBoundsFit() throws Exception {
        var rows=List.of(MiningInputTest.row(1,11,"1500","1500"),MiningInputTest.row(2,22,"1500","1500"),
            MiningInputTest.row(3,33,"0.000001","0.000001"));
        var dataset=new MiningInputBuilder().build(MiningInputTest.snapshot("3000.000001"),rows);
        assertEquals(3000000001L,dataset.totalUtility());
        int minUtil=new MiningInputBuilder().threshold(dataset,MiningContracts.MinUtilMode.PERCENTAGE,new java.math.BigDecimal("25"));

        var output=engine(60000).execute(MiningEngine.Algorithm.EFIM,dataset,minUtil);

        assertEquals(Map.of("11",1500000000,"22",1500000000),output.patterns());
        long recomputed=output.patterns().values().stream().mapToLong(Integer::longValue).sum();
        assertEquals(3000000000L,recomputed);
        assertTrue(output.runtimeMillis()>0);
    }
    /** DEC-20261119-05: actual EFIM on a whole-đồng input; product 1 TWU is exactly int32 max after rounding. */
    @Test void actualEfimMinesTheWholeDongFallbackInputInDong() throws Exception {
        var rows=List.of(MiningInputTest.row(1,1,"1073741823.4","1073741824.0"),MiningInputTest.row(1,2,"0.6","1073741824.0"),
            MiningInputTest.row(2,1,"1073741823.4","1073741823.4"));
        assertThrows(vn.edu.fpt.paspm.common.ApiException.class,()->new MiningInputBuilder().build(MiningInputTest.snapshot("2147483647.4"),rows));
        var prepared=new MiningInputBuilder().prepare(MiningInputTest.snapshot("2147483647.4"),rows,MiningContracts.MinUtilMode.ABSOLUTE,new java.math.BigDecimal("1"));
        assertEquals(MiningInputBuilder.MODE_WHOLE_UNIT,prepared.input().mode());
        assertEquals("1 2:1073741824:1073741823 1\n1:1073741823:1073741823\n",prepared.input().text());

        var output=engine(60000).execute(MiningEngine.Algorithm.EFIM,prepared.input(),prepared.threshold());

        assertEquals(Map.of("1",2147483646,"2",1,"1,2",1073741824),output.patterns());
        assertEquals(0,new java.math.BigDecimal("2147483646").compareTo(prepared.input().originalUtility(output.patterns().get("1"))),"SPMF utility is đồng");
    }
    @Test void actualZeroUtilityAndEmptyResultsAreValid() throws Exception {
        var runner=engine(10000);var input=data("1 2:10:0 10\n1 2:5:0 5\n");
        for(var algo:MiningEngine.Algorithm.values()){
            assertEquals(Map.of("2",15,"1,2",15),runner.execute(algo,input,10).patterns());
            assertTrue(runner.execute(algo,input,16).patterns().isEmpty());
            assertTrue(runner.execute(algo,data("1 2:0:0 0\n"),1).patterns().isEmpty());
        }
    }
    @Test void missingDependencyAndTimeoutAreDistinguished() throws Exception {
        assertThrows(vn.edu.fpt.paspm.common.ApiException.class,()->new SpmfMiningEngine("/not-found.jar",JAVA,1000,128,10).requireAvailable());
        var e=assertThrows(SpmfMiningEngine.ExecutionFailure.class,()->engine(1).execute(MiningEngine.Algorithm.EFIM,data(INPUT),30));
        assertEquals("TIMED_OUT",e.code());
        var failure=assertThrows(vn.edu.fpt.paspm.common.ApiException.class,()->new SpmfMiningEngine(JAR.toString(),"/usr/bin/false",1000,128,10).execute(MiningEngine.Algorithm.EFIM,data(INPUT),30));
        assertEquals(503,failure.status());assertTrue(failure.getMessage().startsWith("SPMF_RUNTIME_UNAVAILABLE:"));
    }
    @Test void interruptionCleansTemporaryFilesAndPreservesInterruptFlag() throws Exception {
        var before=temporaryDirectories();
        Thread.currentThread().interrupt();
        try {
            var failure=assertThrows(SpmfMiningEngine.ExecutionFailure.class,()->engine(10000).execute(MiningEngine.Algorithm.EFIM,data(INPUT),30));
            assertEquals("INTERRUPTED",failure.code());assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        assertEquals(before,temporaryDirectories());
    }
    Set<String> temporaryDirectories()throws Exception{try(var files=Files.list(Path.of(System.getProperty("java.io.tmpdir")))){return files.filter(p->p.getFileName().toString().startsWith("paspm-mining-")).map(Path::toString).collect(java.util.stream.Collectors.toSet());}}
    @Test void parserRejectsDuplicatesUnknownIdsAndWrongUtility(){
        var runner=engine(10000);
        for(String out:List.of("1 #UTIL: 999","99 #UTIL: 30","2 4 #UTIL: 30\n4 2 #UTIL: 30","nonsense","1 1 #UTIL: 30","1 #UTIL: -1"))
            assertThrows(SpmfMiningEngine.ExecutionFailure.class,()->runner.parse(out,data(INPUT),30));
    }
    @Test void actualEfimFailsAtThePatternCapInsteadOfReturningTruncatedResults() throws Exception {
        var ids=new StringJoiner(" ");var utilities=new StringJoiner(" ");var products=new LinkedHashMap<Integer,Integer>();
        for(int id=1;id<=12;id++){ids.add(Integer.toString(id));utilities.add("1");products.put(id,id);}
        String input=ids+":12:"+utilities+"\n";
        var dense=new MiningInputBuilder.Dataset(input,MiningInputBuilder.sha(input),products,1,12,1);
        var capped=new SpmfMiningEngine(JAR.toString(),JAVA,60000,256,2000);
        var failure=assertThrows(SpmfMiningEngine.ExecutionFailure.class,()->capped.execute(MiningEngine.Algorithm.EFIM,dense,1));
        assertEquals("OUTPUT_LIMIT",failure.code());
    }
    @Test void actualEfimOutputBeyondFourMiBStopsAtTheKernelFileCap() throws Exception {
        var ids=new StringJoiner(" ");var utilities=new StringJoiner(" ");var products=new LinkedHashMap<Integer,Integer>();
        for(int id=1;id<=18;id++){ids.add(Integer.toString(id));utilities.add("1");products.put(id,id);}
        String input=ids+":18:"+utilities+"\n";
        var dense=new MiningInputBuilder.Dataset(input,MiningInputBuilder.sha(input),products,1,18,1);
        var capped=new SpmfMiningEngine(JAR.toString(),JAVA,60000,256,2000);
        var failure=assertThrows(SpmfMiningEngine.ExecutionFailure.class,()->capped.execute(MiningEngine.Algorithm.EFIM,dense,1));
        assertEquals("OUTPUT_LIMIT",failure.code());
    }
}
