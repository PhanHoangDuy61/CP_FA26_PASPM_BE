package vn.edu.fpt.paspm.catalog;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

/** Idempotent sample catalog for the dev profile only. */
@Component
@Profile("dev")
public class CatalogDevSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final ZoneId storeTimeZone;

    public CatalogDevSeeder(JdbcTemplate jdbc, UserRepository users, PasswordEncoder passwords,
                            @Value("${paspm.store-time-zone:Asia/Ho_Chi_Minh}") String storeTimeZone) {
        this.jdbc = jdbc; this.users = users; this.passwords = passwords;
        this.storeTimeZone = ZoneId.of(storeTimeZone);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int storeA = store("F02 Demo Store A");
        int storeB = store("F02 Demo Store B");
        ensureUser(storeA, "storeadmin@dev.paspm.local", "F02 Demo Store Admin", "STORE_ADMIN");
        ensureUser(storeB, "storeadmin-b@dev.paspm.local", "F02 Demo Store B Admin", "STORE_ADMIN");
        ensureUser(storeA, "merchandiser@dev.paspm.local", "F02 Demo Merchandiser", "MERCHANDISER");
        int dairy = category(storeA, "F02 Dairy");
        int pantryA = category(storeA, "F02 Pantry");
        int drinksB = category(storeB, "F02 Drinks");
        int milk = product(storeA, dairy, "F02-MILK-01", "F02 Demo Milk", true);
        int coffee = product(storeA, pantryA, "F02-COFFEE-01", "F02 Demo Coffee", false);
        int tea = product(storeB, drinksB, "F02-TEA-01", "F02 Demo Tea", true);

        batch(milk, "F02-MILK-SOON", LocalDate.now(storeTimeZone).plusDays(3), 12, "ACTIVE");
        batch(milk, "F02-MILK-EXPIRED", LocalDate.now(storeTimeZone).minusDays(1), 4, "ACTIVE");
        batch(milk, "F02-MILK-BLOCKED", LocalDate.now(storeTimeZone).plusDays(20), 5, "BLOCKED");
        batch(coffee, "F02-COFFEE-NO-EXPIRY", null, 8, "ACTIVE");
        batch(tea, "F02-TEA-OTHER-STORE", LocalDate.now(storeTimeZone).plusDays(20), 7, "ACTIVE");
    }

    private void ensureUser(int storeId, String email, String name, String role) {
        if (users.findByEmail(email).isPresent()) return;
        User user = users.createPendingUser(name, email, role, storeId,
                passwords.encode("PaspmDev123!"), null);
        jdbc.update("UPDATE app_user SET status='ACTIVE', email_verified_at=? WHERE user_id=?",
                Timestamp.from(Instant.now()), user.id());
    }

    private int store(String name) {
        Integer id = jdbc.query("SELECT store_id FROM store WHERE name=? ORDER BY store_id LIMIT 1",
                rs -> rs.next() ? rs.getInt(1) : null, name);
        if (id != null) return id;
        jdbc.update("INSERT INTO store(name) VALUES (?)", name);
        return jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
    }

    private int category(int storeId, String name) {
        Integer id = jdbc.query("SELECT category_id FROM category WHERE store_id=? AND name=? ORDER BY category_id LIMIT 1",
                rs -> rs.next() ? rs.getInt(1) : null, storeId, name);
        if (id != null) return id;
        jdbc.update("INSERT INTO category(store_id, name, status) VALUES (?, ?, 'ACTIVE')", storeId, name);
        return jdbc.queryForObject("SELECT MAX(category_id) FROM category WHERE store_id=?", Integer.class, storeId);
    }

    private int product(int storeId, int categoryId, String sku, String name, boolean requiresExpiry) {
        Integer id = jdbc.query("SELECT product_id FROM product WHERE store_id=? AND sku=?",
                rs -> rs.next() ? rs.getInt(1) : null, storeId, sku);
        if (id != null) return id;
        jdbc.update("""
                INSERT INTO product(store_id, category_id, sku, name, unit, requires_expiry,
                                    selling_price, cost_price, status)
                VALUES (?, ?, ?, ?, 'each', ?, ?, ?, 'ACTIVE')
                """, storeId, categoryId, sku, name, requiresExpiry,
                new BigDecimal("1.00"), new BigDecimal("0.50"));
        return jdbc.queryForObject("SELECT product_id FROM product WHERE store_id=? AND sku=?", Integer.class, storeId, sku);
    }

    private void batch(int productId, String code, LocalDate expiry, int quantity, String status) {
        Integer exists = jdbc.query("SELECT batch_id FROM inventory_batch WHERE product_id=? AND batch_code=?",
                rs -> rs.next() ? rs.getInt(1) : null, productId, code);
        if (exists != null) return;
        jdbc.update("""
                INSERT INTO inventory_batch(product_id, batch_code, received_at, expiry_date,
                    initial_quantity, remaining_quantity, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, productId, code, Timestamp.from(Instant.now()), expiry, quantity, quantity, status,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
    }
}
