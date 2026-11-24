package vn.edu.fpt.paspm.utility;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.SeasonContracts.*;

/** Q2/Q8: Store-scoped seasons. Merchandiser writes, Store Admin reads; snapshots keep their own copy by value. */
@Service
public class SeasonService {
    private final SeasonRepository seasons;
    private final CatalogAccess access;
    private final UtilityTimeZone timeZone;
    public SeasonService(SeasonRepository seasons,CatalogAccess access,UtilityTimeZone timeZone) {
        this.seasons=seasons;this.access=access;this.timeZone=timeZone;
    }
    @Transactional(readOnly=true)
    public SeasonPage list(Jwt jwt,int page,int size) {
        pagination(page,size);int store=access.currentStoreId(jwt);
        return new SeasonPage(seasons.list(store,page,size,timeZone.today()),page,size,seasons.count(store));
    }
    @Transactional(readOnly=true)
    public SeasonView get(Jwt jwt,int id) { return require(access.currentStoreId(jwt),id,false); }
    @Transactional
    public SeasonView create(Jwt jwt,SeasonRequest request) {
        if(request.expectedVersion()!=null)throw new ApiException(400,"expectedVersion is not allowed when creating a season");
        validate(request);
        int store=access.currentStoreId(jwt);
        if(seasons.nameTaken(store,request.name(),null))throw new ApiException(409,"A season with this name already exists in the Store");
        try { return require(store,seasons.create(store,actor(jwt),request),false); }
        catch(DataIntegrityViolationException e) { throw new ApiException(409,"A season with this name already exists in the Store"); }
    }
    @Transactional
    public SeasonView update(Jwt jwt,int id,SeasonRequest request) {
        if(request.expectedVersion()==null)throw new ApiException(400,"expectedVersion is required");
        validate(request);
        int store=access.currentStoreId(jwt);
        require(store,id,true);
        if(seasons.nameTaken(store,request.name(),id))throw new ApiException(409,"A season with this name already exists in the Store");
        try { if(!seasons.update(store,id,actor(jwt),request))throw new ApiException(409,"Season was changed by another request; reload it"); }
        catch(DataIntegrityViolationException e) { throw new ApiException(409,"A season with this name already exists in the Store"); }
        return require(store,id,false);
    }
    @Transactional
    public void delete(Jwt jwt,int id) {
        int store=access.currentStoreId(jwt);
        var season=require(store,id,true);
        if(season.productCount()>0)throw new ApiException(409,"Unassign every product before deleting the season");
        seasons.delete(store,id);
    }
    @Transactional(readOnly=true)
    public SeasonProductPage products(Jwt jwt,int id,int page,int size) {
        pagination(page,size);int store=access.currentStoreId(jwt);require(store,id,false);
        return new SeasonProductPage(seasons.products(store,id,page,size),page,size,seasons.productCount(store,id));
    }
    /** @return true when a new assignment was created; false when it already existed (idempotent). */
    @Transactional
    public boolean assign(Jwt jwt,int id,int product) {
        int store=access.currentStoreId(jwt);require(store,id,true);
        if(!seasons.productExists(store,product))throw new ApiException(404,"Product not found");
        // DEC-20261124-03: a soft-deleted product keeps its old assignments but never gets a new one
        if(seasons.productDeleted(store,product))throw new ApiException(409,"Product is deleted and cannot be assigned to a season","PRODUCT_DELETED");
        try { return seasons.assign(store,id,product,actor(jwt)); }
        catch(DataIntegrityViolationException e) { return false; }
    }
    @Transactional
    public void unassign(Jwt jwt,int id,int product) {
        int store=access.currentStoreId(jwt);require(store,id,true);
        if(!seasons.productExists(store,product))throw new ApiException(404,"Product not found");
        if(!seasons.unassign(store,id,product))throw new ApiException(404,"Product is not assigned to this season");
    }
    private SeasonView require(int store,int id,boolean lock) {
        return seasons.find(store,id,timeZone.today(),lock).orElseThrow(()->new ApiException(404,"Season not found"));
    }
    private static void validate(SeasonRequest request) {
        if(request.name()==null||request.name().isBlank())throw new ApiException(400,"name is required");
        if(request.startDate().isAfter(request.endDate()))throw new ApiException(400,"startDate must not be after endDate");
    }
    private static int actor(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    private static void pagination(int page,int size) { if(page<0||size<1||size>100)throw new ApiException(400,"Invalid pagination"); }
}
