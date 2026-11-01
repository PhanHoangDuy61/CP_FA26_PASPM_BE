package vn.edu.fpt.paspm.mining;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.mining.SystemStoreReadPolicy.Route;
import static org.junit.jupiter.api.Assertions.*;

class SystemStoreReadPolicyTest {
    private static void assertRefused(SystemStoreReadPolicy policy, Route route) {
        var e=assertThrows(ApiException.class,()->policy.require(route));
        assertEquals(403,e.status());
        assertTrue(e.getMessage().contains("pending Product Owner approval"),e.getMessage());
        assertTrue(e.getMessage().contains("D1"),e.getMessage());
        assertTrue(e.getMessage().contains(route.name()),e.getMessage());
    }
    @Test void nothingIsAllowedByDefault() {
        for(String configured:new String[]{null,""," ","  ,  "}) {
            var policy=new SystemStoreReadPolicy(configured);
            assertTrue(policy.allowed().isEmpty(),"configured=["+configured+"]");
            for(Route route:Route.values())assertRefused(policy,route);
        }
    }
    @Test void opensExactlyTheNamedGroupsIgnoringCaseAndSpaces() {
        var statusAndRuns=new SystemStoreReadPolicy(" requests , RUNS ");   // decision D1 option "b"
        assertDoesNotThrow(()->statusAndRuns.require(Route.REQUESTS));
        assertDoesNotThrow(()->statusAndRuns.require(Route.RUNS));
        assertRefused(statusAndRuns,Route.SNAPSHOTS);
        assertRefused(statusAndRuns,Route.PATTERNS);
        var all=new SystemStoreReadPolicy("SNAPSHOTS,REQUESTS,RUNS,PATTERNS,RUNS");  // decision D1 option "a"; duplicates are harmless
        for(Route route:Route.values())assertDoesNotThrow(()->all.require(route));
    }
    @Test void unknownOrCatchAllNamesFailStartupInsteadOfOpeningOrSilentlyIgnoring() {
        for(String bad:List.of("ALL","true","*","REQUEST","requests;runs","REQUESTS,PATTERN","1")) {
            var e=assertThrows(IllegalArgumentException.class,()->new SystemStoreReadPolicy(bad),bad);
            assertTrue(e.getMessage().contains("SNAPSHOTS")&&e.getMessage().contains("PATTERNS"),"message must list the valid groups: "+e.getMessage());
        }
    }
}
