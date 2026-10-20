package vn.edu.fpt.paspm.mining;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.fpt.paspm.common.ApiException;
import static org.junit.jupiter.api.Assertions.*;

class SpmfRuntimeTest {
    @TempDir Path temp;
    SpmfMiningEngine engine(String jar,String java) { return new SpmfMiningEngine(jar,java,10000,128,2000); }
    void error(String code,SpmfMiningEngine engine) {
        var e=assertThrows(ApiException.class,engine::requireAvailable);
        assertEquals(503,e.status());assertTrue(e.getMessage().startsWith(code+":"),e.getMessage());
        assertFalse(e.getMessage().contains(temp.toString()),"Do not expose server paths");
    }
    @Test void missingJarAndJavaHaveActionableDistinctCodes() throws Exception {
        error("SPMF_JAR_NOT_CONFIGURED",engine("",SpmfMiningEngineTest.JAVA));
        error("SPMF_JAR_NOT_FOUND",engine(temp.resolve("missing.jar").toString(),SpmfMiningEngineTest.JAVA));
        Path wrong=temp.resolve("wrong.jar");Files.writeString(wrong,"not a valid jar");
        error("SPMF_JAR_HASH_MISMATCH",engine(wrong.toString(),SpmfMiningEngineTest.JAVA));
        error("SPMF_JAVA_NOT_FOUND",engine(SpmfMiningEngineTest.JAR.toString(),temp.resolve("missing-java").toString()));
        error("SPMF_JAVA_NOT_FOUND",engine(SpmfMiningEngineTest.JAR.toString(),""));
    }
    @Test @EnabledOnOs({OS.MAC,OS.LINUX})
    void unreadableJarAndUnrunnableExecutableAreRejected() throws Exception {
        Path jar=Files.copy(SpmfMiningEngineTest.JAR,temp.resolve("unreadable.jar"));
        var permissions=Files.getPosixFilePermissions(jar);
        try {
            Files.setPosixFilePermissions(jar,Set.of());
            error("SPMF_JAR_NOT_READABLE",engine(jar.toString(),SpmfMiningEngineTest.JAVA));
        } finally { Files.setPosixFilePermissions(jar,permissions); }
        error("SPMF_RUNTIME_UNAVAILABLE",engine(SpmfMiningEngineTest.JAR.toString(),"/usr/bin/false"));
    }
    @Test @EnabledIfSystemProperty(named="paspm.test.java17",matches=".+")
    void actualJava17IsRejectedBeforeMiningAdmission() {
        error("SPMF_JAVA_VERSION_UNSUPPORTED",engine(SpmfMiningEngineTest.JAR.toString(),System.getProperty("paspm.test.java17")));
    }
    @Test void verifiedJava25AndJarPassAndPreflightFilesAreRemoved() throws Exception {
        var before=preflightDirectories();
        var runner=engine(SpmfMiningEngineTest.JAR.toString(),SpmfMiningEngineTest.JAVA);
        runner.requireAvailable();runner.requireAvailable();
        assertEquals(before,preflightDirectories());
    }
    @Test void sameSizeSameTimestampJarChangeCannotBypassCachedPreflight() throws Exception {
        Path jar=Files.copy(SpmfMiningEngineTest.JAR,temp.resolve("copy.jar"));
        var runner=engine(jar.toString(),SpmfMiningEngineTest.JAVA);runner.requireAvailable();
        var timestamp=Files.getLastModifiedTime(jar);byte[] bytes=Files.readAllBytes(jar);
        bytes[bytes.length-1]^=1;Files.write(jar,bytes);Files.setLastModifiedTime(jar,timestamp);
        error("SPMF_JAR_HASH_MISMATCH",runner);
    }
    @Test @EnabledOnOs({OS.MAC,OS.LINUX})
    void entrypointFailureAndChangedJavaAreDetectedWithCleanup() throws Exception {
        Path launcher=temp.resolve("java");
        String real=SpmfMiningEngineTest.JAVA.replace("'","'\\''");
        String good="#!/bin/sh\nexec '"+real+"' \"$@\"\n";
        Files.writeString(launcher,good);Files.setPosixFilePermissions(launcher,PosixFilePermissions.fromString("rwx------"));
        var runner=engine(SpmfMiningEngineTest.JAR.toString(),launcher.toString());runner.requireAvailable();
        var before=preflightDirectories();var timestamp=Files.getLastModifiedTime(launcher);
        Files.writeString(launcher,"#!/bin/sh\nif [ \"$1\" = \"-version\" ]; then echo 'openjdk version \"25.0.1\"'; exit 0; fi\nexit 1\n");
        Files.setLastModifiedTime(launcher,timestamp);
        error("SPMF_RUNTIME_UNAVAILABLE",runner);assertEquals(before,preflightDirectories());
    }
    @Test @EnabledOnOs({OS.MAC,OS.LINUX})
    void childExitAfterSuccessfulPreflightStillFailsWithoutSuccess() throws Exception {
        Path launcher=temp.resolve("java");
        String real=SpmfMiningEngineTest.JAVA.replace("'","'\\''");
        Files.writeString(launcher,"#!/bin/sh\nfor arg in \"$@\"; do if [ \"$arg\" = \"run\" ]; then exit 7; fi; done\nexec '"+real+"' \"$@\"\n");
        Files.setPosixFilePermissions(launcher,PosixFilePermissions.fromString("rwx------"));
        var runner=engine(SpmfMiningEngineTest.JAR.toString(),launcher.toString());runner.requireAvailable();
        var failure=assertThrows(SpmfMiningEngine.ExecutionFailure.class,()->runner.execute(MiningEngine.Algorithm.EFIM,SpmfMiningEngineTest.data(SpmfMiningEngineTest.INPUT),30));
        assertEquals("SPMF_FAILED",failure.code());
    }
    @Test @EnabledOnOs({OS.MAC,OS.LINUX})
    void boundedPreflightTerminatesHungExecutableAndRemovesFiles() throws Exception {
        Path launcher=temp.resolve("java");
        Files.writeString(launcher,"#!/bin/sh\nexec /bin/sleep 20\n");
        Files.setPosixFilePermissions(launcher,PosixFilePermissions.fromString("rwx------"));
        var before=preflightDirectories();
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(9),()->error("SPMF_RUNTIME_UNAVAILABLE",engine(SpmfMiningEngineTest.JAR.toString(),launcher.toString())));
        assertEquals(before,preflightDirectories());
    }
    Set<Path> preflightDirectories() throws Exception {
        try(var files=Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p->p.getFileName().toString().startsWith("paspm-spmf-preflight-")).collect(java.util.stream.Collectors.toSet());
        }
    }
}
