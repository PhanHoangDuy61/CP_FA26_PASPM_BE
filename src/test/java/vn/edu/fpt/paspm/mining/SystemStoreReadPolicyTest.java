package vn.edu.fpt.paspm.mining;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.SystemStoreReadPolicy.Route;
import static org.junit.jupiter.api.Assertions.*;

/** Decision D1 (confirmed 2026-11-02): SNAPSHOTS, REQUESTS and RUNS are released; PATTERNS never are. */
class SystemStoreReadPolicyTest {
    private static void assertClosed(SystemStoreReadPolicy policy, Route route) {
        var e=assertThrows(ApiException.class,()->policy.require(route));
        assertEquals(403,e.status());
        assertTrue(e.getMessage().contains("D1"),e.getMessage());
        assertTrue(e.getMessage().contains(route.name()),e.getMessage());
    }
    @Test void theShippedDefaultReleasesExactlySnapshotsRequestsAndRuns() {
        var policy=new SystemStoreReadPolicy(SystemStoreReadPolicy.DEFAULT_ROUTES);
        assertEquals(Set.of(Route.SNAPSHOTS,Route.REQUESTS,Route.RUNS),policy.allowed());
        for(Route route:Route.values())assertDoesNotThrow(()->policy.require(route));
    }
    @Test void patternsAreRefusedWhateverIsConfigured() {
        var e=new SystemStoreReadPolicy(SystemStoreReadPolicy.DEFAULT_ROUTES).patternsRefusal();
        assertEquals(403,e.status());
        assertTrue(e.getMessage().contains("raw mining patterns"),e.getMessage());
        for(String attempt:List.of("PATTERNS","patterns"," SNAPSHOTS , Patterns ","SNAPSHOTS,REQUESTS,RUNS,PATTERNS")) {
            var ex=assertThrows(IllegalArgumentException.class,()->new SystemStoreReadPolicy(attempt),attempt);
            assertTrue(ex.getMessage().contains("must not name PATTERNS"),ex.getMessage());
        }
    }
    @Test void anOperatorCanCloseGroupsButCannotOpenMore() {
        var statusAndRuns=new SystemStoreReadPolicy(" requests , RUNS ");
        assertDoesNotThrow(()->statusAndRuns.require(Route.REQUESTS));
        assertDoesNotThrow(()->statusAndRuns.require(Route.RUNS));
        assertClosed(statusAndRuns,Route.SNAPSHOTS);
        for(String configured:new String[]{"",null," ","  ,  "}) {
            var none=new SystemStoreReadPolicy(configured);
            assertTrue(none.allowed().isEmpty(),"configured=["+configured+"]");
            for(Route route:Route.values())assertClosed(none,route);
        }
    }
    @Test void unknownOrCatchAllNamesFailStartupInsteadOfOpeningOrSilentlyIgnoring() {
        for(String bad:List.of("ALL","true","*","REQUEST","requests;runs","REQUESTS,PATTERN","1")) {
            var e=assertThrows(IllegalArgumentException.class,()->new SystemStoreReadPolicy(bad),bad);
            assertTrue(e.getMessage().contains("SNAPSHOTS")&&e.getMessage().contains("RUNS"),"message must list the valid groups: "+e.getMessage());
        }
    }
}
