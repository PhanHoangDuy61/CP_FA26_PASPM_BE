package vn.edu.fpt.paspm.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;

@Component
public class SessionOriginGuard {
    private final boolean secure;
    private final Set<String> allowed;

    public SessionOriginGuard(@Value("${paspm.refresh-cookie-secure:true}") boolean secure,
            @Value("${paspm.session.allowed-origins:}") String origins) {
        this.secure = secure;
        this.allowed = Arrays.stream(origins.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }

    public void validate(HttpServletRequest request) {
        if (!secure && !Set.of("localhost", "127.0.0.1", "::1").contains(request.getServerName())) {
            throw new ApiException(503, "Insecure session cookies are restricted to local development");
        }
        String origin = request.getHeader("Origin");
        if (origin == null) return;
        try {
            URI uri = URI.create(origin);
            int port = uri.getPort() < 0 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort();
            boolean same = request.getScheme().equals(uri.getScheme())
                    && request.getServerName().equalsIgnoreCase(uri.getHost()) && request.getServerPort() == port
                    && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && (uri.getRawPath() == null || uri.getRawPath().isEmpty());
            if (same || allowed.contains(origin)) return;
        } catch (IllegalArgumentException ignored) { }
        throw new ApiException(403, "Origin is not permitted");
    }
}
