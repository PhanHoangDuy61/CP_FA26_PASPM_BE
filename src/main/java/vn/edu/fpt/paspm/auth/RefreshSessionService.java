package vn.edu.fpt.paspm.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.Contracts.Token;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@Service
public class RefreshSessionService {
    // Absolute family lifetime: rotation never extends it. Raw credentials are never persisted.
    public record Issued(Token credentials, String refreshToken, long maxAge) {}
    private record Refresh(String session, String csrfHash, boolean consumed) {}
    private record Session(int userId, int version, Instant expires, boolean revoked) {}
    private final JdbcTemplate db;
    private final UserRepository users;
    private final AuthService auth;
    private final JwtDecoder decoder;
    private final long ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshSessionService(JdbcTemplate db, UserRepository users, AuthService auth, JwtDecoder decoder,
            @Value("${paspm.session.refresh-ttl-seconds:604800}") long ttl) {
        if (ttl < 60 || ttl > 2592000) throw new IllegalArgumentException("Refresh lifetime must be 60..2592000 seconds");
        this.db=db; this.users=users; this.auth=auth; this.decoder=decoder; this.ttl=ttl;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Issued start(Token login) {
        Jwt jwt=decoder.decode(login.accessToken());
        User user=users.lockById(Integer.parseInt(jwt.getSubject()));
        Number version=jwt.getClaim("ver");
        if (!user.active() || version==null || version.intValue()!=user.version()) throw invalid();
        String sid=UUID.randomUUID().toString();
        Instant expires=Instant.now().plusSeconds(ttl);
        db.update("INSERT INTO auth_session(session_id,user_id,token_version,expires_at) VALUES(?,?,?,?)",sid,user.id(),user.version(),Timestamp.from(expires));
        return issue(user,sid,expires);
    }
    // Reuse must commit family revocation even when the response is 401.
    @Transactional(isolation=Isolation.READ_COMMITTED,noRollbackFor=ApiException.class)
    public Issued rotate(String raw, String csrf) {
        Refresh initial=lookup(raw); checkCsrf(initial,csrf);
        Session initialSession=session(initial.session(),false);
        User user=users.lockById(initialSession.userId());
        Session current=session(initial.session(),true);
        Refresh refresh=lookup(raw);
        if (!valid(current,user) || refresh.consumed()) {
            revoke(initial.session()); throw invalid();
        }
        db.update("UPDATE auth_refresh_token SET consumed=TRUE WHERE token_hash=?",hash(raw));
        return issue(user,initial.session(),current.expires());
    }
    @Transactional(isolation=Isolation.READ_COMMITTED,noRollbackFor=ApiException.class)
    public void logout(Jwt jwt, String raw, String csrf, boolean all) {
        Refresh refresh=lookup(raw); checkCsrf(refresh,csrf);
        User user=users.lockById(Integer.parseInt(jwt.getSubject()));
        Session current=session(refresh.session(),true);
        refresh=lookup(raw); // Re-read after acquiring the account/family locks.
        Number version=jwt.getClaim("ver");
        if (!refresh.session().equals(jwt.getClaimAsString("sid")) || current.userId()!=user.id()
                || version==null || version.intValue()!=user.version() || !valid(current,user)) throw invalid();
        if (refresh.consumed()) { revoke(refresh.session()); throw invalid(); }
        if (all) {
            db.update("UPDATE auth_session SET revoked=TRUE WHERE user_id=?",user.id());
            db.update("UPDATE app_user SET token_version=token_version+1 WHERE user_id=?",user.id());
        } else revoke(refresh.session());
    }
    private Issued issue(User user, String sid, Instant expires) {
        String raw=secret(), csrf=secret();
        db.update("INSERT INTO auth_refresh_token(token_hash,session_id,csrf_hash) VALUES(?,?,?)",hash(raw),sid,hash(csrf));
        long remaining=Math.max(1,java.time.Duration.between(Instant.now(),expires).getSeconds());
        return new Issued(auth.token(user,sid,csrf),raw,remaining);
    }
    private Refresh lookup(String raw) {
        if (raw==null || !raw.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        return db.query("SELECT session_id,csrf_hash,consumed FROM auth_refresh_token WHERE token_hash=?",
            (r,n)->new Refresh(r.getString(1),r.getString(2),r.getBoolean(3)),hash(raw)).stream().findFirst().orElseThrow(RefreshSessionService::invalid);
    }
    private Session session(String sid, boolean lock) {
        return db.query("SELECT user_id,token_version,expires_at,revoked FROM auth_session WHERE session_id=?"+(lock?" FOR UPDATE":""),
            (r,n)->new Session(r.getInt(1),r.getInt(2),r.getTimestamp(3).toInstant(),r.getBoolean(4)),sid).stream().findFirst().orElseThrow(RefreshSessionService::invalid);
    }
    private boolean valid(Session session, User user) {
        return !session.revoked() && session.expires().isAfter(Instant.now()) && user.active() && session.version()==user.version();
    }
    private void revoke(String sid) { db.update("UPDATE auth_session SET revoked=TRUE WHERE session_id=?",sid); }
    private void checkCsrf(Refresh refresh, String csrf) {
        if (csrf==null || !csrf.matches("[A-Za-z0-9_-]{43}") || !equal(refresh.csrfHash(),hash(csrf))) throw new ApiException(403,"Session verification failed (CSRF)");
    }
    private String secret() { byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    static boolean equal(String a,String b) { return a!=null && b!=null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)); }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ApiException invalid() { return new ApiException(401,"Invalid or expired refresh token"); }
}
