package vn.edu.fpt.paspm.utility;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityContracts.*;

@Service
public class ProductScoreService {
    private final ProductScoreRepository repository;
    private final CatalogAccess access;
    public ProductScoreService(ProductScoreRepository repository,CatalogAccess access) { this.repository=repository;this.access=access; }
    public Scores get(Jwt jwt,int id) { return find(access.currentStoreId(jwt),id); }
    private Scores find(int store,int id) {
        Scores scores=repository.find(store,id);
        if(scores==null)throw new ApiException(404,"Product not found");
        return scores;
    }
    @Transactional public Scores patch(Jwt jwt,int id,ScorePatch request) {
        if(request.getStockPriorityScore()==null&&request.getSeasonalPriorityScore()==null)throw new ApiException(400,"At least one score must be provided");
        int store=access.currentStoreId(jwt);
        if(!repository.update(store,id,request))throw new ApiException(404,"Product not found");
        return find(store,id);
    }
}
