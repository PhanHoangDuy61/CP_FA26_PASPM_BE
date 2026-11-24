package db.migration;

import java.sql.Connection;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * DEC-20261123-01 (Store management, F01): Store profile, status, optimistic version, audit of the last deactivation and
 * branding-image keys.
 * <ul>
 * <li>{@code slug} is backfilled for every existing row as {@code slugify(name)-{store_id}} <b>before</b> it becomes NOT NULL
 * and UNIQUE. The id suffix is unique, so duplicate Store names (allowed until now) cannot collide.</li>
 * <li>{@code slug} gets a random-UUID default so raw SQL inserts that omit it (seed scripts, tests) still produce a valid,
 * unique slug; the API always sets it explicitly.</li>
 * <li>{@code name}, {@code utility_config} and {@code mining_schedule} are not touched. No UNIQUE on {@code name}: name
 * uniqueness (trim, case-insensitive) is a service rule.</li>
 * </ul>
 * Written in Java because the backfill needs slug generation and the column DDL differs between MySQL 8.4 and H2.
 */
public final class V27__f01_store_profile_and_status extends BaseJavaMigration {
    static final int SLUG_MAX = 50;

    @Override
    public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        boolean mysql = c.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
        try (var st = c.createStatement()) {
            st.execute("ALTER TABLE store ADD COLUMN slug VARCHAR(50) NULL");
            st.execute("ALTER TABLE store ADD COLUMN status VARCHAR(10) DEFAULT 'ACTIVE' NOT NULL");
            st.execute("ALTER TABLE store ADD COLUMN address VARCHAR(255) NULL");
            st.execute("ALTER TABLE store ADD COLUMN phone VARCHAR(30) NULL");
            st.execute("ALTER TABLE store ADD COLUMN icon_key VARCHAR(512) NULL");
            st.execute("ALTER TABLE store ADD COLUMN banner_key VARCHAR(512) NULL");
            st.execute("ALTER TABLE store ADD COLUMN version BIGINT DEFAULT 1 NOT NULL");
            st.execute("ALTER TABLE store ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL");
            st.execute("ALTER TABLE store ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL");
            st.execute("ALTER TABLE store ADD COLUMN deactivated_at TIMESTAMP NULL");
            st.execute("ALTER TABLE store ADD COLUMN deactivated_by INT NULL");
        }

        Map<Integer, String> names = new LinkedHashMap<>();
        try (var st = c.createStatement(); var rows = st.executeQuery("SELECT store_id,name FROM store ORDER BY store_id")) {
            while (rows.next()) {
                names.put(rows.getInt(1), rows.getString(2));
            }
        }
        try (var update = c.prepareStatement("UPDATE store SET slug=? WHERE store_id=?")) {
            for (var entry : names.entrySet()) {
                update.setString(1, slug(entry.getValue(), entry.getKey()));
                update.setInt(2, entry.getKey());
                update.addBatch();
            }
            update.executeBatch();
        }

        try (var st = c.createStatement()) {
            if (mysql) {
                st.execute("ALTER TABLE store MODIFY slug VARCHAR(50) NOT NULL DEFAULT (uuid())");
            } else {
                st.execute("ALTER TABLE store ALTER COLUMN slug SET DEFAULT LOWER(CAST(RANDOM_UUID() AS VARCHAR(36)))");
                st.execute("ALTER TABLE store ALTER COLUMN slug SET NOT NULL");
            }
            st.execute("ALTER TABLE store ADD CONSTRAINT uk_store_slug UNIQUE (slug)");
            st.execute("ALTER TABLE store ADD CONSTRAINT ck_store_status CHECK (status IN ('ACTIVE','INACTIVE'))");
            st.execute("ALTER TABLE store ADD CONSTRAINT fk_store_deactivated_by FOREIGN KEY (deactivated_by) REFERENCES app_user(user_id)");
        }
    }

    /** {@code slugify(name)-{id}}, at most 50 characters, lowercase letters, digits and single hyphens. */
    static String slug(String name, int storeId) {
        String suffix = "-" + storeId;
        String base = slugify(name);
        int room = SLUG_MAX - suffix.length();
        if (base.length() > room) {
            base = base.substring(0, room).replaceAll("-+$", "");
        }
        if (base.isEmpty()) {
            base = "store";
        }
        return base + suffix;
    }

    static String slugify(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replace('đ', 'd').replace('Đ', 'D');
        text = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }
}
