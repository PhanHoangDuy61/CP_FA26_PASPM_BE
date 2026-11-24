package vn.edu.fpt.paspm.security;

import org.springframework.jdbc.core.JdbcTemplate;

import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.user.User;

/**
 * DEC-20261123-01 §4: the single rule for an INACTIVE Store.
 * <ul>
 * <li>{@link #requireActiveStore(User)} — the central guard: users of an INACTIVE Store get 403 {@code STORE_INACTIVE}.
 * Called where the user row (and with it the Store status) is loaded: JWT authentication and token issuance/refresh.</li>
 * <li>{@link #requireActiveForNewWork(JdbcTemplate, int)} — paths without a Store user (System Admin creating work for a
 * Store, assigning users to it): 409 {@code STORE_INACTIVE}, reactivate first.</li>
 * </ul>
 */
public final class StoreStatusGuard {
    public static final String CODE = "STORE_INACTIVE";
    public static final String MESSAGE = "Store is inactive";

    private StoreStatusGuard() {
    }

    public static ApiException inactive() {
        return new ApiException(403, MESSAGE, CODE);
    }

    public static void requireActiveStore(User user) {
        if (user.storeInactive()) {
            throw inactive();
        }
    }

    public static void requireActiveForNewWork(JdbcTemplate jdbc, int storeId) {
        String status = jdbc.query("SELECT status FROM store WHERE store_id=?", (rs, n) -> rs.getString(1), storeId)
                .stream().findFirst().orElse(null);
        if ("INACTIVE".equals(status)) {
            throw new ApiException(409, "Store is inactive; reactivate it first", CODE);
        }
    }
}
