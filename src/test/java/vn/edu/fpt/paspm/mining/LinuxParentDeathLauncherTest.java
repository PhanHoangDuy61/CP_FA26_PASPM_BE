package vn.edu.fpt.paspm.mining;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinuxParentDeathLauncherTest {
    @Test void linuxUsesArgumentVectorAndPreservesExecutableAndArguments() {
        var original=List.of("/runtime path/java","-jar","/jar path/spmf.jar","run","HUI-Miner");
        var wrapped=LinuxParentDeathLauncher.command("Linux",original,1234);
        assertEquals("python3",wrapped.get(0));
        assertEquals("-I",wrapped.get(1));
        assertEquals("-c",wrapped.get(2));
        assertEquals("1234",wrapped.get(4));
        assertEquals(original,wrapped.subList(5,wrapped.size()));
        assertTrue(wrapped.get(3).contains("prctl"));
        assertTrue(wrapped.get(3).contains("getppid"));
        assertTrue(wrapped.get(3).contains("execv"));
    }
    @Test void developerPlatformsKeepExistingInvocationWithoutPretendingLinuxProtection() {
        var original=List.of("C:\\Program Files\\Java\\java.exe","-version");
        assertEquals(original,LinuxParentDeathLauncher.command("Windows 11",original,42));
        assertEquals(original,LinuxParentDeathLauncher.command("Mac OS X",original,42));
    }
}
