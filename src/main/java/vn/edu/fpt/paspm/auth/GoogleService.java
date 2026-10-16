package vn.edu.fpt.paspm.auth;

import java.time.Instant;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.Contracts.Token;
import vn.edu.fpt.paspm.security.RateLimits;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@Service
public class GoogleService {
    private final UserRepository userRepository;
    private final ExternalIdentityRepository identities;
    private final AuthService auth;
    private final OtpService otp;
    private final RateLimits limits;
    private final String clientId;
    private final JwtDecoder decoder;

    @Autowired
    public GoogleService(
            UserRepository userRepository,
            ExternalIdentityRepository identities,
            AuthService auth,
            OtpService otp,
            RateLimits limits,
            @Value("${paspm.google-client-id}") String clientId) {
        this(
                userRepository,
                identities,
                auth,
                otp,
                limits,
                clientId,
                NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build()
        );
    }

    GoogleService(
            UserRepository userRepository,
            ExternalIdentityRepository identities,
            AuthService auth,
            OtpService otp,
            RateLimits limits,
            String clientId,
            JwtDecoder decoder) {
        this.userRepository = userRepository;
        this.identities = identities;
        this.auth = auth;
        this.otp = otp;
        this.limits = limits;
        this.clientId = clientId;
        this.decoder = decoder;
    }

    public Jwt verify(String token) {
        if (clientId.isBlank()) {
            throw new ApiException(503, "Google sign-in is not configured");
        }

        try {
            Jwt jwt = decoder.decode(token);
            if (!Set.of("https://accounts.google.com", "accounts.google.com").contains(jwt.getClaimAsString("iss"))
                    || !jwt.getAudience().contains(clientId)
                    || !Boolean.TRUE.equals(jwt.getClaim("email_verified"))
                    || jwt.getSubject() == null
                    || jwt.getSubject().isBlank()
                    || jwt.getExpiresAt() == null
                    || !jwt.getExpiresAt().isAfter(Instant.now())) {
                throw new ApiException(401, "Invalid Google identity token");
            }
            return jwt;
        } catch (JwtException | ApiException e) {
            if (e instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(401, "Invalid Google identity token");
        }
    }

    @Transactional
    public void register(String token, String ip) {
        limits.hit("google-register-ip:" + ip, 10, 3600);
        Jwt jwt = verify(token);
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ApiException(401, "Google account does not provide a verified email");
        }
        email = UserRepository.email(email);
        if (email.indexOf('@') <= 0) {
            throw new ApiException(401, "Google account does not provide a verified email");
        }
        limits.hit("google-register-email:" + email, 3, 3600);

        if (identities.findGoogleUserId(jwt.getSubject()).isPresent()
                || userRepository.findByEmail(email).isPresent()) {
            return;
        }

        String name = jwt.getClaimAsString("name");
        if (name == null || name.isBlank()) {
            name = email.substring(0, email.indexOf('@'));
        }
        if (name.length() > 150) {
            name = name.substring(0, 150);
        }

        User user = userRepository.createPendingUser(
                name,
                email,
                "END_CUSTOMER",
                null,
                null,
                "SELF_REGISTER"
        );
        identities.linkGoogle(user.id(), jwt.getSubject());
        otp.issue(user);
    }

    public Token login(String token, String ip) {
        limits.hit("google:" + ip, 20, 900);
        Jwt jwt = verify(token);
        int userId = identities.findGoogleUserId(jwt.getSubject())
                .orElseThrow(() -> new ApiException(401, "Google identity is not linked"));

        User user = userRepository.findById(userId);
        if (!user.active()) {
            throw new ApiException(401, "Account unavailable");
        }
        return auth.token(user);
    }

    @Transactional
    public void link(User user, String token) {
        Jwt jwt = verify(token);
        userRepository.lockById(user.id());

        var linkedUserId = identities.findGoogleUserId(jwt.getSubject());
        if (linkedUserId.isPresent()) {
            if (linkedUserId.get() != user.id()) {
                throw new ApiException(409, "Identity already linked");
            }
            return;
        }

        identities.linkGoogle(
                user.id(),
                jwt.getSubject()
        );
    }
}
