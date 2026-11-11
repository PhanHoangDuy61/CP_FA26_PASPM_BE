package vn.edu.fpt.paspm.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.Contracts.Token;

/** Cookie auth is checked explicitly; existing bearer-only business APIs remain stateless. */
@Component
public class SessionHttpSupport {
    private final boolean secure;
    private final Set<String> origins;

    public SessionHttpSupport(@Value("${paspm.session.cookie-secure:true}") boolean secure,
            @Value("${paspm.session.allowed-origins:}") String allowedOrigins) {
        this.secure = secure;
        this.origins = Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public void validateOrigin(HttpServletRequest request) {
        if (!secure && !Set.of("localhost", "127.0.0.1", "::1").contains(request.getServerName())) {
            throw new ApiException(503, "Insecure session cookies are restricted to local development");
        }
        String origin = request.getHeader("Origin");
        if (origin == null) return; // Non-browser clients still require the CSRF cookie/header pair.
        try {
            URI uri = URI.create(origin);
            int port = uri.getPort() < 0 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
            boolean sameOrigin = uri.getScheme() != null && uri.getScheme().equals(request.getScheme())
                    && request.getServerName().equalsIgnoreCase(uri.getHost()) && request.getServerPort() == port
                    && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty());
            if (sameOrigin || origins.contains(origin)) return;
        } catch (IllegalArgumentException ignored) { }
        throw new ApiException(403, "Origin is not permitted");
    }

    public String refresh(HttpServletRequest request) { return cookie(request, name("REFRESH")); }

    public String csrf(HttpServletRequest request) {
        String header = request.getHeader("X-CSRF-Token");
        if (!RefreshSessionService.equal(header, cookie(request, name("CSRF")))) {
            throw new ApiException(403, "Session verification failed (CSRF)");
        }
        return header;
    }

    public Token write(RefreshSessionService.Issued issued, HttpServletResponse response) {
        noStore(response);
        set(response, "REFRESH", issued.refreshToken(), true, issued.maxAge());
        set(response, "CSRF", issued.credentials().csrfToken(), false, issued.maxAge());
        return issued.credentials();
    }

    public void clear(HttpServletResponse response) {
        noStore(response);
        set(response, "REFRESH", "", true, 0);
        set(response, "CSRF", "", false, 0);
    }

    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }

    private void set(HttpServletResponse response, String suffix, String value, boolean httpOnly, long age) {
        response.addHeader("Set-Cookie", ResponseCookie.from(name(suffix), value)
                .httpOnly(httpOnly).secure(secure).path("/").sameSite("Strict").maxAge(age).build().toString());
    }

    private String name(String suffix) { return (secure ? "__Host-" : "") + "PASPM-" + suffix; }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        String value = null;
        for (Cookie cookie : request.getCookies()) {
            if (!name.equals(cookie.getName())) continue;
            if (value != null) throw new ApiException(403, "Ambiguous session cookie");
            value = cookie.getValue();
        }
        return value;
    }
}
