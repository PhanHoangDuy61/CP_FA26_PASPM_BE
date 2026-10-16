package vn.edu.fpt.paspm.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.security.RateLimits;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

class GoogleRegistrationTest {
    private static final String CLIENT_ID = "paspm-test-client";
    private static final String TOKEN = "google-id-token";
    private static final String SUBJECT = "google-subject";
    private static final String EMAIL = "member@gmail.com";

    private final UserRepository users = mock(UserRepository.class);
    private final ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final OtpService otp = mock(OtpService.class);
    private final RateLimits limits = mock(RateLimits.class);
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final GoogleService service = new GoogleService(
            users,
            identities,
            auth,
            otp,
            limits,
            CLIENT_ID,
            decoder
    );

    @Test
    void registersGoogleIdentityAsPendingCustomerAndSendsOtp() {
        User pendingUser = new User(
                42,
                EMAIL,
                "Gmail User",
                "END_CUSTOMER",
                null,
                "PENDING_VERIFICATION",
                null,
                null,
                0,
                "SELF_REGISTER"
        );
        when(decoder.decode(TOKEN)).thenReturn(identityToken(true));
        when(identities.findGoogleUserId(SUBJECT)).thenReturn(Optional.empty());
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(users.createPendingUser("Gmail User", EMAIL, "END_CUSTOMER", null, null, "SELF_REGISTER"))
                .thenReturn(pendingUser);

        service.register(TOKEN, "127.0.0.1");

        verify(users).createPendingUser("Gmail User", EMAIL, "END_CUSTOMER", null, null, "SELF_REGISTER");
        verify(identities).linkGoogle(42, SUBJECT);
        verify(otp).issue(pendingUser);
        verify(limits).hit("google-register-ip:127.0.0.1", 10, 3600);
        verify(limits).hit("google-register-email:" + EMAIL, 3, 3600);
    }

    @Test
    void doesNotAutomaticallyLinkGoogleIdentityToExistingEmail() {
        User existingUser = new User(
                7,
                EMAIL,
                "Existing User",
                "END_CUSTOMER",
                null,
                "ACTIVE",
                "password-hash",
                Instant.now(),
                0,
                null
        );
        when(decoder.decode(TOKEN)).thenReturn(identityToken(true));
        when(identities.findGoogleUserId(SUBJECT)).thenReturn(Optional.empty());
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser));

        service.register(TOKEN, "127.0.0.1");

        verify(users, never()).createPendingUser(
                "Gmail User",
                EMAIL,
                "END_CUSTOMER",
                null,
                null,
                "SELF_REGISTER"
        );
        verify(identities, never()).linkGoogle(7, SUBJECT);
        verify(otp, never()).issue(existingUser);
    }

    @Test
    void rejectsGoogleIdentityWithoutVerifiedEmail() {
        when(decoder.decode(TOKEN)).thenReturn(identityToken(false));

        assertThrows(ApiException.class, () -> service.register(TOKEN, "127.0.0.1"));

        verify(users, never()).createPendingUser(
                "Gmail User",
                EMAIL,
                "END_CUSTOMER",
                null,
                null,
                "SELF_REGISTER"
        );
    }

    private Jwt identityToken(boolean emailVerified) {
        Instant now = Instant.now();
        return Jwt.withTokenValue(TOKEN)
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .subject(SUBJECT)
                .audience(List.of(CLIENT_ID))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("email", EMAIL)
                .claim("email_verified", emailVerified)
                .claim("name", "Gmail User")
                .build();
    }
}
