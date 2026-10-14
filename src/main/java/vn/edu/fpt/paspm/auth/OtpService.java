package vn.edu.fpt.paspm.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.security.RateLimits;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@Service
public class OtpService {
    private final UserRepository userRepository;
    private final VerificationChallengeRepository challenges;
    private final JavaMailSender mail;
    private final AuthService auth;
    private final RateLimits limits;
    private final String secret;
    private final String from;
    private final long ttl;
    private final long cooldown;
    private final SecureRandom random = new SecureRandom();

    public OtpService(
            UserRepository userRepository,
            VerificationChallengeRepository challenges,
            JavaMailSender mail,
            AuthService auth,
            RateLimits limits,
            @Value("${paspm.otp-secret}") String secret,
            @Value("${paspm.mail-from}") String from,
            @Value("${paspm.otp-ttl-seconds}") long ttl,
            @Value("${paspm.otp-cooldown-seconds}") long cooldown) {
        this.userRepository = userRepository;
        this.challenges = challenges;
        this.mail = mail;
        this.auth = auth;
        this.limits = limits;
        this.secret = secret;
        this.from = from;
        this.ttl = ttl;
        this.cooldown = cooldown;

        if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || ttl < 60 || ttl > 1800 || cooldown < 30) {
            throw new IllegalStateException("Invalid OTP policy / secret");
        }
    }

    private String hash(int userId, String purpose, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String raw = userId + ":" + purpose + ":" + code;
            return HexFormat.of().formatHex(mac.doFinal(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void request(String email, String purpose, String ip) {
        limits.hit("otp-ip:" + ip, 20, 3600);
        limits.hit("otp-email:" + UserRepository.email(email), 5, 3600);

        var candidate = userRepository.findByEmail(email);
        if (candidate.isEmpty()) {
            return;
        }

        User user = userRepository.lockById(candidate.get().id());
        if (!"PENDING_VERIFICATION".equals(user.status()) || !purpose.equals(user.purpose())) {
            return;
        }

        issue(user);
    }

    public void issue(User user) {
        Instant now = Instant.now();
        if (challenges.findIssuedAt(user.id())
                .map(issuedAt -> issuedAt.plusSeconds(cooldown).isAfter(now))
                .orElse(false)) {
            throw new ApiException(429, "Wait before requesting another OTP");
        }

        String oldHash = challenges.findCodeHash(user.id()).orElse(null);

        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (hash(user.id(), user.purpose(), code).equals(oldHash));

        challenges.replace(
                user.id(),
                user.purpose(),
                hash(user.id(), user.purpose(), code),
                now.plusSeconds(ttl),
                now
        );

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.email());
        message.setSubject("PASPM account verification");
        message.setText("Your " + user.purpose() + " code is " + code + ". Expires in " + ttl / 60 + " minutes. Do not share this code.");
        mail.send(message);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void verify(String email, String code, String purpose, String newPassword, String ip) {
        limits.hit("verify-ip:" + ip, 30, 900);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(400, "Invalid or expired verification"));
        user = userRepository.lockById(user.id());

        var challenge = challenges.lockByUserId(user.id()).orElse(null);
        if (!"PENDING_VERIFICATION".equals(user.status())
                || !purpose.equals(user.purpose())
                || challenge == null) {
            throw new ApiException(400, "Invalid or expired verification");
        }

        if (!purpose.equals(challenge.purpose())
                || challenge.consumed()
                || challenge.attempts() >= 5
                || !challenge.expiresAt().isAfter(Instant.now())) {
            throw new ApiException(400, "Invalid or expired verification");
        }

        if (!MessageDigest.isEqual(
                hash(user.id(), purpose, code).getBytes(StandardCharsets.UTF_8),
                challenge.codeHash().getBytes(StandardCharsets.UTF_8))) {
            challenges.incrementAttempts(user.id());
            throw new ApiException(400, "Invalid or expired verification");
        }

        String passwordHash = null;
        if ("ADMIN_INVITE".equals(purpose)) {
            passwordHash = auth.encodePassword(newPassword);
        }

        challenges.consume(user.id());
        userRepository.activate(user.id(), Instant.now(), passwordHash);
    }
}
