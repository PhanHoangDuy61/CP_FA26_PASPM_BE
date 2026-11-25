package vn.edu.fpt.paspm.utility;

import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityMatrixContracts.*;

@Service
public class UtilitySnapshotService {
    private final CatalogAccess access;
    private final UtilitySettingsRepository settings;
    private final UtilityMatrixBuilder builder;
    private final UtilitySnapshotRepository snapshots;
    private final UtilityTimeZone timeZone;
    public UtilitySnapshotService(CatalogAccess access,UtilitySettingsRepository settings,UtilityMatrixBuilder builder,UtilitySnapshotRepository snapshots,UtilityTimeZone timeZone) { this.access=access;this.settings=settings;this.builder=builder;this.snapshots=snapshots;this.timeZone=timeZone; }
    // Lock the policy before the Store config in every writer to serialize policy changes with snapshot capture.
    private FrozenConfiguration freeze(int store,RangeRequest range) {
        var policy=settings.policy(true);
        var config=settings.config(store,true);
        UtilityConfigurationService.requireLambda(config.lambda(),policy,409);
        return new FrozenConfiguration(config,policy,new EffectiveCoefficients(config.effectiveAlpha(),config.effectiveBeta(),config.effectiveGamma()),range.asOfDate(),builder.sourceType(),UtilityMath.SCALE,"HALF_UP","PRODUCT_ID_V1",timeZone.id());
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public MatrixPage preview(Jwt jwt,PreviewRequest request) {
        int page=request.page()==null?0:request.page(),size=request.pageSize()==null?20:request.pageSize();
        pagination(page,size);
        int store=access.currentStoreId(jwt);
        var frozen=freeze(store,request.range());
        var matrix=builder.build(store,request.range(),frozen);
        int start=(int)Math.min((long)page*size,matrix.items().size());
        return new MatrixPage(matrix.items().subList(start,Math.min(start+size,matrix.items().size())),page,size,matrix.items().size(),matrix.totalWeightedUtility(),matrix.sourceHash(),matrix.matrixHash(),matrix.warnings(),matrix.ready(),frozen);
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public SnapshotView create(Jwt jwt,RangeRequest request) {
        int store=access.currentStoreId(jwt);
        var frozen=freeze(store,request);
        var matrix=builder.build(store,request,frozen);
        if(!matrix.ready())throw new ApiException(400,"Snapshot requires complete enabled scores and expiry metadata: "+String.join("; ",matrix.warnings()));
        if(matrix.items().isEmpty())throw new ApiException(400,"No eligible transaction items in the selected range");
        int id=snapshots.create(store,Integer.parseInt(jwt.getSubject()),request,frozen,matrix);
        snapshots.saveItems(id,matrix.items());
        return require(store,id);
    }
    @Transactional(readOnly=true) public SnapshotView get(Jwt jwt,int id) { return require(access.currentStoreId(jwt),id); }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public MatrixPage matrix(Jwt jwt,int id,int page,int size) {
        pagination(page,size);int store=access.currentStoreId(jwt);var snapshot=require(store,id);
        return new MatrixPage(snapshots.page(store,id,page,size),page,size,snapshots.countItems(store,id),snapshot.totalWeightedUtility(),snapshot.sourceHash(),snapshot.matrixHash(),snapshot.warnings(),true,snapshot.configuration());
    }
    private SnapshotView require(int store,int id) {
        var snapshot=snapshots.find(store,id);if(snapshot==null)throw new ApiException(404,"Utility snapshot not found");return snapshot;
    }
    private void pagination(int page,int size) { if(page<0||size<1||size>100)throw new ApiException(400,"Invalid pagination"); }
}
