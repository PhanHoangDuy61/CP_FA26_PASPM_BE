package vn.edu.fpt.paspm.persistence;

import java.net.URI;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Opt-in clone only: never falls back to application.yml or a source database. */
final class PilotCloneTarget {
    static Properties validate(Properties p, String allowed) {
        if (!"true".equals(allowed) || !"true".equals(p.getProperty("disposable")))
            throw new IllegalStateException("Explicit disposable mutation switch and marker required");
        String marker = p.getProperty("marker", "");
        if (!marker.matches("[a-f0-9]{12}")) throw new IllegalStateException("Invalid disposable marker");
        String schema = "paspm_jpa_pilot_" + marker;
        URI uri = URI.create(p.getProperty("url", "").replaceFirst("^jdbc:", ""));
        if (!"mysql".equals(uri.getScheme()) || !Set.of("localhost", "127.0.0.1").contains(uri.getHost())
                || !uri.getPath().equals("/" + schema) || !schema.equals(p.getProperty("schema"))
                || schema.equalsIgnoreCase(p.getProperty("sourceSchema", ""))
                || !p.getProperty("username", "").equals("jpa_pilot_" + marker))
            throw new IllegalStateException("Refusing non-isolated database target");
        return p;
    }

    static Properties load() {
        var p = new Properties();
        Path path = Path.of(System.getProperty("paspm.test.jpa-clone-config", "/tmp/paspm-jpa-pilot/clone.properties"));
        try (var input = Files.newInputStream(path)) { p.load(input); }
        catch (Exception e) { throw new IllegalStateException("Explicit private clone configuration unavailable"); }
        validate(p, System.getenv("PASPM_JPA_PILOT_MUTATIONS_ALLOWED"));
        try (var c = DriverManager.getConnection(p.getProperty("url"), p.getProperty("username"), p.getProperty("password"));
                var s = c.createStatement()) {
            try (var r = s.executeQuery("SELECT DATABASE(),CURRENT_USER()")) {
                r.next();
                if (!p.getProperty("schema").equals(r.getString(1))
                        || !r.getString(2).startsWith(p.getProperty("username") + "@"))
                    throw new IllegalStateException("Database identity differs from disposable marker");
            }
            try (var r = s.executeQuery("SHOW GRANTS")) {
                while (r.next()) {
                    String grant = r.getString(1).replace("`", "").replace("\\_", "_");
                    if (grant.startsWith("GRANT USAGE ON *.* ")) continue;
                    if (!grant.contains(" ON " + p.getProperty("schema") + ".* "))
                        throw new IllegalStateException("Clone credential has privileges beyond disposable schema");
                }
            }
        } catch (SQLException e) { throw new IllegalStateException("Disposable target verification failed; SQL state=" + e.getSQLState()); }
        return p;
    }

    static void register(DynamicPropertyRegistry registry) {
        Properties p = load();
        registry.add("spring.datasource.url", () -> p.getProperty("url"));
        registry.add("spring.datasource.username", () -> p.getProperty("username"));
        registry.add("spring.datasource.password", () -> p.getProperty("password"));
    }
}
