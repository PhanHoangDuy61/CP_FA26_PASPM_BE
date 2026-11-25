package vn.edu.fpt.paspm.recommendation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.ExpiryScoreCalculator;
import vn.edu.fpt.paspm.utility.UtilityProductRepository;
import vn.edu.fpt.paspm.utility.UtilityTimeZone;

/** Current Store-local expiry alerts, independent of historical recommendation evidence. */
@Service
public class RecommendationExpiryService {
    public record RecExpiryPage(List<RecExpiryBatch> items,int page,int pageSize,long totalElements,
        LocalDate asOfDate,String timeZone,Instant checkedAt,List<String> warnings) {}
    public record RecExpiryBatch(int productId,int batchId,LocalDate expiryDate,long daysRemaining,
        long remainingQuantity,int expiryPushDays,boolean pushRequired,Instant checkedAt,String status,List<String> warnings) {}
    private final UtilityProductRepository products;
    private final ExpiryScoreCalculator calculator;
    private final CatalogAccess access;
    private final UtilityTimeZone timeZone;

    public RecommendationExpiryService(UtilityProductRepository products,ExpiryScoreCalculator calculator,
        CatalogAccess access,UtilityTimeZone timeZone) {
        this.products=products;this.calculator=calculator;this.access=access;this.timeZone=timeZone;
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public RecExpiryPage list(Jwt jwt,Integer productId,int page,int pageSize) {
        if(page<0||pageSize<1||pageSize>100)throw new ApiException(400,"Invalid pagination");
        int store=access.currentStoreId(jwt);
        if(productId!=null)products.product(store,productId);
        LocalDate date=timeZone.today();
        Instant checkedAt=Instant.now();
        List<RecExpiryBatch> rows=new ArrayList<>();
        List<String> warnings=new ArrayList<>();
        for(var product:products.pushCandidates(store,productId)) {
            var result=calculator.calculate(product.id(),product.requiresExpiry(),products.batches(store,product.id()),date,product.expiryPushDays());
            List<String> itemWarnings="MISSING_METADATA".equals(result.status())
                ?List.of("Product "+product.id()+": missing required expiry metadata"):List.of();
            warnings.addAll(itemWarnings);
            for(var batch:result.evidence().batches()) {
                if(batch.pushRequired())rows.add(new RecExpiryBatch(product.id(),batch.batchId(),batch.expiryDate(),
                    batch.daysRemaining(),batch.remainingQuantity(),batch.expiryPushDays(),true,checkedAt,result.status(),itemWarnings));
            }
        }
        rows.sort(Comparator.comparing(RecExpiryBatch::expiryDate).thenComparingInt(RecExpiryBatch::productId).thenComparingInt(RecExpiryBatch::batchId));
        int start=(int)Math.min((long)page*pageSize,rows.size());
        return new RecExpiryPage(List.copyOf(rows.subList(start,Math.min(start+pageSize,rows.size()))),
            page,pageSize,rows.size(),date,timeZone.id(),checkedAt,List.copyOf(warnings));
    }
}
