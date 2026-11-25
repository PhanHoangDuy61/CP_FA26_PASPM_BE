package vn.edu.fpt.paspm.recommendation;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.*;
import vn.edu.fpt.paspm.utility.*;

/** Read-only bridge from F05's persisted input and immutable F03 membership. */
@Component
public class FrozenEvidenceReader {
    private final JdbcTemplate jdbc;
    private final MiningRepository mining;
    private final UtilitySnapshotRepository snapshots;
    private final UtilitySettingsRepository json;
    public FrozenEvidenceReader(JdbcTemplate jdbc,MiningRepository mining,UtilitySnapshotRepository snapshots,UtilitySettingsRepository json) {
        this.jdbc=jdbc;this.mining=mining;this.snapshots=snapshots;this.json=json;
    }
    public Source read(int store,long runId) {
        // Resolve ownership before interpreting persisted source data.
        MiningContracts.RunView run;
        try {run=mining.run(store,runId);}catch(ApiException ex){throw ex;}catch(RuntimeException ex){throw invalid();}
        if(!"COMPLETED".equals(run.status()))throw new ApiException(409,"Mining run must be completed");
        try {return readSource(store,run);}catch(RuntimeException ex){throw invalid();}
    }
    private Source readSource(int store,MiningContracts.RunView run) {
        require(run.storeId()==store);
        snapshots.assertAssociationEvidence(store,run.snapshotId());
        var requests=jdbc.query("SELECT * FROM mining_request WHERE store_id=? AND request_id=?",(r,n)->new FrozenRequest(
            json.read(r.getString("frozen_input"),MiningInputBuilder.Dataset.class),
            json.read(r.getString("utility_config_snapshot"),UtilityMatrixContracts.SnapshotView.class),
            r.getString("min_util_mode"),r.getBigDecimal("min_util_value"),r.getInt("min_util_spmf"),
            r.getString("input_snapshot_hash"),r.getInt("utility_snapshot_id")),store,run.requestId());
        require(requests.size()==1);var request=requests.get(0);var input=request.input();var snapshot=request.snapshot();
        var actual=snapshots.find(store,run.snapshotId());
        require(snapshot!=null&&actual!=null&&snapshot.storeId()==store&&actual.storeId()==store
            &&request.snapshotId()==run.snapshotId()&&snapshot.snapshotId()==run.snapshotId()&&actual.snapshotId()==run.snapshotId()
            &&Objects.equals(snapshot.matrixHash(),actual.matrixHash())&&snapshot.matrixHash()!=null
            &&snapshot.matrixHash().matches("[0-9a-f]{64}")&&Objects.equals(snapshot.sourceHash(),actual.sourceHash())
            &&Objects.equals(snapshot.configuration(),actual.configuration())&&Objects.equals(snapshot.asOfDate(),actual.asOfDate())
            &&Objects.equals(snapshot.rangeStart(),actual.rangeStart())&&Objects.equals(snapshot.rangeEnd(),actual.rangeEnd())
            &&snapshot.totalWeightedUtility()!=null&&actual.totalWeightedUtility()!=null
            &&snapshot.totalWeightedUtility().compareTo(actual.totalWeightedUtility())==0
            &&"READY".equals(snapshot.status())&&"READY".equals(actual.status())&&snapshot.roundingScale()==6&&actual.roundingScale()==6);
        require(input!=null&&input.text()!=null&&input.products()!=null&&input.divisor()>0&&input.totalUtility()>=0
            &&input.utilityScale()==6&&"HALF_UP".equals(input.roundingMode())
            &&MiningInputBuilder.VERSION.equals(input.conversionVersion())&&input.conversionVersion().equals(run.conversionVersion())
            &&Objects.equals(input.hash(),MiningInputBuilder.sha(input.text()))&&Objects.equals(input.hash(),run.inputHash())
            &&Objects.equals(input.hash(),request.inputHash())&&request.threshold()==run.minUtilSpmf()&&request.threshold()>0
            &&request.value()!=null&&request.value().signum()>0&&Set.of("ABSOLUTE","PERCENTAGE").contains(request.mode()));
        var configuration=snapshot.configuration();
        require(configuration!=null&&configuration.utilityScale()==6&&"HALF_UP".equals(configuration.roundingMode())
            &&"PRODUCT_ID_V1".equals(configuration.itemMapping()));
        require(input.originalUtility(input.totalUtility()).compareTo(snapshot.totalWeightedUtility())==0);
        var transactions=parse(input);
        require(transactions.size()==input.transactionCount());
        require(snapshots.countItems(snapshot.snapshotId())<=MiningInputBuilder.MAX_ROWS);
        var rows=snapshots.page(store,snapshot.snapshotId(),0,MiningInputBuilder.MAX_ROWS);
        require(rows.size()==snapshots.countItems(snapshot.snapshotId()));
        var matrix=new TreeMap<Integer,Set<Integer>>();
        for(var row:rows) {
            require(row.transactionId()>0&&row.productId()>0&&row.algorithmItemId()==row.productId());
            require(matrix.computeIfAbsent(row.transactionId(),id->new TreeSet<>()).add(row.productId()));
        }
        require(new ArrayList<>(matrix.values()).equals(transactions));
        require(run.patternCount()!=null&&run.patternCount()>=0);
        var evidence=new TransactionEvidence(transactions);
        var patternIds=new HashSet<Long>();
        var patterns=new ArrayList<SourcePattern>();
        for(int page=0;;page++) {
            var result=mining.patterns(store,run.runId(),page,MiningInputBuilder.MAX_ROWS);
            require(result.totalElements()==run.patternCount());
            for(var pattern:result.items()) {
                require(pattern.runId()==run.runId()&&patternIds.add(pattern.patternId()));
                var ids=pattern.productIds();
                require(ids!=null&&!ids.isEmpty()&&ids.size()==pattern.patternSize()
                    &&ids.equals(ids.stream().distinct().sorted().toList())&&input.products().values().containsAll(ids)
                    &&pattern.utility()!=null&&pattern.utilitySpmf()>=0
                    &&input.originalUtility(pattern.utilitySpmf()).compareTo(pattern.utility())==0);
                var support=evidence.measure(ids,null).supportCount();
                require(support!=null&&support>0);
                var items=jdbc.query("SELECT store_id,product_id,algorithm_item_id FROM pattern_item WHERE pattern_id=?",
                    (r,n)->new PatternItem(r.getInt(1),r.getInt(2),r.getInt(3)),pattern.patternId());
                var itemIds=new TreeSet<Integer>();
                for(var item:items)require(item.store()==store&&itemIds.add(item.product())&&Objects.equals(input.products().get(item.dense()),item.product()));
                require(items.size()==ids.size()&&new ArrayList<>(itemIds).equals(ids));
                patterns.add(new SourcePattern(pattern.patternId(),List.copyOf(ids),pattern.utility(),pattern.utilitySpmf(),pattern.patternSize()));
            }
            if((long)(page+1)*MiningInputBuilder.MAX_ROWS>=result.totalElements())break;
            require(!result.items().isEmpty());
        }
        require(patterns.size()==run.patternCount());
        return new Source(run,snapshot,request.mode(),request.value(),input.originalUtility(request.threshold()),input.hash(),List.copyOf(patterns),evidence);
    }
    private static List<Set<Integer>> parse(MiningInputBuilder.Dataset input) {
        var values=new HashSet<Integer>();
        require(input.products().size()<=MiningInputBuilder.MAX_ITEMS);
        for(int i=1;i<=input.products().size();i++) {
            Integer product=input.products().get(i);require(product!=null&&product>0&&values.add(product));
        }
        var transactions=new ArrayList<Set<Integer>>();var seen=new HashSet<Integer>();long total=0;
        if(!input.text().isEmpty())for(String line:input.text().split("\\n",-1)) {
            if(line.isEmpty())continue;
            var sections=line.split(":",-1);require(sections.length==3);
            String[] ids=sections[0].split(" "),utilities=sections[2].split(" ");require(ids.length==utilities.length);
            var transaction=new TreeSet<Integer>();int previous=0;long sum=0;
            for(int i=0;i<ids.length;i++) {
                int dense=Integer.parseInt(ids[i]);Integer product=input.products().get(dense);
                require(dense>previous&&product!=null&&transaction.add(product));previous=dense;seen.add(product);
                long utility=Long.parseLong(utilities[i]);require(utility>=0);sum=Math.addExact(sum,utility);
            }
            require(sum==Long.parseLong(sections[1]));total=Math.addExact(total,sum);transactions.add(Set.copyOf(transaction));
        }
        require(seen.equals(values)&&total==input.totalUtility());return List.copyOf(transactions);
    }
    private static void require(boolean valid){if(!valid)throw invalid();}
    private static ApiException invalid(){return new ApiException(409,"Frozen mining source is inconsistent or unavailable");}
    private record FrozenRequest(MiningInputBuilder.Dataset input,UtilityMatrixContracts.SnapshotView snapshot,String mode,BigDecimal value,int threshold,String inputHash,int snapshotId) {}
    private record PatternItem(int store,int product,int dense) {}
    public record Source(MiningContracts.RunView run,UtilityMatrixContracts.SnapshotView snapshot,String minUtilMode,BigDecimal minUtilValue,BigDecimal effectiveMinUtility,String inputHash,List<SourcePattern> patterns,TransactionEvidence evidence) {}
    public record SourcePattern(long patternId,List<Integer> productIds,BigDecimal utility,int utilitySpmf,int patternSize) {}
}
