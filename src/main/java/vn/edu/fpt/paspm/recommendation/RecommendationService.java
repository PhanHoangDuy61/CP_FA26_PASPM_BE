package vn.edu.fpt.paspm.recommendation;

import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.*;
import static vn.edu.fpt.paspm.recommendation.RecommendationContracts.*;

@Service
@PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
public class RecommendationService {
    private static final int MAX_GENERATED_CANDIDATES=10000;
    private final FrozenEvidenceReader source;private final RecommendationRules rules;private final RecommendationRepository repository;
    private final RecommendationPolicyService policies;private final ProductEligibilityService eligibility;
    private final CatalogAccess access;private final AuthService auth;private final MiningRepository mining;
    public RecommendationService(FrozenEvidenceReader source,RecommendationRules rules,RecommendationRepository repository,
        RecommendationPolicyService policies,ProductEligibilityService eligibility,CatalogAccess access,AuthService auth,MiningRepository mining) {
        this.source=source;this.rules=rules;this.repository=repository;this.policies=policies;this.eligibility=eligibility;this.access=access;this.auth=auth;this.mining=mining;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) @PreAuthorize("hasRole('MERCHANDISER')")
    public RecGenerationResult generate(Jwt jwt,RecGenerateRequest request) {
        int store=access.currentStoreId(jwt);
        if(new HashSet<>(request.types()).size()!=request.types().size())throw new ApiException(400,"Recommendation types must be unique");
        policies.lock(store);var policy=policies.current(store);var input=source.read(store,request.miningRunId());
        int created=0,reused=0;var ids=new LinkedHashSet<Long>();var skipped=new ArrayList<RecSkipped>();
        var current=new HashMap<String,RecEligibility>();
        for(var pattern:input.patterns()) {
            var result=rules.apply(input,pattern,request.types(),policy);skipped.addAll(result.skipped());
            for(var candidate:result.candidates()) {
                if(ids.size()>=MAX_GENERATED_CANDIDATES)throw new ApiException(409,"Generation exceeds the 10000 candidate safety limit; request fewer types");
                String key=canonical(candidate.productIds());
                String identity=MiningInputBuilder.sha(input.snapshot().snapshotId()+"|"+input.inputHash()+"|"+input.run().minUtilSpmf()+"|"+candidate.utility().stripTrailingZeros().toPlainString()+"|"+policy.ruleVersion()+"|"+policy.version()+"|"+key+"|"+candidate.type()+"|"+candidate.anchor());
                Long id=repository.byIdentity(store,identity);
                if(id==null) {
                    var check=current.computeIfAbsent(key,k->eligibility.check(store,candidate.productIds(),policy));
                    id=repository.create(store,auth.current(jwt).id(),input.snapshot().snapshotId(),identity,key,candidate,policy,check);created++;
                } else reused++;
                repository.source(store,id,new RecSource(input.run().runId(),pattern.patternId(),input.run().requestId(),input.snapshot().snapshotId(),input.run().algorithm().name(),input.run().libraryVersion(),input.run().conversionVersion(),input.inputHash(),input.snapshot().sourceHash(),input.snapshot().matrixHash(),input.minUtilMode(),input.minUtilValue(),input.run().minUtilSpmf(),input.effectiveMinUtility(),pattern.utility(),input.snapshot().asOfDate()));
                ids.add(id);
            }
        }
        return new RecGenerationResult(input.run().runId(),input.snapshot().snapshotId(),policy.version(),policy.ruleVersion(),created,reused,List.copyOf(ids),List.copyOf(skipped));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public RecView get(Jwt jwt,long id){int store=access.currentStoreId(jwt);return view(repository.find(store,id,false),null,null);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public RecPage list(Jwt jwt,Long run,Integer snapshot,RecType type,ReviewStatus status,int page,int size) {
        if(page<0||size<1||size>100)throw new ApiException(400,"page must be >=0 and pageSize in 1..100");
        int store=access.currentStoreId(jwt);if(run!=null)mining.run(store,run);
        long total=repository.count(store,run,snapshot,type,status);
        if(run!=null&&total>MAX_GENERATED_CANDIDATES)throw new ApiException(409,"Rank scope exceeds 10000 candidates; narrow type or reviewStatus filters");
        var stored=repository.list(store,run,snapshot,type,status,run==null?size:MAX_GENERATED_CANDIDATES,run==null?(long)page*size:0);var cache=new HashMap<String,RecEligibility>();
        var views=new ArrayList<RecView>();
        for(var record:stored)views.add(view(record,null,cache));
        if(run!=null)views.sort(Comparator.comparingInt((RecView v)->v.currentEligibility().status().ordinal())
            .thenComparing(RecView::utility,Comparator.reverseOrder())
            .thenComparing(v->v.evidence().supportCount(),Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(v->canonical(v.productIds())).thenComparing(v->v.type().name())
            .thenComparing(RecView::anchorProductId,Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(RecView::recommendationId));
        int start=run==null?0:(int)Math.min((long)page*size,views.size()),end=Math.min(start+size,views.size());var items=new ArrayList<RecView>();
        for(int i=start;i<end;i++)items.add(withRank(views.get(i),run==null?null:i+1));
        return new RecPage(List.copyOf(items),page,size,total,run==null?"CREATED_DESC":"RANKED_WITHIN_SOURCE_RUN");
    }
    RecView view(RecommendationRepository.Stored record,Integer rank,Map<String,RecEligibility> cache) {
        String key=record.policy().minimumSellableQuantity()+"|"+record.key();
        var current=cache==null?eligibility.check(record.store(),record.products(),record.policy()):cache.computeIfAbsent(key,k->eligibility.check(record.store(),record.products(),record.policy()));
        var explanations=new ArrayList<>(record.explanation());explanations.addAll(record.eligibility().warnings());explanations.addAll(current.warnings());
        return new RecView(record.id(),record.store(),record.snapshot(),record.type(),record.products(),record.anchor(),record.utility(),record.status(),record.version(),record.policy().ruleVersion(),record.policy(),record.evidence(),record.eligibility(),current,!record.eligibility().fingerprint().equals(current.fingerprint()),record.status()==ReviewStatus.APPROVED&&current.status()==EligibilityStatus.ELIGIBLE,repository.sources(record.store(),record.id()),List.copyOf(explanations),repository.history(record.store(),record.id()),record.reviewer(),record.reviewedAt(),record.reason(),record.createdAt(),rank);
    }
    private static RecView withRank(RecView v,Integer rank){return new RecView(v.recommendationId(),v.storeId(),v.snapshotId(),v.type(),v.productIds(),v.anchorProductId(),v.utility(),v.reviewStatus(),v.version(),v.ruleVersion(),v.policy(),v.evidence(),v.generationEligibility(),v.currentEligibility(),v.inventoryChanged(),v.readyForConsumption(),v.sources(),v.explanation(),v.reviewHistory(),v.reviewedBy(),v.reviewedAt(),v.reviewReason(),v.createdAt(),rank);}
    private static String canonical(List<Integer> ids){return ids.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(","));}
}
