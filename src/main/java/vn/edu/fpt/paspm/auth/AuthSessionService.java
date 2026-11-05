package vn.edu.fpt.paspm.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.Contracts.Token;
import vn.edu.fpt.paspm.security.RateLimits;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@Service
public class AuthSessionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long MIN_ACCESS_TTL_SECONDS = 60;
    private static final long MAX_ACCESS_TTL_SECONDS = 3600;
    private static final long MIN_ABSOLUTE_TTL_SECONDS = 3600;
    private static final long MAX_ABSOLUTE_TTL_SECONDS = 90 * 24 * 60 * 60;
    private static final long MIN_IDLE_TTL_SECONDS = 60;
    private static final Duration REPLAY_RETENTION = Duration.ofDays(30);
    private static final int CLEANUP_BATCH_SIZE = 100;

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final JwtEncoder encoder;
    private final RateLimits limits;
    private final long accessTtlSeconds;
    private final long absoluteTtlSeconds;
    private final long idleTtlSeconds;

    public AuthSessionService(
            JdbcTemplate jdbc,
            UserRepository users,
            JwtEncoder encoder,
            RateLimits limits,
            @Value("${paspm.token-ttl-seconds}") long accessTtlSeconds,
            @Value("${paspm.refresh-token-absolute-ttl-seconds}") long absoluteTtlSeconds,
            @Value("${paspm.refresh-token-idle-ttl-seconds}") long idleTtlSeconds) {
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
        this.limits = limits;
        this.accessTtlSeconds = accessTtlSeconds;
        this.absoluteTtlSeconds = absoluteTtlSeconds;
        this.idleTtlSeconds = idleTtlSeconds;

        if (accessTtlSeconds < MIN_ACCESS_TTL_SECONDS || accessTtlSeconds > MAX_ACCESS_TTL_SECONDS) {
            throw new IllegalStateException("TOKEN_TTL_SECONDS must be between 60 and 3600");
        }
        if (absoluteTtlSeconds < MIN_ABSOLUTE_TTL_SECONDS
                || absoluteTtlSeconds > MAX_ABSOLUTE_TTL_SECONDS
                || idleTtlSeconds < MIN_IDLE_TTL_SECONDS
                || idleTtlSeconds > absoluteTtlSeconds) {
            throw new IllegalStateException("Invalid refresh token lifetime policy");
        }
    }

    public Token accessToken(User user) {
        return new Token(accessTokenValue(user, null), accessTtlSeconds, "Bearer", null);
    }

    @Transactional
    public AuthCredentials issue(User requestedUser) {
        User user = users.lockById(requestedUser.id());
        if (!user.active()
                || user.version() != requestedUser.version()
                || !Objects.equals(user.passwordHash(), requestedUser.passwordHash())) {
            throw new ApiException(401, "Invalid credentials or account unavailable");
        }

        Instant now = Instant.now();
        cleanupExpired(now);
        String sessionId = UUID.randomUUID().toString();
        String refreshToken = generateRefreshToken();
        Instant absoluteExpiry = now.plusSeconds(absoluteTtlSeconds);
        Instant idleExpiry = earlier(now.plusSeconds(idleTtlSeconds), absoluteExpiry);

        jdbc.update(
                "INSERT INTO auth_refresh_session(session_id,user_id,token_version,created_at,last_used_at," +
                        "absolute_expires_at,idle_expires_at) VALUES(?,?,?,?,?,?,?)",
                sessionId,
                user.id(),
                user.version(),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(absoluteExpiry),
                java.sql.Timestamp.from(idleExpiry));
        jdbc.update(
                "INSERT INTO auth_refresh_token(session_id,token_hash,created_at) VALUES(?,?,?)",
                sessionId,
                hash(refreshToken),
                java.sql.Timestamp.from(now));

        return credentials(user, sessionId, refreshToken, absoluteExpiry);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthCredentials refresh(String refreshToken, String ip) {
        limits.hit("refresh-ip:" + ip, 30, 900);
        byte[] tokenHash = hash(refreshToken);
        TokenReference reference = jdbc.query(
                        "SELECT s.session_id,s.user_id FROM auth_refresh_token t " +
                                "JOIN auth_refresh_session s ON s.session_id=t.session_id WHERE t.token_hash=?",
                        (rs, row) -> new TokenReference(rs.getString("session_id"), rs.getInt("user_id")),
                        tokenHash)
                .stream()
                .findFirst()
                .orElseThrow(AuthSessionService::invalidRefresh);

        User user = users.lockById(reference.userId());
        Session session = jdbc.query(
                        "SELECT session_id,user_id,token_version,absolute_expires_at,idle_expires_at,revoked_at " +
                                "FROM auth_refresh_session WHERE session_id=? FOR UPDATE",
                        (rs, row) -> new Session(
                                rs.getString("session_id"),
                                rs.getInt("user_id"),
                                rs.getInt("token_version"),
                                rs.getTimestamp("absolute_expires_at").toInstant(),
                                rs.getTimestamp("idle_expires_at").toInstant(),
                                rs.getTimestamp("revoked_at") == null
                                        ? null
                                        : rs.getTimestamp("revoked_at").toInstant()),
                        reference.sessionId())
                .stream()
                .findFirst()
                .orElseThrow(AuthSessionService::invalidRefresh);
        TokenUse tokenUse = jdbc.query(
                        "SELECT token_id,consumed_at FROM auth_refresh_token WHERE token_hash=? AND session_id=? FOR UPDATE",
                        (rs, row) -> new TokenUse(rs.getLong("token_id"), rs.getTimestamp("consumed_at") != null),
                        tokenHash,
                        session.sessionId())
                .stream()
                .findFirst()
                .orElseThrow(AuthSessionService::invalidRefresh);

        Instant now = Instant.now();
        if (session.userId() != reference.userId() || tokenUse.consumed() || session.revokedAt() != null) {
            revokeSession(session.sessionId(), now);
            throw invalidRefresh();
        }
        if (!session.absoluteExpiry().isAfter(now)
                || !session.idleExpiry().isAfter(now)
                || !user.active()
                || user.version() != session.tokenVersion()) {
            revokeSession(session.sessionId(), now);
            throw invalidRefresh();
        }

        String replacement = generateRefreshToken();
        jdbc.update(
                "INSERT INTO auth_refresh_token(session_id,token_hash,created_at) VALUES(?,?,?)",
                session.sessionId(),
                hash(replacement),
                java.sql.Timestamp.from(now));
        jdbc.update(
                "UPDATE auth_refresh_token SET consumed_at=? WHERE token_id=? AND consumed_at IS NULL",
                java.sql.Timestamp.from(now),
                tokenUse.id());
        Instant idleExpiry = earlier(now.plusSeconds(idleTtlSeconds), session.absoluteExpiry());
        jdbc.update(
                "UPDATE auth_refresh_session SET last_used_at=?,idle_expires_at=? WHERE session_id=?",
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(idleExpiry),
                session.sessionId());
        cleanupExpired(now);

        return credentials(user, session.sessionId(), replacement, session.absoluteExpiry());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void logout(Jwt jwt) {
        lockCurrentUser(jwt);
        String sessionId = jwt.getClaimAsString("sid");
        if (sessionId != null) {
            revokeSession(sessionId, Instant.now(), Integer.parseInt(jwt.getSubject()));
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void logoutAll(Jwt jwt) {
        User user = lockCurrentUser(jwt);
        users.incrementTokenVersion(user.id());
        revokeUserSessions(user.id());
    }

    @Transactional
    public void revokeUserSessions(int userId) {
        jdbc.update(
                "UPDATE auth_refresh_session SET revoked_at=COALESCE(revoked_at,?) " +
                        "WHERE user_id=? AND revoked_at IS NULL",
                java.sql.Timestamp.from(Instant.now()),
                userId);
    }

    private User lockCurrentUser(Jwt jwt) {
        User user = users.lockById(Integer.parseInt(jwt.getSubject()));
        Number version = jwt.getClaim("ver");
        if (!user.active() || version == null || version.intValue() != user.version()) {
            throw new ApiException(401, "Invalid or expired token");
        }
        return user;
    }

    private String accessTokenValue(User user, String sessionId) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("paspm")
                .subject(String.valueOf(user.id()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(accessTtlSeconds))
                .id(UUID.randomUUID().toString())
                .claim("ver", user.version());
        if (sessionId != null) {
            claims.claim("sid", sessionId);
        }

        return encoder.encode(
                JwtEncoderParameters.from(
                        org.springframework.security.oauth2.jwt.JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims.build()))
                .getTokenValue();
    }

    private AuthCredentials credentials(User user, String sessionId, String refreshToken, Instant absoluteExpiry) {
        byte[] csrfBytes = new byte[32];
        RANDOM.nextBytes(csrfBytes);
        String csrfToken = Base64.getUrlEncoder().withoutPadding().encodeToString(csrfBytes);
        return new AuthCredentials(
                new Token(accessTokenValue(user, sessionId), accessTtlSeconds, "Bearer", csrfToken),
                refreshToken,
                absoluteExpiry);
    }

    private void revokeSession(String sessionId, Instant at) {
        jdbc.update(
                "UPDATE auth_refresh_session SET revoked_at=COALESCE(revoked_at,?) WHERE session_id=?",
                java.sql.Timestamp.from(at),
                sessionId);
    }

    private void revokeSession(String sessionId, Instant at, int userId) {
        jdbc.update(
                "UPDATE auth_refresh_session SET revoked_at=COALESCE(revoked_at,?) " +
                        "WHERE session_id=? AND user_id=?",
                java.sql.Timestamp.from(at),
                sessionId,
                userId);
    }

    private void cleanupExpired(Instant now) {
        Instant retentionCutoff = now.minus(REPLAY_RETENTION);
        var expiredSessionIds = jdbc.query(
                "SELECT session_id FROM auth_refresh_session WHERE absolute_expires_at<? OR revoked_at<? " +
                        "ORDER BY created_at LIMIT ?",
                (rs, row) -> rs.getString(1),
                java.sql.Timestamp.from(retentionCutoff),
                java.sql.Timestamp.from(retentionCutoff),
                CLEANUP_BATCH_SIZE);
        expiredSessionIds.forEach(id -> jdbc.update(
                "DELETE FROM auth_refresh_session WHERE session_id=?",
                id));
    }

    private static Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ApiException invalidRefresh() {
        return new ApiException(401, "Invalid or expired refresh token");
    }

    private record TokenReference(String sessionId, int userId) {
    }

    private record Session(
            String sessionId,
            int userId,
            int tokenVersion,
            Instant absoluteExpiry,
            Instant idleExpiry,
            Instant revokedAt) {
    }

    private record TokenUse(long id, boolean consumed) {
    }
}
