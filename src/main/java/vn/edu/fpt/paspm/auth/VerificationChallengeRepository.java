package vn.edu.fpt.paspm.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VerificationChallengeRepository {
    private final JdbcTemplate jdbc;

    public VerificationChallengeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ChallengeMetadata> findMetadata(int userId) {
        return jdbc.query(
                        "SELECT purpose,code_hash,issued_at FROM verification_challenge WHERE user_id=?",
                        (resultSet, rowNum) -> new ChallengeMetadata(
                                resultSet.getString("purpose"),
                                resultSet.getString("code_hash"),
                                resultSet.getTimestamp("issued_at").toInstant()
                        ),
                        userId
                )
                .stream()
                .findFirst();
    }

    public void replace(int userId, String purpose, String codeHash, Instant expiresAt, Instant issuedAt) {
        jdbc.update("DELETE FROM verification_challenge WHERE user_id=?", userId);
        jdbc.update(
                "INSERT INTO verification_challenge(user_id,purpose,code_hash,expires_at,issued_at,attempts,consumed) " +
                        "VALUES(?,?,?,?,?,0,FALSE)",
                userId,
                purpose,
                codeHash,
                Timestamp.from(expiresAt),
                Timestamp.from(issuedAt)
        );
    }

    public Optional<Challenge> lockByUserId(int userId) {
        return jdbc.query(
                        "SELECT purpose,code_hash,expires_at,attempts,consumed " +
                                "FROM verification_challenge WHERE user_id=? FOR UPDATE",
                        (resultSet, rowNum) -> new Challenge(
                                resultSet.getString("purpose"),
                                resultSet.getString("code_hash"),
                                resultSet.getTimestamp("expires_at").toInstant(),
                                resultSet.getInt("attempts"),
                                resultSet.getBoolean("consumed")
                        ),
                        userId
                )
                .stream()
                .findFirst();
    }

    public void incrementAttempts(int userId) {
        jdbc.update(
                "UPDATE verification_challenge SET attempts=attempts+1 WHERE user_id=?",
                userId
        );
    }

    public void consume(int userId) {
        jdbc.update(
                "UPDATE verification_challenge SET consumed=TRUE WHERE user_id=?",
                userId
        );
    }

    public record Challenge(String purpose, String codeHash, Instant expiresAt, int attempts, boolean consumed) {
    }

    public record ChallengeMetadata(String purpose, String codeHash, Instant issuedAt) {
    }
}
