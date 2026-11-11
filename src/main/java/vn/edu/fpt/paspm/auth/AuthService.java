package vn.edu.fpt.paspm.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.Contracts.ChangePassword;
import vn.edu.fpt.paspm.common.Contracts.Login;
import vn.edu.fpt.paspm.common.Contracts.Token;
import vn.edu.fpt.paspm.security.RateLimits;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwords;
    private final AuthSessionService sessions;
    private final RateLimits limits;
    private final String dummy;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwords,
            AuthSessionService sessions,
            RateLimits limits) {
        this.userRepository = userRepository;
        this.passwords = passwords;
        this.sessions = sessions;
        this.limits = limits;
        this.dummy = passwords.encode(UUID.randomUUID().toString());
    }

    public void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(400, "Password exceeds bcrypt byte limit");
        }
    }

    public String encodePassword(String password) {
        validatePasswordLength(password);
        return passwords.encode(password);
    }

    @Transactional
    public AuthCredentials login(Login request, String ip) {
        limits.hit("login-ip:" + ip, 30, 900);
        limits.hit("login-user:" + UserRepository.email(request.email()), 10, 900);
        validatePasswordLength(request.password());

        User user = userRepository.findByEmail(request.email()).orElse(null);
        boolean match = passwords.matches(
                request.password(),
                user == null || user.passwordHash() == null ? dummy : user.passwordHash()
        );

        if (user == null || !match || !user.active()) {
            throw new ApiException(401, "Invalid credentials or account unavailable");
        }

        return sessions.issue(user);
    }

    @Transactional
    public void changePassword(Jwt jwt, ChangePassword request) {
        validatePasswordLength(request.currentPassword());
        validatePasswordLength(request.newPassword());

        User user = userRepository.lockById(Integer.parseInt(jwt.getSubject()));
        Number tokenVersion = jwt.getClaim("ver");
        if (!user.active() || tokenVersion == null || tokenVersion.intValue() != user.version()) {
            throw new ApiException(401, "Invalid or expired token");
        }
        if (user.passwordHash() == null) {
            throw new ApiException(400, "Password change is unavailable for this account");
        }
        if (!passwords.matches(request.currentPassword(), user.passwordHash())) {
            throw new ApiException(401, "Current password is incorrect");
        }
        if (passwords.matches(request.newPassword(), user.passwordHash())) {
            throw new ApiException(400, "New password must differ from current password");
        }

        userRepository.updatePassword(user.id(), encodePassword(request.newPassword()));
        sessions.revokeUserSessions(user.id());
    }

    public Token token(User user) {
        return sessions.accessToken(user);
    }

    public AuthCredentials issueCredentials(User user) {
        return sessions.issue(user);
    }

    public AuthCredentials refresh(String refreshToken, String csrfToken, String ip) {
        return sessions.refresh(refreshToken, csrfToken, ip);
    }

    public void logout(Jwt jwt, String csrfToken) {
        sessions.logout(jwt, csrfToken);
    }

    public void logoutAll(Jwt jwt, String csrfToken) {
        sessions.logoutAll(jwt, csrfToken);
    }

    public User current(Jwt jwt) {
        return userRepository.findById(Integer.parseInt(jwt.getSubject()));
    }
}
