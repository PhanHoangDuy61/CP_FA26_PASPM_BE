package vn.edu.fpt.paspm.security;

import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.user.User;

/**
 * F02 controllers must call require before any store-scoped read/write.
 */
@Component
public class StoreScope {
    private static final Set<String> STORE_ROLES = Set.of("STORE_ADMIN", "MERCHANDISER");

    private final AuthService auth;

    public StoreScope(AuthService auth) {
        this.auth = auth;
    }

    public int require(Jwt token, int resourceStoreId) {
        User user = auth.current(token);
        if (!user.active()) {
            throw new ApiException(401, "Account unavailable");
        }
        if ("SYSTEM_ADMIN".equals(user.role())) {
            return resourceStoreId;
        }
        if (user.storeId() == null
                || user.storeId() != resourceStoreId
                || !STORE_ROLES.contains(user.role())) {
            throw new ApiException(403, "Store access denied");
        }
        return user.storeId();
    }
}
