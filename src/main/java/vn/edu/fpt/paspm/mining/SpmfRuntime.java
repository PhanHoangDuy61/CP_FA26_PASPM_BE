package vn.edu.fpt.paspm.mining;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import vn.edu.fpt.paspm.common.ApiException;

/** Validates the trusted external dependency without making F05 a startup requirement. */
final class SpmfRuntime {
    private static final long PROBE_TIMEOUT_MS=5000, CACHE_NANOS=TimeUnit.SECONDS.toNanos(30);
    private static final int LOG_LIMIT=65536, MINIMUM_JAVA=25;
    private final Path jar,java;
    private final int heapMiB;
    private String verifiedFingerprint;
    private long verifiedAt;

    SpmfRuntime(String jar,String java,int heapMiB) {
        this.jar=path(jar);this.java=path(java);this.heapMiB=heapMiB;
    }
    private static Path path(String value) {
        try { return value==null||value.isBlank()?null:Path.of(value); }
        catch(InvalidPathException e) { return null; }
    }
    Path jar() { return jar; }
    Path javaExecutable() { return java; }

    synchronized void requireAvailable() {
        if(jar==null)throw unavailable("SPMF_JAR_NOT_CONFIGURED","Set PASPM_MINING_SPMF_JAR to the approved absolute JAR path");
        if(!jar.isAbsolute()||!Files.isRegularFile(jar))throw unavailable("SPMF_JAR_NOT_FOUND","Configure an existing absolute SPMF JAR file");
        if(!Files.isReadable(jar))throw unavailable("SPMF_JAR_NOT_READABLE","Grant the backend read access to the approved SPMF JAR");
        String jarHash;
        try { jarHash=hash(jar); }
        catch(IOException e) { throw unavailable("SPMF_JAR_NOT_READABLE","The SPMF JAR could not be read"); }
        if(!SpmfMiningEngine.JAR_HASH.equals(jarHash))
            throw unavailable("SPMF_JAR_HASH_MISMATCH","SPMF must match the approved 2.67a SHA-256; verify provisioning");
        if(java==null||!java.isAbsolute()||!Files.isRegularFile(java)||!Files.isExecutable(java))
            throw unavailable("SPMF_JAVA_NOT_FOUND","Set PASPM_MINING_JAVA_EXECUTABLE to a separate executable Java 25+ binary");
        final String fingerprint;
        try { fingerprint=jar.toRealPath()+"|"+jarHash+"|"+java.toRealPath()+"|"+hash(java); }
        catch(IOException e) { throw unavailable("SPMF_RUNTIME_UNAVAILABLE","The configured Java executable could not be read"); }
        // Rehash both files on every admission/execution; metadata-only caches miss same-size/timestamp changes.
        if(fingerprint.equals(verifiedFingerprint)&&System.nanoTime()-verifiedAt<CACHE_NANOS)return;
        Path directory=null;
        try {
            directory=Files.createTempDirectory("paspm-spmf-preflight-");
            Files.writeString(directory.resolve("write-check"),"ready",StandardCharsets.UTF_8);
            String version=probe(List.of(java.toString(),"-version"),directory.resolve("java.log"));
            var match=Pattern.compile("version\\s+\"(?:1\\.)?([0-9]+)").matcher(version);
            if(!match.find())throw unavailable("SPMF_RUNTIME_UNAVAILABLE","The configured executable did not report a Java version");
            if(Integer.parseInt(match.group(1))<MINIMUM_JAVA)
                throw unavailable("SPMF_JAVA_VERSION_UNSUPPORTED","The approved SPMF JAR requires a Java 25+ child runtime; the backend may remain on Java 17");
            String banner=probe(List.of(java.toString(),"-Xmx"+heapMiB+"m","-Djava.awt.headless=true","-jar",jar.toString(),"version"),directory.resolve("spmf.log"));
            if(!banner.contains("-- SPMF version 2.67a --"))
                throw unavailable("SPMF_RUNTIME_UNAVAILABLE","The approved SPMF entrypoint did not report version 2.67a");
        } catch(IOException|NumberFormatException e) {
            throw unavailable("SPMF_RUNTIME_UNAVAILABLE","Check Java/SPMF provisioning and backend temporary-directory permissions");
        } finally {
            if(directory!=null) {
                try(var files=Files.walk(directory)) {
                    for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);
                } catch(IOException e) {
                    throw unavailable("SPMF_RUNTIME_UNAVAILABLE","The backend could not clean its SPMF preflight directory");
                }
            }
        }
        verifiedFingerprint=fingerprint;verifiedAt=System.nanoTime();
    }

    private String probe(List<String> command,Path log) throws IOException {
        Process process=null;boolean interrupted=false;
        try {
            process=new ProcessBuilder(command).directory(log.getParent().toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(PROBE_TIMEOUT_MS);
            while(process.isAlive()) {
                if(System.nanoTime()>=deadline||Files.size(log)>LOG_LIMIT)
                    throw unavailable("SPMF_RUNTIME_UNAVAILABLE","Runtime preflight exceeded its time/output limit");
                process.waitFor(20,TimeUnit.MILLISECONDS);
            }
            if(process.exitValue()!=0||Files.size(log)>LOG_LIMIT)
                throw unavailable("SPMF_RUNTIME_UNAVAILABLE","Java/SPMF preflight failed; verify the executable and supported JAR");
            return Files.readString(log,StandardCharsets.UTF_8);
        } catch(InterruptedException e) {
            interrupted=true;
            throw unavailable("SPMF_RUNTIME_UNAVAILABLE","SPMF runtime preflight was interrupted; retry later");
        } finally {
            interrupted|=Thread.interrupted();
            if(process!=null&&process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(5,TimeUnit.SECONDS); } catch(InterruptedException e) { interrupted=true; }
            }
            if(interrupted)Thread.currentThread().interrupt();
        }
    }
    static String hash(Path file) throws IOException {
        try(var input=Files.newInputStream(file)) {
            var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ApiException unavailable(String code,String message) { return new ApiException(503,code+": "+message); }
}
