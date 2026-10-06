package io.quizforge.desktop.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.*;
import java.nio.file.*;
import java.util.*;
import static io.quizforge.desktop.extension.WindowsSandboxNative.*;

/** Trusted pipe supervisor. The untrusted JVM is the only process admitted to its kill-on-close job. */
public final class WindowsSandboxLauncher {
    public record Configuration(long parent, String profile, String directory, String runtime, List<String> command, Map<String,String> environment) {}
    public static void main(String[] args) {
        int result = 125;
        try { result = launch(new ObjectMapper().readValue(Path.of(args[0]).toFile(), Configuration.class)); }
        catch (Throwable failure) { System.out.println("QF-SANDBOX-FAILED: " + failure); System.out.flush(); }
        System.exit(result);
    }
    private static int launch(Configuration configuration) throws Exception {
        PointerByReference packageSid = new PointerByReference(); WinNT.HANDLE job = null, parent = null;
        WinBase.PROCESS_INFORMATION child = new WinBase.PROCESS_INFORMATION(); boolean started = false;
        Memory attributeList = null;
        status(UserEnv.API.CreateAppContainerProfile(configuration.profile(), "QuizForge extension", "Isolated extension worker", null, 0, packageSid), "Create AppContainer");
        try (Capability capability = new Capability(); IsolatedDesktop desktop = new IsolatedDesktop(configuration.profile(), packageSid.getValue(), capability.sid())) {
            // The host protects the shared runtime once, before publishing it. Reapplying an
            // inherited ACL recursively on every launch races readers in other running workers.
            protectDirectory(Path.of(configuration.directory()), packageSid.getValue(), true);
            lowIntegrity(Path.of(configuration.directory()));
            parent = Kernel32.INSTANCE.OpenProcess(0x100000, false, (int)configuration.parent());
            if (parent == null) throw error("Open supervisor parent");
            job = Processes.API.CreateJobObjectW(null, null); if (job == null) throw error("Create sandbox job");
            JobLimits limits = new JobLimits(); limits.basic.flags = 0x2000 | 0x8; limits.basic.activeProcesses = 1; limits.write();
            check(Processes.API.SetInformationJobObject(job, 9, limits.getPointer(), limits.size()), "Set sandbox job limits");
            Memory uiRestrictions = new Memory(4); uiRestrictions.setInt(0, 0xde);
            check(Processes.API.SetInformationJobObject(job, 4, uiRestrictions, 4), "Restrict clipboard and desktop operations");

            // No network capabilities. The single custom capability grants only staged runtime reads.
            SidAttributes runtimeSid = new SidAttributes(); runtimeSid.sid = capability.sid(); runtimeSid.attributes = 4; runtimeSid.write();
            Capabilities capabilities = new Capabilities(); capabilities.sid = packageSid.getValue(); capabilities.capabilities = runtimeSid.getPointer(); capabilities.count = 1; capabilities.write();
            Memory size = new Memory(Native.POINTER_SIZE); size.clear(); Processes.API.InitializeProcThreadAttributeList(null, 4, 0, size);
            long length = Native.POINTER_SIZE == 8 ? size.getLong(0) : Integer.toUnsignedLong(size.getInt(0));
            attributeList = new Memory(length); check(Processes.API.InitializeProcThreadAttributeList(attributeList, 4, 0, size), "Initialize sandbox attributes");
            check(Processes.API.UpdateProcThreadAttribute(attributeList, 0, new BaseTSD.ULONG_PTR(0x20009), capabilities.getPointer(), new BaseTSD.SIZE_T(capabilities.size()), null, null), "Set AppContainer capabilities");
            Memory childPolicy = new Memory(4); childPolicy.setInt(0, 1);
            check(Processes.API.UpdateProcThreadAttribute(attributeList, 0, new BaseTSD.ULONG_PTR(0x2000e), childPolicy, new BaseTSD.SIZE_T(4), null, null), "Deny sandbox child processes");
            Memory jobs = new Memory(Native.POINTER_SIZE); jobs.setPointer(0, job.getPointer());
            check(Processes.API.UpdateProcThreadAttribute(attributeList, 0, new BaseTSD.ULONG_PTR(0x2000d), jobs, new BaseTSD.SIZE_T(jobs.size()), null, null), "Assign sandbox job at creation");
            Startup startup = new Startup(); startup.attributes = attributeList;
            startup.startup.lpDesktop = desktop.name;
            startup.startup.cb = new WinDef.DWORD(startup.size()); startup.startup.dwFlags = 0x100;
            startup.startup.hStdInput = Kernel32.INSTANCE.GetStdHandle(-10); startup.startup.hStdOutput = Kernel32.INSTANCE.GetStdHandle(-11); startup.startup.hStdError = Kernel32.INSTANCE.GetStdHandle(-12);
            Memory handles = new Memory(3L * Native.POINTER_SIZE);
            WinNT.HANDLE[] standard = {startup.startup.hStdInput, startup.startup.hStdOutput, startup.startup.hStdError};
            for (int i = 0; i < standard.length; i++) {
                check(Kernel32.INSTANCE.SetHandleInformation(standard[i], 1, 1), "Allow private pipe inheritance"); handles.setPointer((long)i * Native.POINTER_SIZE, standard[i].getPointer());
            }
            check(Processes.API.UpdateProcThreadAttribute(attributeList, 0, new BaseTSD.ULONG_PTR(0x20002), handles, new BaseTSD.SIZE_T(handles.size()), null, null), "Restrict inherited handles");
            startup.write();
            String environment = configuration.environment().entrySet().stream().sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)).map(entry -> entry.getKey() + "=" + entry.getValue() + '\0').reduce("", String::concat) + '\0';
            Memory environmentBlock = new Memory((environment.length() + 1L) * Native.WCHAR_SIZE); environmentBlock.setWideString(0, environment);
            char[] command = Native.toCharArray(commandLine(configuration.command()));
            try {
                check(Processes.API.CreateProcessW(configuration.command().getFirst(), command, null, null, true, 0x08000000 | 0x80000 | 0x400 | 0x4, environmentBlock, configuration.directory(), startup.getPointer(), child), "Create isolated JVM"); started = true;
            } finally {
                java.lang.ref.Reference.reachabilityFence(List.of(capabilities, runtimeSid, childPolicy, jobs, handles, environmentBlock, startup));
            }
            System.out.println("QF-SANDBOX-READY"); System.out.flush();
            if (Processes.API.ResumeThread(child.hThread) == -1) throw error("Resume isolated JVM");
            while (Kernel32.INSTANCE.WaitForSingleObject(child.hProcess, 200) == 258) {
                if (Kernel32.INSTANCE.WaitForSingleObject(parent, 0) != 258) { Processes.API.TerminateJobObject(job, 125); break; }
            }
            IntByReference exit = new IntByReference(); check(Kernel32.INSTANCE.GetExitCodeProcess(child.hProcess, exit), "Read worker exit"); return exit.getValue();
        } finally {
            if (started) { Kernel32.INSTANCE.TerminateProcess(child.hProcess, 125); Kernel32.INSTANCE.CloseHandle(child.hThread); Kernel32.INSTANCE.CloseHandle(child.hProcess); }
            if (job != null) Kernel32.INSTANCE.CloseHandle(job);
            if (parent != null) Kernel32.INSTANCE.CloseHandle(parent);
            if (attributeList != null) { Processes.API.DeleteProcThreadAttributeList(attributeList); attributeList.close(); }
            if (packageSid.getValue() != null) Security.API.FreeSid(packageSid.getValue());
            UserEnv.API.DeleteAppContainerProfile(configuration.profile());
        }
    }
}
