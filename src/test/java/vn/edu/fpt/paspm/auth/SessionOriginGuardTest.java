package vn.edu.fpt.paspm.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import vn.edu.fpt.paspm.common.ApiException;
import static org.junit.jupiter.api.Assertions.*;

class SessionOriginGuardTest {
    @Test void insecureModeRequiresLoopbackHostname() {
        var request=new MockHttpServletRequest();request.setServerName("paspm.example");
        assertEquals(503,assertThrows(ApiException.class,()->new SessionOriginGuard(false,"").validate(request)).status());
    }
    @Test void sameAndExplicitOriginsAreAccepted() {
        var guard=new SessionOriginGuard(true,"http://localhost:3000");var request=new MockHttpServletRequest();
        request.addHeader("Origin","http://localhost:3000");guard.validate(request);
        request.removeHeader("Origin");request.addHeader("Origin","http://localhost");guard.validate(request);
    }
    @Test void malformedHostileAndNullOriginsAreRejected() {
        var guard=new SessionOriginGuard(true,"");var request=new MockHttpServletRequest();
        for(String origin:java.util.List.of("null","https://evil.invalid","http://localhost.evil.invalid","http://localhost/path")) {
            request.removeHeader("Origin");request.addHeader("Origin",origin);
            assertEquals(403,assertThrows(ApiException.class,()->guard.validate(request)).status());
        }
    }
}
