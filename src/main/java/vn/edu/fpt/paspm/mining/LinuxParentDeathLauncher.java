package vn.edu.fpt.paspm.mining;

import java.util.ArrayList;
import java.util.List;

/** Kernel parent-death protection on Linux. No shell interprets user-provided paths. */
final class LinuxParentDeathLauncher {
    private LinuxParentDeathLauncher() {}
    private static final String LAUNCHER = """
        import ctypes, errno, os, signal, stat, sys
        try:
            expected = int(sys.argv[1])
            executable = sys.argv[2]
            info = os.stat(executable)
            if info.st_mode & (stat.S_ISUID | stat.S_ISGID):
                raise RuntimeError('privileged child executable is unsupported')
            try:
                if os.getxattr(executable, 'security.capability'):
                    raise RuntimeError('capability-bearing child executable is unsupported')
            except OSError as error:
                if error.errno not in (errno.ENODATA, errno.ENOTSUP):
                    raise
            libc = ctypes.CDLL(None, use_errno=True)
            if libc.prctl(1, signal.SIGKILL, 0, 0, 0) != 0:
                raise OSError(ctypes.get_errno(), 'PR_SET_PDEATHSIG failed')
            if os.getppid() != expected:
                os._exit(125)
            os.execv(executable, sys.argv[2:])
        except BaseException:
            print('PASPM_PROCESS_SUPERVISION_UNAVAILABLE', file=sys.stderr, flush=True)
            os._exit(125)
        """;
    static List<String> command(String os,List<String> command,long parentPid) {
        if(!"Linux".equalsIgnoreCase(os))return List.copyOf(command);
        if(command.isEmpty()||parentPid<=0)throw new IllegalArgumentException("Invalid supervised process");
        var result=new ArrayList<String>();
        result.add("python3");result.add("-I");result.add("-c");result.add(LAUNCHER);result.add(Long.toString(parentPid));result.addAll(command);
        return List.copyOf(result);
    }
}
