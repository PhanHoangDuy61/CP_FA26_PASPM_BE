package vn.edu.fpt.paspm.mining;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;

/**
 * Which System Admin read routes under /api/system/mining/stores/{storeId}/ are released (decision D1, PO register).
 * Nothing is released by default; an operator opens exactly the named groups, e.g. {@code REQUESTS,RUNS} for option "b".
 * An unknown name fails startup, so a typo can neither open nor silently ignore a permission.
 */
@Component
public class SystemStoreReadPolicy {
    public enum Route { SNAPSHOTS, REQUESTS, RUNS, PATTERNS }
    private final Set<Route> allowed;
    public SystemStoreReadPolicy(@Value("${paspm.mining.system-store-read.routes:}") String configured) { this.allowed = parse(configured); }
    private static Set<Route> parse(String configured) {
        var routes = EnumSet.noneOf(Route.class);
        if (configured == null) return routes;
        for (String raw : configured.split(",")) {
            String name = raw.trim();
            if (name.isEmpty()) continue;
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
            throw new ApiException(403, "System Admin reads of Store mining results (" + route + ") are pending Product Owner approval (decision D1)");
    }
}
