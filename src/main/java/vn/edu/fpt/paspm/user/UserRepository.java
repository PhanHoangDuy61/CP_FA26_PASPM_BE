package vn.edu.fpt.paspm.user;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.Contracts.UserView;

@Repository
public class UserRepository {
    private static final String SELECT =
            "SELECT u.*,r.code FROM app_user u JOIN role r ON r.role_id=u.role_id ";

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static String email(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private final RowMapper<User> mapper = (resultSet, rowNum) -> new User(
            resultSet.getInt("user_id"),
            resultSet.getString("email"),
            resultSet.getString("name"),
            resultSet.getString("code"),
            (Integer) resultSet.getObject("store_id"),
            resultSet.getString("status"),
            resultSet.getString("password_hash"),
            resultSet.getTimestamp("email_verified_at") == null
                    ? null
                    : resultSet.getTimestamp("email_verified_at").toInstant(),
            resultSet.getInt("token_version"),
            resultSet.getString("verification_purpose"),
            resultSet.getString("avatar_key")
    );

    public User findById(int id) {
        return jdbc.query(SELECT + "WHERE user_id=?", mapper, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "User not found"));
    }

    public Optional<User> findByEmail(String email) {
        return jdbc.query(SELECT + "WHERE email=?", mapper, email(email))
                .stream()
                .findFirst();
    }

    public User lockById(int id) {
        return jdbc.query(SELECT + "WHERE user_id=? FOR UPDATE", mapper, id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "User not found"));
    }

    public int findRoleId(String code) {
        return jdbc.query(
                        "SELECT role_id FROM role WHERE code=?",
                        (rs, rowNum) -> rs.getInt(1),
                        code
                )
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(400, "Unknown role"));
    }

    public void validateRoleStoreScope(String roleCode, Integer storeId) {
        findRoleId(roleCode);

        if (Set.of("STORE_ADMIN", "MERCHANDISER").contains(roleCode)) {
            if (storeId == null || jdbc.queryForObject(
                    "SELECT COUNT(*) FROM store WHERE store_id=?",
                    Integer.class,
                    storeId
            ) != 1) {
                throw new ApiException(400, "Staff requires an existing Store");
            }
        } else if (storeId != null) {
            throw new ApiException(400, "Global account must not have a Store");
        }
    }

    public User createPendingUser(
            String name,
            String email,
            String roleCode,
            Integer storeId,
            String passwordHash,
            String verificationPurpose) {
        validateRoleStoreScope(roleCode, storeId);
        KeyHolder key = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO app_user(email,name,role_id,store_id,password_hash,status,verification_purpose) " +
                            "VALUES(?,?,?,?,?,'PENDING_VERIFICATION',?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, email(email));
            ps.setString(2, name == null ? null : name.trim());
            ps.setInt(3, findRoleId(roleCode));
            ps.setObject(4, storeId);
            ps.setString(5, passwordHash);
            ps.setString(6, verificationPurpose);
            return ps;
        }, key);

        return findById(Objects.requireNonNull(key.getKey()).intValue());
    }

    public List<UserView> searchUsers(String q, String role, String status, int page, int size) {
        if (page < 0 || page > 100_000 || size < 1 || size > 100) {
            throw new ApiException(400, "Invalid pagination");
        }

        String sql = SELECT +
                "WHERE (?='' OR LOWER(u.email) LIKE ? OR LOWER(u.name) LIKE ?) " +
                "AND (?='' OR r.code=?) AND (?='' OR u.status=?) " +
                "ORDER BY user_id LIMIT ? OFFSET ?";
        String like = "%" + q.toLowerCase(Locale.ROOT) + "%";

        return jdbc.query(sql, mapper, q, like, like, role, role, status, status, size, page * size)
                .stream()
                .map(User::view)
                .toList();
    }

    public List<Map<String, Object>> listRoles() {
        return jdbc.queryForList("SELECT code,display_name FROM role ORDER BY role_id");
    }

    public void updateName(int id, String name) {
        jdbc.update("UPDATE app_user SET name=? WHERE user_id=?", name, id);
    }

    public void updateStatus(int id, String status) {
        jdbc.update(
                "UPDATE app_user SET status=?,token_version=token_version+1 WHERE user_id=?",
                status,
                id
        );
    }

    public void updateRole(int id, int roleId, Integer storeId) {
        jdbc.update(
                "UPDATE app_user SET role_id=?,store_id=?,token_version=token_version+1 WHERE user_id=?",
                roleId,
                storeId,
                id
        );
    }

    public void activate(int id, Instant verifiedAt, String passwordHash) {
        jdbc.update(
                "UPDATE app_user SET status='ACTIVE',email_verified_at=?,password_hash=COALESCE(?,password_hash),token_version=token_version+1 WHERE user_id=?",
                Timestamp.from(verifiedAt),
                passwordHash,
                id
        );
    }

    public void markEmailVerified(int id, Instant verifiedAt) {
        jdbc.update(
                "UPDATE app_user SET email_verified_at=? WHERE user_id=?",
                Timestamp.from(verifiedAt),
                id
        );
    }

    public void completeRegistration(int id, String name, String passwordHash) {
        jdbc.update(
                "UPDATE app_user SET name=?,password_hash=?,status='ACTIVE',token_version=token_version+1 WHERE user_id=?",
                name,
                passwordHash,
                id
        );
    }

    public void updatePassword(int id, String passwordHash) {
        jdbc.update(
                "UPDATE app_user SET password_hash=?,token_version=token_version+1 WHERE user_id=?",
                passwordHash,
                id
        );
    }

    public void updateAvatar(int id, String avatarKey) {
        jdbc.update("UPDATE app_user SET avatar_key=? WHERE user_id=?", avatarKey, id);
    }

    public void activateBootstrapAdmin(int id, Instant verifiedAt) {
        jdbc.update(
                "UPDATE app_user SET status='ACTIVE',email_verified_at=? WHERE user_id=?",
                Timestamp.from(verifiedAt),
                id
        );
    }
}
