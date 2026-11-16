package vn.edu.fpt.paspm.mining;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MiningProcessOutputLimitTest {
    private static final long CAP_BYTES=4L*1024*1024;

    @Test void nonPosixLauncherLeavesCommandUnwrappedAndCanStartChildJvm() throws Exception {
        List<String> childCommand=List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-version");
        List<String> command=MiningProcessLauncher.command("Windows 11",childCommand,ProcessHandle.current().pid(),CAP_BYTES);
        assertEquals(childCommand,command,"non-POSIX systems retain direct ProcessBuilder argv and use the existing polling guard");

        Path output=Files.createTempFile("paspm-non-posix-launch-",".log");
        try {
            Process child=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
            assertTrue(child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS),"fallback child JVM must start and terminate");
            assertEquals(0,child.exitValue());
            assertTrue(Files.size(output)>0,"child JVM should emit its version output");
        } finally { Files.deleteIfExists(output); }
    }

    @Test void childFileWritesCannotOvershootTheFourMiBCapAsProducerVolumeGrows() throws Exception {
        String os=System.getProperty("os.name");
        Assumptions.assumeTrue(os.startsWith("Mac")||os.equalsIgnoreCase("Linux"),"Kernel file-size enforcement is POSIX-only");
        for(long requested:List.of(CAP_BYTES+64*1024,CAP_BYTES+16*1024*1024)) {
            Path output=Files.createTempFile("paspm-output-limit-",".bin");
            try {
                String writer="import pathlib,sys; p=pathlib.Path(sys.argv[1]); n=int(sys.argv[2]); f=p.open('wb'); "
                    + "\ntry: f.write(b'x'*n); f.close(); sys.exit(0)\nexcept OSError: f.close(); sys.exit(73)";
                var command=MiningProcessLauncher.command(os,List.of("python3","-c",writer,output.toString(),Long.toString(requested)),ProcessHandle.current().pid(),CAP_BYTES);
                var child=new ProcessBuilder(command).start();
                assertTrue(child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS),"bounded file writer must terminate");
                long actual=Files.size(output);
                assertEquals(CAP_BYTES,actual,"the kernel cap must apply even when the producer attempts a larger write");
                assertEquals(0,actual-CAP_BYTES,"bytes beyond the configured output cap");
                System.out.println("F05_OUTPUT_CAP requestedBytes="+requested+" writtenBytes="+actual+" overshootBytes="+(actual-CAP_BYTES));
            } finally { Files.deleteIfExists(output); }
        }
    }
}
