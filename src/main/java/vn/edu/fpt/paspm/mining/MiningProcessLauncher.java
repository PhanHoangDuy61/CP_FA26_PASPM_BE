package vn.edu.fpt.paspm.mining;

import java.util.ArrayList;
import java.util.List;

/** Applies a kernel file-size ceiling before starting SPMF on supported POSIX deployment/development systems. */
final class MiningProcessLauncher {
    private MiningProcessLauncher() {}

    static List<String> command(String os,List<String> command,long parentPid,long maxFileBytes) {
        if(command.isEmpty()||maxFileBytes<1||parentPid<1)throw new IllegalArgumentException("Invalid mining process limits");
        boolean mac=os.toLowerCase(java.util.Locale.ROOT).startsWith("mac");
        boolean linux=os.equalsIgnoreCase("Linux");
        if(!mac&&!linux)return List.copyOf(command);
        long unitBytes=mac?1024:512; // Darwin's shell uses KiB; Linux ulimit -f uses 512-byte blocks.
        if(maxFileBytes%unitBytes!=0)throw new IllegalArgumentException("File-size cap must align to the platform ulimit unit");
        long blocks=maxFileBytes/unitBytes;
        List<String> supervised=LinuxParentDeathLauncher.command(os,command,parentPid);
        var wrapped=new ArrayList<String>(supervised.size()+4);
        wrapped.add("/bin/sh");wrapped.add("-c");
        wrapped.add("ulimit -f "+blocks+" || exit 125; exec \"$@\"");
        wrapped.add("paspm-mining");wrapped.addAll(supervised);
        return List.copyOf(wrapped);
    }
}
