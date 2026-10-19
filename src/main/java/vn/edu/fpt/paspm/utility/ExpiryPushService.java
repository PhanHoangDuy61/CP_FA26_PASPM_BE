package vn.edu.fpt.paspm.utility;

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
import vn.edu.fpt.paspm.utility.ExpiryScoreCalculator.PushBatch;

@Service
public class ExpiryPushService {
    public record PushPage(List<PushBatch> items,int page,int pageSize,long totalElements,LocalDate asOfDate,String timeZone,List<String> warnings) {}
    private final UtilityProductRepository products;
    private final ExpiryScoreCalculator calculator;
    private final CatalogAccess access;
    private final UtilityTimeZone timeZone;
    public ExpiryPushService(UtilityProductRepository products,ExpiryScoreCalculator calculator,CatalogAccess access,UtilityTimeZone timeZone) {
        this.products=products;this.calculator=calculator;this.access=access;this.timeZone=timeZone;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PushPage list(Jwt jwt,Integer productId,LocalDate requestedDate,int page,int pageSize) {
        if(page<0||pageSize<1||pageSize>100)throw new ApiException(400,"Invalid pagination");
        int store=access.currentStoreId(jwt);
        if(productId!=null)products.product(store,productId); // Conceal foreign IDs using the same 404 convention.
        LocalDate date=requestedDate==null?timeZone.today():requestedDate;
        List<PushBatch> rows=new ArrayList<>();
        List<String> warnings=new ArrayList<>();
        for(var product:products.pushCandidates(store,productId)) {
            var result=calculator.calculate(product.id(),product.requiresExpiry(),products.batches(store,product.id()),date,product.expiryPushDays());
            rows.addAll(result.evidence().batches().stream().filter(PushBatch::pushRequired).toList());
            if("MISSING_METADATA".equals(result.status()))warnings.add("Product "+product.id()+": missing required expiry metadata");
        }
        rows.sort(Comparator.comparing(PushBatch::expiryDate).thenComparingInt(PushBatch::productId).thenComparingInt(PushBatch::batchId));
        int start=(int)Math.min((long)page*pageSize,rows.size());
        return new PushPage(List.copyOf(rows.subList(start,Math.min(start+pageSize,rows.size()))),page,pageSize,rows.size(),date,timeZone.id(),List.copyOf(warnings));
    }
}
