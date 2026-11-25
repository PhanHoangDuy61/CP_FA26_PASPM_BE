package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * DEC-20261125-03: SPMF 2.67a is redistributed under GPL v3 (Release asset and Docker image). The repository keeps the license,
 * the source note and the notices, and the image copies them next to the jar. Every record must carry the same pinned SHA-256.
 */
class SpmfThirdPartyNoticesTest {
    private static final String LICENSE_SHA256 = "8ceb4b9ee5adedde47b31e975c1d90c73ad27b6b165a1dcd80c7c545eb65b903";
    private static final Path LICENSE = Path.of("third_party/spmf/LICENSE-GPL-3.0.txt");
    private static final Path SOURCE = Path.of("third_party/spmf/SOURCE.md");
    private static final Path NOTICES = Path.of("THIRD_PARTY_NOTICES.md");

    private static String pinnedJarSha256() throws IOException {
        var matcher = Pattern.compile("APPROVED_SHA256 = \"([0-9a-f]{64})\"").matcher(Files.readString(Path.of(".github/scripts/provision-spmf.py")));
        assertTrue(matcher.find(), "provision-spmf.py keeps the pin");
        return matcher.group(1);
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    @Test void theGplLicenseIsShippedUnchanged() throws Exception {
        assertTrue(Files.isRegularFile(LICENSE), "third_party/spmf/LICENSE-GPL-3.0.txt");
        assertEquals(LICENSE_SHA256, sha256(LICENSE), "the license is the copy that ships inside SPMF 2.67a");
        assertTrue(Files.readString(LICENSE).contains("GNU GENERAL PUBLIC LICENSE") && Files.readString(LICENSE).contains("Version 3, 29 June 2007"));
    }

    @Test void noticesAndSourceNameTheVersionLicenseSeparateProcessAndTheSamePin() throws IOException {
        String pin = pinnedJarSha256();
        for (Path file : new Path[] {NOTICES, SOURCE}) {
            assertTrue(Files.isRegularFile(file), file.toString());
            String text = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(text.contains("2.67a"), file + " names the version");
            assertTrue(text.contains("GPL"), file + " names the license");
            assertTrue(text.contains(pin), file + " carries the pinned jar SHA-256");
            assertTrue(text.toLowerCase(java.util.Locale.ROOT).contains("separate process"), file + " states the process boundary");
        }
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("spmf-2.67a-src.zip") && source.contains("philippe-fournier-viger.com/spmf"), "official site and the hosted source zip");
        assertTrue(Files.readString(NOTICES).contains("unmodified"));
    }

    @Test void theDockerImageCopiesTheLicenseNextToTheJarAndLabelsIt() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertTrue(dockerfile.contains("COPY third_party/spmf/ /opt/paspm/licenses/spmf/"));
        assertTrue(dockerfile.contains("COPY THIRD_PARTY_NOTICES.md /opt/paspm/licenses/THIRD_PARTY_NOTICES.md"));
        assertTrue(dockerfile.contains("LABEL "), "image labels");
        assertTrue(dockerfile.contains("paspm.third-party.spmf.version=\"2.67a\""));
        assertTrue(dockerfile.contains("paspm.third-party.spmf.license=\"GPL-3.0\""));
        assertTrue(dockerfile.contains("paspm.third-party.spmf.source=\"https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/releases/tag/spmf-2.67a\""));
        assertTrue(dockerfile.contains(pinnedJarSha256()), "the Dockerfile still verifies the same jar pin");
        String dockerignore = Files.readString(Path.of(".dockerignore"));
        assertFalse(dockerignore.lines().anyMatch(l -> l.startsWith("third_party") || l.startsWith("THIRD_PARTY") || l.equals("*") || l.equals("**")),
                "the license files must reach the build context");
    }

    @Test void theImageBuildScriptDefaultsToAmd64AndVerifiesTheArchitecture() throws IOException {
        String script = Files.readString(Path.of("scripts/staging/build-image.sh"));
        assertTrue(script.contains("PLATFORM=\"${PLATFORM:-linux/amd64}\""), "amd64 is the default deployment platform");
        assertTrue(script.contains("buildx build"), "buildx");
        assertTrue(script.contains("--load"));
        assertTrue(script.contains("image inspect") && script.contains("{{.Architecture}}"), "the architecture is checked after the build");
        assertTrue(script.contains(pinnedJarSha256()), "the script keeps the SHA-256 pin");
    }
}
