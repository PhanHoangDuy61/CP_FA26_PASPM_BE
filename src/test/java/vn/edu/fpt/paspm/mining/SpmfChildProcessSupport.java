package vn.edu.fpt.paspm.mining;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Test helper shared by the multi-JVM mining tests: finds the real SPMF (EFIM) java process of a Backend.
 *
 * <p>On Linux the pid in {@code SPMF_PROCESS_STARTED} first belongs to the Python parent-death launcher
 * ({@code python3 -I -c ...}, whose argv also contains {@code EFIM}) and only becomes java after {@code execv}.
 * Stopping it before its {@code prctl(PR_SET_PDEATHSIG)} call exercises the documented startup race, not the
 * safety guarantee, and leaves a stopped launcher behind when the Backend dies. Commit 24ab3e5 fixed this once
 * ({@code HUI-Miner && !"-c"}); commit cd90e58 (EFIM-only) dropped the {@code "-c"} guard in both tests. Keep the
 * selection in this one place so the two tests cannot drift apart again.
 */
final class SpmfChildProcessSupport {
    private SpmfChildProcessSupport() {}
    private static final Pattern STARTED = Pattern.compile("SPMF_PROCESS_STARTED [^\\n]*pid=([0-9]+)");

    /**
     * True only for the SPMF EFIM java process: not the Python launcher, not a shell. The launcher's argv carries the whole
     * child command ({@code -jar}, {@code EFIM}) after its own {@code -I -c <script>}, so {@code -c} is the discriminator.
     * {@code info().command()} is deliberately not used: under Rosetta/QEMU {@code /proc/<pid>/exe} is the emulator.
     */
    static boolean isSpmfJava(ProcessHandle candidate) {
        return candidate.info().arguments().map(args -> {
            var list = Arrays.asList(args);
            return list.contains("EFIM") && list.contains("-jar") && !list.contains("-c");
        }).orElse(false);
    }

    /** Waits until the Backend's logged SPMF pid is a direct child of {@code backend} that has exec'd into java. */
    static ProcessHandle await(Process backend, Path backendLog, Duration timeout) throws Exception {
        long end = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < end) {
            var match = STARTED.matcher(Files.readString(backendLog));
            long pid = -1;
            while (match.find()) pid = Long.parseLong(match.group(1));
            if (pid > 0) {
                var child = ProcessHandle.of(pid);
                if (child.isPresent() && child.get().parent().map(p -> p.pid() == backend.pid()).orElse(false) && isSpmfJava(child.get())) return child.get();
            }
            Thread.sleep(1);
        }
        throw new AssertionError("actual SPMF java child not observed in the process log and ancestry (after exec of the launcher)");
    }
}
