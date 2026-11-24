package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * DEC-20261125-01: SPMF is GPL v3 and PASPM runs it only as a separate process ({@code java -jar spmf.jar run EFIM ...}).
 * The backend must never import an SPMF class, depend on SPMF in Maven, or bundle it. The package name is built from two
 * parts so that this file does not match its own scan.
 */
class SpmfProcessBoundaryTest {
    private static final String SPMF_PACKAGE = "ca." + "pfv";

    /** Java sources under {@code root} that mention the SPMF package. */
    static List<String> sourceViolations(Path root) throws IOException {
        List<String> found = new ArrayList<>();
        if (!Files.isDirectory(root)) return found;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                if (Files.readString(file).contains(SPMF_PACKAGE)) found.add(root.relativize(file).toString());
            }
        }
        return found;
    }

    /** True when the Maven descriptor declares SPMF as a dependency or plugin artifact. */
    static boolean pomMentionsSpmf(String pom) {
        String lower = pom.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("<groupid>" + SPMF_PACKAGE) || lower.contains("<artifactid>spmf") || lower.contains("spmf.jar");
    }

    @Test void backendAndTestSourcesNeverImportSpmfClasses() throws IOException {
        assertEquals(List.of(), sourceViolations(Path.of("src/main")));
        assertEquals(List.of(), sourceViolations(Path.of("src/test")));
    }

    @Test void mavenDoesNotDependOnOrBundleSpmf() throws IOException {
        assertFalse(pomMentionsSpmf(Files.readString(Path.of("pom.xml"))), "SPMF must stay an external process, never a Maven dependency");
        assertFalse(Files.exists(Path.of("build.gradle")) || Files.exists(Path.of("build.gradle.kts")), "no second build system");
    }

    @Test void theScannerDetectsAnImportAndAPomDependency(@TempDir Path dir) throws IOException {
        Path source = Files.createDirectories(dir.resolve("vn/example"));
        Files.writeString(source.resolve("Bad.java"), "package vn.example;\nimport " + SPMF_PACKAGE + ".spmf.algorithms.frequentpatterns.efim.AlgoEFIM;\nclass Bad {}\n");
        Files.writeString(source.resolve("Good.java"), "package vn.example;\nclass Good { String cmd = \"java -jar spmf.jar run EFIM\"; }\n");
        assertEquals(List.of("vn/example/Bad.java".replace('/', java.io.File.separatorChar)), sourceViolations(dir));
        assertTrue(pomMentionsSpmf("<dependency><groupId>" + SPMF_PACKAGE + "</groupId><artifactId>spmf</artifactId></dependency>"));
        assertTrue(pomMentionsSpmf("<artifactId>SPMF-core</artifactId>"));
        assertFalse(pomMentionsSpmf("<dependency><groupId>org.apache.poi</groupId><artifactId>poi-ooxml</artifactId></dependency>"));
    }

    @Test void theBackendLaunchesSpmfAsAChildProcess() throws IOException {
        String engine = Files.readString(Path.of("src/main/java/vn/edu/fpt/paspm/mining/SpmfMiningEngine.java"));
        assertTrue(engine.contains("ProcessBuilder") && engine.contains("\"-jar\""), "SPMF is started with java -jar in a child process");
    }
}
