package vn.edu.fpt.paspm.auth;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.Contracts.Token;
import vn.edu.fpt.paspm.security.RateLimits;

@RestController
@RequestMapping("/api/auth")
public class SessionController {
    private final RefreshSessionService sessions;
    private final SessionHttpSupport cookies;
    private final RateLimits limits;

    public SessionController(RefreshSessionService sessions, SessionHttpSupport cookies, RateLimits limits) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.limits = limits;
    }

    @Operation(summary = "Rotate refresh session", security = {}, description = "Requires refresh cookie and matching CSRF cookie / X-CSRF-Token header; returns fresh accessToken and csrfToken. Reuse revokes the session family.")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PostMapping("/refresh")
    public Token refresh(HttpServletRequest request, HttpServletResponse response) {
        cookies.noStore(response);
        cookies.validateOrigin(request);
        limits.hit("refresh:" + request.getRemoteAddr(), 60, 60);
        String csrf = cookies.csrf(request);
        try {
            return cookies.write(sessions.rotate(cookies.refresh(request), csrf), response);
        } catch (ApiException e) {
            if (e.status() == 401) cookies.clear(response);
            throw e;
        }
    }

    @Operation(summary = "Log out this session", description = "Requires Bearer token, refresh cookie and matching CSRF cookie / X-CSRF-Token. Revokes this session and clears cookies.")
    @PostMapping("/logout")
    public Map<String, String> logout(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, HttpServletResponse response) {
        return logout(jwt, request, response, false);
    }

    @Operation(summary = "Log out all sessions", description = "Requires Bearer token, refresh cookie and CSRF pair. Revokes all sessions and increments the account token version.")
    @PostMapping("/logout-all")
    public Map<String, String> logoutAll(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request, HttpServletResponse response) {
        return logout(jwt, request, response, true);
    }

    private Map<String, String> logout(Jwt jwt, HttpServletRequest request, HttpServletResponse response, boolean all) {
        cookies.noStore(response);
        cookies.validateOrigin(request);
        sessions.logout(jwt, cookies.refresh(request), cookies.csrf(request), all);
        cookies.clear(response);
        return Map.of("message", all ? "All sessions revoked" : "Session revoked");
    }
}
