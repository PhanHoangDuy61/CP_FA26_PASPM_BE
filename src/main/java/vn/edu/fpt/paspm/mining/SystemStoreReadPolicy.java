package vn.edu.fpt.paspm.mining;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;

/**
 * Which System Admin read routes under /api/system/mining/stores/{storeId}/ are released.
 * Decision D1 (confirmed 2026-11-02): SNAPSHOTS, REQUESTS and RUNS are released by default; raw PATTERNS are never released to
 * System Admin (Store Admin and Merchandiser keep their Store-scoped pattern route). An operator may close a group by listing only
 * the others (an empty value closes all three). Naming PATTERNS, or any unknown name, fails startup so a typo can neither open
 * nor silently ignore a permission.
 */
@Component
public class SystemStoreReadPolicy {
    public enum Route { SNAPSHOTS, REQUESTS, RUNS }
    public static final String DEFAULT_ROUTES = "SNAPSHOTS,REQUESTS,RUNS";
    private final Set<Route> allowed;
    public SystemStoreReadPolicy(@Value("${paspm.mining.system-store-read.routes:" + DEFAULT_ROUTES + "}") String configured) { this.allowed = parse(configured); }
    private static Set<Route> parse(String configured) {
        var routes = EnumSet.noneOf(Route.class);
        if (configured == null) return routes;
        for (String raw : configured.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) continue;
            if (name.equalsIgnoreCase("PATTERNS"))
                throw new IllegalArgumentException("paspm.mining.system-store-read.routes must not name PATTERNS: raw pattern reads are not released to System Admin (decision D1); use "
                    + Arrays.toString(Route.values()));
            try { routes.add(Route.valueOf(name.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid paspm.mining.system-store-read.routes entry '" + name + "'; use a comma-separated list of "
                    + Arrays.toString(Route.values()), e);
            }
        }
        return routes;
    }
    Set<Route> allowed() { return Set.copyOf(allowed); }
    /** Checked before any Store lookup so a refused call cannot be used to probe which Store ids exist. */
    public void require(Route route) {
        if (!allowed.contains(route))
            throw new ApiException(403, "System Admin reads of Store mining results (" + route + ") are disabled by configuration (decision D1)");
    }
    /** Raw patterns are never released to System Admin, whatever the configuration says. */
    public ApiException patternsRefusal() {
        return new ApiException(403, "System Admin may not read raw mining patterns (decision D1); a Store Admin or Merchandiser of the Store can");
    }
}
