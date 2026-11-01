package vn.edu.fpt.paspm.deployment;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;

/** DB-free guard: Flyway versions must be unique and F05 mining migrations must sit after F04's V12. */
class MigrationVersioningTest {
    private static final Path SQL = Path.of("src/main/resources/db/migration");
    private static final Path JAVA = Path.of("src/main/java/db/migration");
    private static final Pattern NAME = Pattern.compile("V(\\d+)__(.+)\\.(sql|java)");

    private static Map<Integer, List<String>> versions() throws IOException {
        Map<Integer, List<String>> found = new TreeMap<>();
        for (Path dir : List.of(SQL, JAVA)) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    Matcher m = NAME.matcher(p.getFileName().toString());
                    if (m.matches()) found.computeIfAbsent(Integer.parseInt(m.group(1)), k -> new ArrayList<>()).add(m.group(2));
                }
            }
        }
        return found;
    }

    @Test void f05MiningMigrationsOccupyV13ToV17InOrder() throws IOException {
        Map<Integer, List<String>> v = versions();
        assertEquals(List.of("mining_administration_config"), v.get(13));
        assertEquals(List.of("scheduled_mining"), v.get(14));
        assertEquals(List.of("freeze_scheduled_occurrence_context"), v.get(15));
        assertEquals(List.of("mining_request_leases"), v.get(16));
        assertEquals(List.of("mining_occurrence_recovery"), v.get(17));
    }

    @Test void noFlywayVersionIsUsedTwice() throws IOException {
        versions().forEach((version, names) -> assertEquals(1, names.size(), "Flyway version " + version + " is used by " + names));
    }

    @Test void f04V12IsNotTakenByMining() throws IOException {
        List<String> v12 = versions().getOrDefault(12, List.of());
        assertTrue(v12.isEmpty() || v12.equals(List.of("f04_preserve_large_raw_import_rows")), "V12 belongs to F04 only, found " + v12);
    }
}
