package vn.edu.fpt.paspm.mining;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Test-only Linux fixture: parent spawns a real supervised child, then waits. */
public class LinuxParentDeathProbe {
    public static void main(String[] args)throws Exception {
        boolean missing=args.length>1 && args[1].equals("missing-python");
        long parent=args.length>1 && !missing ? ProcessHandle.current().pid()+1000000 : ProcessHandle.current().pid();
        Process p;
        try{p=new ProcessBuilder(LinuxParentDeathLauncher.command("Linux",List.of("/usr/bin/sleep","120"),parent)).start();}
        catch(java.io.IOException e){if(missing){System.out.println("MISSING_PYTHON_FAILS_CLOSED");return;}throw e;}
        if(missing){p.destroyForcibly();throw new IllegalStateException("Missing Python must not launch unprotected child");}
        if(args.length>1){if(p.waitFor()!=125)throw new IllegalStateException("Parent mismatch must refuse execution");System.out.println("PARENT_IDENTITY_MISMATCH_REFUSED");return;}
        Files.writeString(Path.of(args[0]),Long.toString(p.pid()));
        p.waitFor();
    }
}
