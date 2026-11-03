package vn.edu.fpt.paspm.recommendation;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import static vn.edu.fpt.paspm.recommendation.RecommendationContracts.*;

@Component
public class RecommendationRules {
    public record Candidate(RecType type,List<Integer> productIds,Integer anchor,BigDecimal utility,
        TransactionEvidence.Evidence evidence,List<String> explanation) {}
    public record Result(List<Candidate> candidates,List<RecSkipped> skipped) {}
    public Result apply(FrozenEvidenceReader.Source source,FrozenEvidenceReader.SourcePattern pattern,List<RecType> types,RecPolicy policy) {
        var candidates=new ArrayList<Candidate>();var skipped=new ArrayList<RecSkipped>();
        for(var type:types.stream().sorted().toList()) {
            var ids=pattern.productIds();
            if(type!=RecType.BANNER&&ids.size()<2){skipped.add(new RecSkipped(pattern.patternId(),type,null,"PATTERN_REQUIRES_AT_LEAST_TWO_PRODUCTS"));continue;}
            if(type==RecType.BUNDLE&&ids.size()>policy.maxBundleSize()){skipped.add(new RecSkipped(pattern.patternId(),type,null,"BUNDLE_EXCEEDS_MAX_SIZE"));continue;}
            List<Integer> anchors=type==RecType.CROSS_SELL?ids:Collections.singletonList(null);
            for(Integer anchor:anchors) {
                var evidence=source.evidence().measure(ids,anchor);String rejected=null;
                if(type==RecType.CROSS_SELL||type==RecType.FBT) {
                    if(!"AVAILABLE".equals(evidence.status()))rejected="TRANSACTION_EVIDENCE_UNAVAILABLE";
                    else if(type==RecType.FBT&&(evidence.supportCount()<policy.minSupportCount()
                        ||BigDecimal.valueOf(evidence.supportCount()).compareTo(policy.minSupportRatio().multiply(BigDecimal.valueOf(evidence.totalTransactions())))<0))rejected="SUPPORT_POLICY_NOT_MET";
                    else if(type==RecType.CROSS_SELL&&(evidence.supportCount()==0||evidence.anchorSupportCount()==null||evidence.anchorSupportCount()==0
                        ||BigDecimal.valueOf(evidence.supportCount()).compareTo(policy.minConfidence().multiply(BigDecimal.valueOf(evidence.anchorSupportCount())))<0))rejected="CONFIDENCE_POLICY_NOT_MET";
                }
                if(rejected!=null){skipped.add(new RecSkipped(pattern.patternId(),type,anchor,rejected));continue;}
                var explanation=List.of("Rule "+policy.ruleVersion()+" / policy "+policy.version()+": "+type,
                    "Completed mining run "+source.run().runId()+", pattern "+pattern.patternId()+", immutable snapshot "+source.snapshot().snapshotId(),
                    "Utility "+pattern.utility().toPlainString()+" uses frozen weighted utility units; it is not purchase probability",
                    "Historical support="+evidence.supportCount()+"/"+evidence.totalTransactions()+"; confidence="+evidence.confidence()+"; anchor="+anchor,
                    "Live eligibility is checked separately; expiry priority is shown per Product and does not alter ranking utility",
                    "Rank policy: eligibility, frozen utility descending within source run, support descending, canonical Product key, type and anchor",
                    "Historical co-occurrence does not establish causality");
                candidates.add(new Candidate(type,ids,anchor,pattern.utility(),evidence,explanation));
            }
        }
        return new Result(List.copyOf(candidates),List.copyOf(skipped));
    }
}
