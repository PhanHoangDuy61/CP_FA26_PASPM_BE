package vn.edu.fpt.paspm.mining;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.*;

/**
 * Consumes only frozen values: never calls Product, inventory or utility calculation services.
 * DEC-20261119-05 amends DEC-20261117-01: the SPMF input is the exact scale-6/GCD conversion ({@link #VERSION}); only when an
 * EFIM int32 bound (transaction utility, Product TWU or converted minUtil) overflows is a whole-đồng HALF_UP input built
 * instead ({@link #WHOLE_UNIT_VERSION}). Frozen F03 snapshots are never rounded.
 */
@Component
public class MiningInputBuilder {
    public static final String VERSION="F03_SCALE6_GCD_INT32_DENSE_V1";
    public static final String WHOLE_UNIT_VERSION="F03_WHOLE_UNIT_HALF_UP_INT32_DENSE_V1";
    public static final String MODE_LOSSLESS="LOSSLESS", MODE_WHOLE_UNIT="WHOLE_UNIT_HALF_UP";
    static final String WHOLE_UNIT_SUFFIX=" even after "+MODE_WHOLE_UNIT+" rounding to 1 đồng";
    /** Bounded by the measured FoodMart target (1,559 distinct products) with a small input headroom. */
    public static final int MAX_ROWS=20000, MAX_ITEMS=1600;
    /**
     * Frozen SPMF input. {@code conversionMode}/{@code roundingUnit} are written only for whole-unit inputs, so lossless frozen JSON
     * keeps the shape it had before DEC-20261119-05; readers use {@link #mode()}, which dispatches on {@code conversionVersion}.
     */
    public record Dataset(String text,String hash,Map<Integer,Integer> products,long divisor,
                          long totalUtility,int transactionCount,String conversionVersion,int utilityScale,String roundingMode,
                          @JsonInclude(JsonInclude.Include.NON_NULL) String conversionMode,
                          @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal roundingUnit) {
        public Dataset(String text,String hash,Map<Integer,Integer> products,long divisor,long totalUtility,int transactionCount){
            this(text,hash,products,divisor,totalUtility,transactionCount,VERSION,6,"HALF_UP");
        }
        public Dataset(String text,String hash,Map<Integer,Integer> products,long divisor,long totalUtility,int transactionCount,
                       String conversionVersion,int utilityScale,String roundingMode){
            this(text,hash,products,divisor,totalUtility,transactionCount,conversionVersion,utilityScale,roundingMode,null,null);
        }
        public int inputBytes(){return text.getBytes(StandardCharsets.UTF_8).length;}
        public BigDecimal originalUtility(long normalized){
            return BigDecimal.valueOf(normalized).multiply(BigDecimal.valueOf(divisor)).movePointLeft(utilityScale);
        }
        /** {@link #MODE_LOSSLESS}, {@link #MODE_WHOLE_UNIT}, or {@code null} for an unknown version or inconsistent fields. */
        public String mode(){
            String byVersion=modeFor(conversionVersion);
            if(MODE_LOSSLESS.equals(byVersion))return conversionMode==null&&roundingUnit==null&&utilityScale==6?byVersion:null;
            if(MODE_WHOLE_UNIT.equals(byVersion))return MODE_WHOLE_UNIT.equals(conversionMode)&&roundingUnit!=null
                &&roundingUnit.compareTo(BigDecimal.ONE)==0&&utilityScale==0&&divisor==1&&"HALF_UP".equals(roundingMode)?byVersion:null;
            return null;
        }
    }
    public record Prepared(Dataset input,int threshold) {}
    /** Same status and message as before; only marks the three EFIM int32 bounds that may take the whole-unit fallback. */
    static final class Int32Capacity extends ApiException { Int32Capacity(String text){super(400,text);} }
    private record Parsed(TreeMap<Integer,TreeMap<Integer,Long>> transactions,TreeSet<Integer> products,long divisor,long total) {}

    public static String modeFor(String conversionVersion){
        if(VERSION.equals(conversionVersion))return MODE_LOSSLESS;
        if(WHOLE_UNIT_VERSION.equals(conversionVersion))return MODE_WHOLE_UNIT;
        return null;
    }
    public static BigDecimal roundingUnitFor(String conversionVersion){return WHOLE_UNIT_VERSION.equals(conversionVersion)?BigDecimal.ONE:null;}

    /** Exact input and threshold; falls back to whole đồng only when an EFIM int32 bound overflows. */
    public Prepared prepare(SnapshotView snapshot,List<MatrixItem> rows,MiningContracts.MinUtilMode mode,BigDecimal value){
        var parsed=parse(snapshot,rows);
        try {
            var exact=lossless(parsed);
            return new Prepared(exact,threshold(exact,mode,value));
        } catch(Int32Capacity overflow) {
            var whole=wholeUnit(parsed);
            return new Prepared(whole,threshold(whole,mode,value));
        }
    }
    /** Lossless scale-6/GCD input only (no fallback); kept for callers and tests of the exact conversion. */
    public Dataset build(SnapshotView snapshot,List<MatrixItem> rows) {
        return lossless(parse(snapshot,rows));
    }
    /** Test-only access to the whole-unit conversion of a frozen matrix (mode-stability measurement); not reachable from configuration. */
    Dataset buildWholeUnitForComparison(SnapshotView snapshot,List<MatrixItem> rows) {
        return wholeUnit(parse(snapshot,rows));
    }
    private Parsed parse(SnapshotView snapshot,List<MatrixItem> rows) {
        if(snapshot==null)throw new ApiException(404,"Utility snapshot not found");
        if(!"READY".equals(snapshot.status()) || snapshot.roundingScale()!=6 || snapshot.configuration()==null
            || snapshot.configuration().utilityScale()!=6 || !"HALF_UP".equals(snapshot.configuration().roundingMode())
            || !"PRODUCT_ID_V1".equals(snapshot.configuration().itemMapping())
            || snapshot.configuration().config()==null || snapshot.asOfDate()==null
            || !snapshot.asOfDate().equals(snapshot.configuration().analysisDate())
            || snapshot.rangeStart()==null || snapshot.rangeEnd()==null || snapshot.rangeEnd().isBefore(snapshot.rangeStart())
            || !hashValid(snapshot.sourceHash()) || !hashValid(snapshot.matrixHash()))
            throw bad("Snapshot is incomplete or has unsupported frozen metadata");
        if(rows.isEmpty() || rows.size()>MAX_ROWS)throw bad("Snapshot matrix must contain 1.."+MAX_ROWS+" rows");
        var transactions=new TreeMap<Integer,TreeMap<Integer,Long>>();
        var totals=new HashMap<Integer,BigDecimal>();var products=new TreeSet<Integer>();
        long divisor=0,total=0;
        try {
            for(var row:rows){
                if(row.transactionId()<=0 || row.productId()<=0 || row.algorithmItemId()!=row.productId() || row.quantity()<=0)
                    throw bad("Invalid frozen item mapping/quantity at transaction "+row.transactionId());
                long scaled=scaled(row.weightedUtility());
                var tx=transactions.computeIfAbsent(row.transactionId(),k->new TreeMap<>());
                if(tx.putIfAbsent(row.productId(),scaled)!=null)throw bad("Duplicate item in transaction "+row.transactionId());
                if(row.transactionUtility()==null || row.transactionUtility().signum()<0)throw bad("Invalid transaction utility at "+row.transactionId());
                var previous=totals.putIfAbsent(row.transactionId(),row.transactionUtility());
                if(previous!=null && previous.compareTo(row.transactionUtility())!=0)throw bad("Inconsistent transaction utility at "+row.transactionId());
                divisor=gcd(divisor,scaled);total=Math.addExact(total,scaled);products.add(row.productId());
            }
            if(products.size()>MAX_ITEMS)throw bad("Snapshot exceeds "+MAX_ITEMS+" distinct products");
            if(scaled(snapshot.totalWeightedUtility())!=total)throw bad("Snapshot total does not match frozen matrix");
            for(var entry:transactions.entrySet()){
                long sum=0;for(long item:entry.getValue().values())sum=Math.addExact(sum,item);
                if(sum!=scaled(totals.get(entry.getKey())))throw bad("Transaction utility does not match items at "+entry.getKey());
            }
        }catch(ArithmeticException ex){throw bad("Frozen utility precision or signed 64-bit capacity exceeded");}
        return new Parsed(transactions,products,divisor==0?1:divisor,total);
    }
    private Dataset lossless(Parsed parsed) {
        long divisor=parsed.divisor();
        try {
            var mapping=mapping(parsed.products());var reverse=reverse(mapping);
            var text=new StringBuilder();var productTwu=new HashMap<Integer,Long>();
            for(var entry:parsed.transactions().entrySet()){
                long sum=0;var ids=new StringJoiner(" ");var values=new StringJoiner(" ");
                for(var item:entry.getValue().entrySet()){
                    sum=Math.addExact(sum,item.getValue());ids.add(reverse.get(item.getKey()).toString());values.add(Long.toString(item.getValue()/divisor));
                }
                long convertedTransaction=sum/divisor;
                if(convertedTransaction>Integer.MAX_VALUE)throw new Int32Capacity("Transaction utility exceeds SPMF int32 capacity");
                text.append(ids).append(':').append(convertedTransaction).append(':').append(values).append('\n');
                for(int product:entry.getValue().keySet()){
                    long twu=Math.addExact(productTwu.getOrDefault(product,0L),convertedTransaction);
                    if(twu>Integer.MAX_VALUE)throw new Int32Capacity("Product "+product+" TWU exceeds SPMF int32 capacity");
                    productTwu.put(product,twu);
                }
            }
            return new Dataset(text.toString(),sha(text.toString()),Collections.unmodifiableMap(mapping),divisor,parsed.total()/divisor,parsed.transactions().size());
        }catch(ArithmeticException ex){throw bad("Frozen utility precision or signed 64-bit capacity exceeded");}
    }
    /** Each item is rounded to whole đồng HALF_UP; TU, TWU and the total are recomputed from the rounded items only. */
    private Dataset wholeUnit(Parsed parsed) {
        try {
            var mapping=mapping(parsed.products());var reverse=reverse(mapping);
            var text=new StringBuilder();var productTwu=new HashMap<Integer,Long>();long total=0;
            for(var entry:parsed.transactions().entrySet()){
                long sum=0;var ids=new StringJoiner(" ");var values=new StringJoiner(" ");
                for(var item:entry.getValue().entrySet()){
                    long rounded=BigDecimal.valueOf(item.getValue(),6).setScale(0,RoundingMode.HALF_UP).longValueExact();
                    sum=Math.addExact(sum,rounded);ids.add(reverse.get(item.getKey()).toString());values.add(Long.toString(rounded));
                }
                if(sum>Integer.MAX_VALUE)throw bad("Transaction utility exceeds SPMF int32 capacity"+WHOLE_UNIT_SUFFIX);
                text.append(ids).append(':').append(sum).append(':').append(values).append('\n');
                for(int product:entry.getValue().keySet()){
                    long twu=Math.addExact(productTwu.getOrDefault(product,0L),sum);
                    if(twu>Integer.MAX_VALUE)throw bad("Product "+product+" TWU exceeds SPMF int32 capacity"+WHOLE_UNIT_SUFFIX);
                    productTwu.put(product,twu);
                }
                total=Math.addExact(total,sum);
            }
            return new Dataset(text.toString(),sha(text.toString()),Collections.unmodifiableMap(mapping),1,total,parsed.transactions().size(),
                WHOLE_UNIT_VERSION,0,"HALF_UP",MODE_WHOLE_UNIT,BigDecimal.ONE);
        }catch(ArithmeticException ex){throw bad("Frozen utility precision or signed 64-bit capacity exceeded");}
    }
    /** Converts minUtil into the dataset's own SPMF unit: scale 6 / divisor (lossless) or whole đồng (whole-unit). */
    public int threshold(Dataset dataset,MiningContracts.MinUtilMode mode,BigDecimal value){
        if(mode==null || value==null || value.signum()<=0 || value.stripTrailingZeros().scale()>6)throw bad("minUtil must be positive with at most 6 decimal places");
        if(mode==MiningContracts.MinUtilMode.PERCENTAGE && value.compareTo(BigDecimal.valueOf(100))>0)throw bad("Percentage minUtil must be in (0,100]");
        boolean whole=MODE_WHOLE_UNIT.equals(dataset.mode());
        String overflow="Converted minUtil exceeds SPMF int32 capacity"+(whole?WHOLE_UNIT_SUFFIX:"");
        try{
            BigDecimal threshold=mode==MiningContracts.MinUtilMode.PERCENTAGE
                ?value.multiply(BigDecimal.valueOf(dataset.totalUtility())).divide(BigDecimal.valueOf(100),0,RoundingMode.CEILING)
                :value.movePointRight(dataset.utilityScale()).divide(BigDecimal.valueOf(dataset.divisor()),0,RoundingMode.CEILING);
            int result;
            try{result=threshold.intValueExact();}catch(ArithmeticException e){throw whole?bad(overflow):new Int32Capacity(overflow);}
            if(result<1)throw bad("Dataset has no positive utility for percentage minUtil; use a positive absolute threshold");
            return result;
        }catch(ArithmeticException e){throw whole?bad(overflow):new Int32Capacity(overflow);}
    }
    private static LinkedHashMap<Integer,Integer> mapping(TreeSet<Integer> products){
        var mapping=new LinkedHashMap<Integer,Integer>();for(int product:products)mapping.put(mapping.size()+1,product);return mapping;
    }
    private static HashMap<Integer,Integer> reverse(Map<Integer,Integer> mapping){
        var reverse=new HashMap<Integer,Integer>();mapping.forEach((id,product)->reverse.put(product,id));return reverse;
    }
    private static long scaled(BigDecimal x){if(x==null || x.signum()<0)throw bad("Null or negative frozen weighted utility");return x.setScale(6,RoundingMode.UNNECESSARY).movePointRight(6).longValueExact();}
    private static long gcd(long a,long b){while(b!=0){long t=a%b;a=b;b=t;}return a;}
    private static boolean hashValid(String x){return x!=null&&x.matches("[0-9a-f]{64}");}
    public static String sha(String x){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(x.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static ApiException bad(String text){return new ApiException(400,text);}
}
