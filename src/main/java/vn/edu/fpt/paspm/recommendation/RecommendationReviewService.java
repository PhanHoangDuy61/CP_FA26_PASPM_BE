package vn.edu.fpt.paspm.recommendation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import static vn.edu.fpt.paspm.recommendation.RecommendationContracts.*;

@Service
public class RecommendationReviewService {
    private final RecommendationRepository repository;private final ProductEligibilityService eligibility;
    private final RecommendationService queries;private final CatalogAccess access;private final AuthService auth;
    public RecommendationReviewService(RecommendationRepository repository,ProductEligibilityService eligibility,RecommendationService queries,CatalogAccess access,AuthService auth){this.repository=repository;this.eligibility=eligibility;this.queries=queries;this.access=access;this.auth=auth;}
    @Transactional(isolation=Isolation.READ_COMMITTED) @PreAuthorize("hasRole('MERCHANDISER')")
    public RecView review(Jwt jwt,long id,RecReviewRequest request,ReviewStatus next) {
        int store=access.currentStoreId(jwt);var rec=repository.find(store,id,true);
        if(rec.version()!=request.expectedVersion()||rec.status()!=ReviewStatus.PENDING_REVIEW)throw new ApiException(409,"Recommendation review version or state changed");
        if(next!=ReviewStatus.APPROVED&&next!=ReviewStatus.REJECTED)throw new ApiException(400,"Invalid review transition");
        var current=eligibility.check(store,rec.products(),rec.policy());
        if(next==ReviewStatus.APPROVED&&current.status()!=EligibilityStatus.ELIGIBLE)throw new ApiException(409,"Current product inventory is ineligible or requires recheck");
        repository.review(rec,auth.current(jwt).id(),request,next,current);
        return queries.view(repository.find(store,id,false),null,null);
    }
}
