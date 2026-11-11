package vn.edu.fpt.paspm.auth;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.Contracts.Token;

class SessionHttpSupportTest {
    @Test void productionCookiesAreSecureHostOnlyAndStrict() {
        var support = new SessionHttpSupport(true, "");
        var response = new MockHttpServletResponse();
        support.write(new RefreshSessionService.Issued(new Token("synthetic", 900, "Bearer", "csrf"), "refresh", 600), response);
        var refresh = response.getCookie("__Host-PASPM-REFRESH");
        var csrf = response.getCookie("__Host-PASPM-CSRF");
        assertNotNull(refresh); assertNotNull(csrf);
        assertTrue(refresh.getSecure()); assertTrue(refresh.isHttpOnly());
        assertTrue(csrf.getSecure()); assertFalse(csrf.isHttpOnly());
        assertNull(refresh.getDomain()); assertEquals("/", refresh.getPath());
        assertEquals("Strict", refresh.getAttribute("SameSite"));
        support.clear(response);
        assertTrue(response.getHeaders("Set-Cookie").stream().anyMatch(s -> s.contains("Max-Age=0")));
    }

    @Test void insecureCookiesAreRejectedOutsideLoopback() {
        var request = new MockHttpServletRequest(); request.setServerName("paspm.example");
        assertEquals(503, assertThrows(ApiException.class, () -> new SessionHttpSupport(false, "").validateOrigin(request)).status());
    }

    @Test void onlyExactAllowedOrSameOriginsPass() {
        var support = new SessionHttpSupport(true, "http://localhost:3000");
        var request = new MockHttpServletRequest();
        request.addHeader("Origin", "http://localhost:3000"); support.validateOrigin(request);
        request.removeHeader("Origin"); request.addHeader("Origin", "http://localhost"); support.validateOrigin(request);
        request.removeHeader("Origin"); request.addHeader("Origin", "http://localhost.evil.invalid");
        assertEquals(403, assertThrows(ApiException.class, () -> support.validateOrigin(request)).status());
        request.removeHeader("Origin"); request.addHeader("Origin", "null");
        assertEquals(403, assertThrows(ApiException.class, () -> support.validateOrigin(request)).status());
    }
}
