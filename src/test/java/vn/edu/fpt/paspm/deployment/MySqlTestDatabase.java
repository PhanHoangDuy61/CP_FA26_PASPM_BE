package vn.edu.fpt.paspm.deployment;

import java.sql.DriverManager;
import java.util.UUID;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Opt-in tests create new local schemas only; no existing schema is reused or deleted. */
public record MySqlTestDatabase(String url, String user, String password) {
    public static MySqlTestDatabase create() {
        String server = System.getenv().getOrDefault("PASPM_TEST_MYSQL_SERVER", "jdbc:mysql://127.0.0.1:3306/");
        if (!server.matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/"))
            throw new IllegalArgumentException("MySQL verification requires an empty local server URL");
        String user = System.getenv().getOrDefault("PASPM_TEST_MYSQL_USER", "root");
        String password = System.getenv().getOrDefault("PASPM_TEST_MYSQL_PASSWORD", "");
        String schema = "paspm_p0_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(server, user, password); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + schema);
            System.out.println("P0_MYSQL_TEST schema=" + schema + " serverVersion=" + connection.getMetaData().getDatabaseProductVersion());
        } catch (java.sql.SQLException e) { throw new IllegalStateException("Cannot create isolated local MySQL test schema", e); }
        return new MySqlTestDatabase(server + schema + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true", user, password);
    }
    public void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", this::url);
        registry.add("spring.datasource.username", this::user);
        registry.add("spring.datasource.password", this::password);
    }
}
