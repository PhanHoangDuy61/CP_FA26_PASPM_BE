package vn.edu.fpt.paspm.auth;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExternalIdentityRepository {
    private final JdbcTemplate jdbc;

    public ExternalIdentityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Integer> findGoogleUserId(String subject) {
        return jdbc.query(
                        "SELECT user_id FROM external_identity WHERE provider='GOOGLE' AND provider_subject=?",
                        (resultSet, rowNum) -> resultSet.getInt("user_id"),
                        subject
                )
                .stream()
                .findFirst();
    }

    public void linkGoogle(int userId, String subject) {
        jdbc.update(
                "INSERT INTO external_identity(user_id,provider,provider_subject) VALUES(?,'GOOGLE',?)",
                userId,
                subject
        );
    }
}
