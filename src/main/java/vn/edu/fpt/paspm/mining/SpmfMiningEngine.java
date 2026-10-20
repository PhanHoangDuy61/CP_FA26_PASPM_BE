package vn.edu.fpt.paspm.mining;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** SPMF is an external GPLv3 dependency, deliberately not bundled into the backend artifact. */
@Component
public class SpmfMiningEngine implements MiningEngine {
    public static final String VERSION="SPMF_2.67a";
    public static final String JAR_HASH="4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a";
    public static final String MEMORY_METHOD="SPMF_SAMPLED_JAVA_HEAP_MIB_NOT_RSS";
    public static final String RUNTIME_METHOD="CHILD_JVM_WALL_TIME_INCLUDING_STARTUP_AND_IO_EXCLUDING_INPUT_PREPARATION";
    private final SpmfRuntime runtime;
    private final long timeoutMillis;
    private final int heapMiB,maxPatterns;
    public SpmfMiningEngine(@Value("${paspm.mining.spmf-jar:}") String jar,
        @Value("${paspm.mining.java-executable:}") String java,
        @Value("${paspm.mining.timeout-millis:60000}") long timeout,
        @Value("${paspm.mining.heap-mib:256}") int heap,
        @Value("${paspm.mining.max-patterns:2000}") int maxPatterns){
        this.runtime=new SpmfRuntime(jar,java,heap);this.timeoutMillis=timeout;this.heapMiB=heap;this.maxPatterns=maxPatterns;
        if(timeout<1||timeout>300000||heap<32||heap>2048||maxPatterns<1||maxPatterns>10000)throw new IllegalArgumentException("Invalid mining resource limits");
    }
    @Override public void requireAvailable(){
        runtime.requireAvailable();
    }
    @Override public Output execute(Algorithm algorithm,MiningInputBuilder.Dataset input,int minUtility)throws Exception {
        if(Thread.currentThread().isInterrupted())throw new ExecutionFailure("INTERRUPTED","Mining execution interrupted");
        requireAvailable();
        Path jar=runtime.jar(),java=runtime.javaExecutable();
        if(minUtility<1||!MiningInputBuilder.sha(input.text()).equals(input.hash()))throw new ExecutionFailure("INVALID_INPUT","Frozen mining input failed integrity validation");
        Path dir=Files.createTempDirectory("paspm-mining-");Process process=null;
        try{
            Path source=dir.resolve("input.txt"),output=dir.resolve("patterns.txt"),log=dir.resolve("process.log");
            Files.writeString(source,input.text(),StandardCharsets.UTF_8);
            var command=List.of(java.toString(),"-Xmx"+heapMiB+"m","-Djava.awt.headless=true","-jar",jar.toString(),"run",algorithm==Algorithm.HUI_MINER?"HUI-Miner":"EFIM",source.toString(),output.toString(),Integer.toString(minUtility));
            long started=System.nanoTime();process=new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            long deadline=started+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while(process.isAlive()){
                if(System.nanoTime()>=deadline)throw new ExecutionFailure("TIMED_OUT","Mining exceeded its execution time limit");
                if((Files.exists(log)&&Files.size(log)>65536)||(Files.exists(output)&&Files.size(output)>4*1024*1024))throw new ExecutionFailure("OUTPUT_LIMIT","Mining exceeded its output size limit");
                process.waitFor(Math.min(20,timeoutMillis),TimeUnit.MILLISECONDS);
            }
            long elapsed=Math.max(1,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));
            if(process.exitValue()!=0)throw new ExecutionFailure("SPMF_FAILED","SPMF process failed; check configured Java compatibility and resource limits");
            if(!Files.isRegularFile(output)||Files.size(output)>4*1024*1024||Files.size(log)>65536)throw new ExecutionFailure("OUTPUT_LIMIT","SPMF output missing or exceeds limits");
            String stats=Files.readString(log);
            var memory=Pattern.compile(algorithm==Algorithm.HUI_MINER?"Memory ~ ([0-9.]+) MB":"Max memory:([0-9.]+)").matcher(stats);
            if(!memory.find())throw new ExecutionFailure("INVALID_METRICS","SPMF memory measurement is missing");
            double heap=Double.parseDouble(memory.group(1));
            if(!Double.isFinite(heap)||heap<0)throw new ExecutionFailure("INVALID_METRICS","SPMF memory measurement is invalid");
            return new Output(parse(Files.readString(output),input,minUtility,deadline),elapsed,heap,
                "SPMF="+VERSION+";jarSha256="+JAR_HASH+";heapMiB="+heapMiB+";os="+System.getProperty("os.name")+";arch="+System.getProperty("os.arch")+";javaExecutableSha256="+executableHash());
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ExecutionFailure("INTERRUPTED","Mining execution interrupted");}
        catch(IOException e){throw new ExecutionFailure("SPMF_FAILED","Mining dependency or temporary IO failed");}
        finally{
            // Clear and restore interruption only after process termination and directory cleanup.
            boolean interrupted=Thread.interrupted();
            try {
                if(process!=null&&process.isAlive()){
                    process.destroyForcibly();
                    try { process.waitFor(5,TimeUnit.SECONDS); } catch(InterruptedException e){interrupted=true;}
                }
            } finally {
                try(var paths=Files.walk(dir)){
                    for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
                } finally { if(interrupted)Thread.currentThread().interrupt(); }
            }
        }
    }
    private String executableHash()throws IOException{
        return SpmfRuntime.hash(runtime.javaExecutable());
    }
    /** Verifies algorithm output utilities by summing frozen input values, never re-evaluating F03 scores/weights. */
    Map<String,Integer> parse(String output,MiningInputBuilder.Dataset input,int minUtility){
        return parse(output,input,minUtility,System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis));
    }
    private Map<String,Integer> parse(String output,MiningInputBuilder.Dataset input,int minUtility,long deadline){
        var txs=new ArrayList<Map<Integer,Integer>>();
        for(String line:input.text().lines().toList()){
            var parts=line.split(":");var ids=parts[0].trim().split(" +");var utils=parts[2].trim().split(" +");var tx=new HashMap<Integer,Integer>();
            for(int i=0;i<ids.length;i++)tx.put(Integer.parseInt(ids[i]),Integer.parseInt(utils[i]));txs.add(tx);
        }
        var patterns=new TreeMap<String,Integer>();
        try{
            for(String line:output.lines().filter(s->!s.isBlank()).toList()){
                if(line.equals("@FILETYPE=\"High-utility itemsets\"") || line.equals("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\""))continue;
                checkDeadline(deadline);
                if(patterns.size()>=maxPatterns)throw new ExecutionFailure("OUTPUT_LIMIT","Pattern count exceeds configured limit");
                var match=Pattern.compile("^([0-9]+(?: +[0-9]+)*) +#UTIL: ([0-9]+) *$").matcher(line.trim());
                if(!match.matches())throw new IllegalArgumentException();
                int utility=Integer.parseInt(match.group(2));if(utility<minUtility)throw new IllegalArgumentException();
                var ids=new TreeSet<Integer>();var products=new TreeSet<Integer>();
                for(String value:match.group(1).split(" +")){
                    int id=Integer.parseInt(value);if(!input.products().containsKey(id)||!ids.add(id))throw new IllegalArgumentException();products.add(input.products().get(id));
                }
                long actual=0;int checked=0;
                for(var tx:txs){if((checked++&255)==0)checkDeadline(deadline);if(tx.keySet().containsAll(ids))for(int id:ids)actual+=tx.get(id);}
                if(actual!=utility)throw new IllegalArgumentException();
                String key=products.stream().map(String::valueOf).collect(Collectors.joining(","));
                if(patterns.putIfAbsent(key,utility)!=null)throw new IllegalArgumentException();
            }
            return Collections.unmodifiableMap(patterns);
        }catch(IllegalArgumentException e){throw new ExecutionFailure("INVALID_OUTPUT","SPMF returned invalid, duplicate, unmapped or inconsistent patterns");}
    }
    private void checkDeadline(long deadline){
        if(Thread.currentThread().isInterrupted())throw new ExecutionFailure("INTERRUPTED","Mining execution interrupted");
        if(System.nanoTime()>=deadline)throw new ExecutionFailure("TIMED_OUT","Mining exceeded its execution time limit");
    }
    public static class ExecutionFailure extends RuntimeException{
        private final String code;
        public ExecutionFailure(String code,String message){super(message);this.code=code;}
        public String code(){return code;}
    }
}
