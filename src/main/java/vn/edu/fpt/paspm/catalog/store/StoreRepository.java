package vn.edu.fpt.paspm.catalog.store;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import vn.edu.fpt.paspm.catalog.store.StoreContracts.AssignedUser;

/** JDBC access to {@code store} (V27 columns). Every write increments {@code version} and sets {@code updated_at}. */
@Repository
public class StoreRepository {
    private final JdbcTemplate jdbc;

    public StoreRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record StoreRow(int storeId, String name, String slug, String status, String address, String phone,
                           String iconKey, String bannerKey, long version, Instant createdAt, Instant updatedAt,
                           Instant deactivatedAt, Integer deactivatedBy) {
        public boolean active() {
            return "ACTIVE".equals(status);
        }
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static StoreRow map(ResultSet rs, int row) throws SQLException {
        return new StoreRow(rs.getInt("store_id"), rs.getString("name"), rs.getString("slug"), rs.getString("status"),
                rs.getString("address"), rs.getString("phone"), rs.getString("icon_key"), rs.getString("banner_key"),
                rs.getLong("version"), instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "deactivated_at"),
                rs.getObject("deactivated_by", Integer.class));
    }

    private static final String COLUMNS = "store_id,name,slug,status,address,phone,icon_key,banner_key,version,created_at,"
            + "updated_at,deactivated_at,deactivated_by";

    /** {@code status} null = every status; {@code q} matches name or slug, ignoring case. Ordered by name, then id. */
    public List<StoreRow> findPage(String q, String status, int page, int size) {
        var where = where(q, status);
        var args = new ArrayList<>(where.args());
        args.add(size);
        args.add((long) page * size);
        return jdbc.query("SELECT " + COLUMNS + " FROM store" + where.sql() + " ORDER BY name,store_id LIMIT ? OFFSET ?",
                StoreRepository::map, args.toArray());
    }

    public long count(String q, String status) {
        var where = where(q, status);
        return jdbc.queryForObject("SELECT COUNT(*) FROM store" + where.sql(), Long.class, where.args().toArray());
    }

    private record Where(String sql, List<Object> args) {
    }

    private static Where where(String q, String status) {
        var clauses = new ArrayList<String>();
        var args = new ArrayList<Object>();
        if (status != null) {
            clauses.add("status=?");
            args.add(status);
        }
        if (q != null) {
            clauses.add("(LOWER(name) LIKE ? OR slug LIKE ?)");
            String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(like);
            args.add(like);
        }
        return new Where(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses), args);
    }

    public Optional<StoreRow> find(int storeId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM store WHERE store_id=?", StoreRepository::map, storeId).stream().findFirst();
    }

    public Optional<StoreRow> lock(int storeId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM store WHERE store_id=? FOR UPDATE", StoreRepository::map, storeId)
                .stream().findFirst();
    }

    /**
     * Locks every Store row and returns id + name. Name uniqueness (trim, ignoring case) is a service rule without a database
     * constraint, so creates and renames are serialised on the (small) store table.
     */
    public List<StoreRow> lockAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM store ORDER BY store_id FOR UPDATE", StoreRepository::map);
    }

    public boolean slugTaken(String slug, int excludeStoreId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM store WHERE slug=? AND store_id<>?", Integer.class, slug, excludeStoreId) > 0;
    }

    public int insert(String name, String slug, String address, String phone) {
        var key = new GeneratedKeyHolder();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO store(name,slug,status,address,phone,version,created_at,updated_at) VALUES(?,?,'ACTIVE',?,?,1,?,?)",
                    new String[]{"store_id"});
            ps.setString(1, name);
            ps.setString(2, slug);
            ps.setString(3, address);
            ps.setString(4, phone);
            ps.setTimestamp(5, now);
            ps.setTimestamp(6, now);
            return ps;
        }, key);
        return Objects.requireNonNull(key.getKey()).intValue();
    }

    public int update(int storeId, long expectedVersion, String name, String slug, String address, String phone) {
        return jdbc.update("UPDATE store SET name=?,slug=?,address=?,phone=?,version=version+1,updated_at=? WHERE store_id=? AND version=?",
                name, slug, address, phone, Timestamp.from(Instant.now()), storeId, expectedVersion);
    }

    public int updateContact(int storeId, long expectedVersion, String address, String phone) {
        return jdbc.update("UPDATE store SET address=?,phone=?,version=version+1,updated_at=? WHERE store_id=? AND version=?",
                address, phone, Timestamp.from(Instant.now()), storeId, expectedVersion);
    }

    /** INACTIVE sets {@code deactivated_at}/{@code deactivated_by}; ACTIVE clears them (DEC-20261123-01 §1). */
    public int updateStatus(int storeId, long expectedVersion, String status, int actorId) {
        Timestamp now = Timestamp.from(Instant.now());
        boolean inactive = "INACTIVE".equals(status);
        return jdbc.update("UPDATE store SET status=?,deactivated_at=?,deactivated_by=?,version=version+1,updated_at=? WHERE store_id=? AND version=?",
                status, inactive ? now : null, inactive ? actorId : null, now, storeId, expectedVersion);
    }

    public void updateImageKey(int storeId, StoreImageKind kind, String key) {
        jdbc.update("UPDATE store SET " + kind.column() + "=?,version=version+1,updated_at=? WHERE store_id=?",
                key, Timestamp.from(Instant.now()), storeId);
    }

    public List<AssignedUser> assignedUsers(int storeId) {
        return jdbc.query("SELECT u.user_id,u.name,u.email,r.code,u.status FROM app_user u JOIN role r ON r.role_id=u.role_id "
                        + "WHERE u.store_id=? ORDER BY u.user_id",
                (rs, n) -> new AssignedUser(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5)), storeId);
    }
}
