package vn.edu.fpt.paspm.catalog;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.security.StoreScope;
import vn.edu.fpt.paspm.user.User;

@Component
public class CatalogAccess {
    private final AuthService auth;
    private final StoreScope storeScope;

    public CatalogAccess(AuthService auth, StoreScope storeScope) {
        this.auth = auth;
        this.storeScope = storeScope;
    }

    public int currentStoreId(Jwt jwt) {
        User user = auth.current(jwt);
        if (user.storeId() == null) {
            throw new ApiException(403, "Store access denied");
        }
        return storeScope.require(jwt, user.storeId());
    }

    public void requireStore(Jwt jwt, int storeId) {
        storeScope.require(jwt, storeId);
    }
}
