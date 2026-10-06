package io.quizforge.desktop.extension;

import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.*;
import com.sun.jna.win32.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Native declarations are used only by the trusted launcher, never exported through the SDK. */
final class WindowsSandboxNative {
    private WindowsSandboxNative() {}
    interface UserEnv extends StdCallLibrary {
        UserEnv API = Native.load("userenv", UserEnv.class, W32APIOptions.UNICODE_OPTIONS);
        int CreateAppContainerProfile(String name, String display, String description, Pointer capabilities, int count, PointerByReference sid);
        int DeleteAppContainerProfile(String name);
    }
    interface Base extends StdCallLibrary {
        Base API = Native.load("kernelbase", Base.class, W32APIOptions.UNICODE_OPTIONS);
        boolean DeriveCapabilitySidsFromName(String name, PointerByReference groups, IntByReference groupCount, PointerByReference sids, IntByReference count);
    }
    interface Security extends StdCallLibrary {
        Security API = Native.load("advapi32", Security.class, W32APIOptions.UNICODE_OPTIONS);
        Pointer FreeSid(Pointer sid);
        boolean GetTokenInformation(WinNT.HANDLE token, int informationClass, Pointer value, int length, IntByReference returned);
        boolean ConvertStringSecurityDescriptorToSecurityDescriptorW(String sddl, int revision, PointerByReference descriptor, IntByReference size);
        boolean GetSecurityDescriptorSacl(Pointer descriptor, IntByReference present, PointerByReference acl, IntByReference defaulted);
        boolean GetSecurityDescriptorDacl(Pointer descriptor, IntByReference present, PointerByReference acl, IntByReference defaulted);
    }
    interface Desktop extends StdCallLibrary {
        Desktop API = Native.load("user32", Desktop.class, W32APIOptions.UNICODE_OPTIONS);
        WinNT.HANDLE GetProcessWindowStation();
        boolean SetProcessWindowStation(WinNT.HANDLE station);
        WinNT.HANDLE CreateWindowStationW(String name, int flags, int access, WinBase.SECURITY_ATTRIBUTES security);
        WinNT.HANDLE CreateDesktopW(String name, Pointer device, Pointer mode, int flags, int access, WinBase.SECURITY_ATTRIBUTES security);
        boolean CloseWindowStation(WinNT.HANDLE station);
        boolean CloseDesktop(WinNT.HANDLE desktop);
        boolean GetUserObjectInformationW(WinNT.HANDLE handle, int index, Pointer value, int length, IntByReference returned);
        WinNT.HANDLE OpenInputDesktop(int flags, boolean inherit, int access);
    }
    interface Processes extends StdCallLibrary {
        Processes API = Native.load("kernel32", Processes.class, W32APIOptions.UNICODE_OPTIONS);
        boolean InitializeProcThreadAttributeList(Pointer list, int count, int flags, Pointer size);
        boolean UpdateProcThreadAttribute(Pointer list, int flags, BaseTSD.ULONG_PTR attribute, Pointer value, BaseTSD.SIZE_T size, Pointer previous, Pointer returned);
        void DeleteProcThreadAttributeList(Pointer list);
        boolean CreateProcessW(String application, char[] command, Pointer processAttributes, Pointer threadAttributes, boolean inherit, int flags, Pointer environment, String directory, Pointer startup, WinBase.PROCESS_INFORMATION process);
        WinNT.HANDLE CreateJobObjectW(Pointer attributes, String name);
        boolean SetInformationJobObject(WinNT.HANDLE job, int informationClass, Pointer information, int size);
        boolean TerminateJobObject(WinNT.HANDLE job, int code);
        int ResumeThread(WinNT.HANDLE thread);
    }
    @Structure.FieldOrder({"sid", "attributes"})
    public static class SidAttributes extends Structure {
        public Pointer sid; public int attributes;
    }
    @Structure.FieldOrder({"sid", "capabilities", "count", "reserved"})
    public static class Capabilities extends Structure {
        public Pointer sid, capabilities; public int count, reserved;
    }
    @Structure.FieldOrder({"startup", "attributes"})
    public static class Startup extends Structure {
        public WinBase.STARTUPINFO startup = new WinBase.STARTUPINFO(); public Pointer attributes;
    }
    @Structure.FieldOrder({"processTime", "jobTime", "flags", "minimum", "maximum", "activeProcesses", "affinity", "priority", "scheduling"})
    public static class JobBasic extends Structure {
        public long processTime, jobTime; public int flags;
        public BaseTSD.SIZE_T minimum, maximum; public int activeProcesses;
        public BaseTSD.ULONG_PTR affinity; public int priority, scheduling;
    }
    @Structure.FieldOrder({"basic", "io", "processMemory", "jobMemory", "peakProcessMemory", "peakJobMemory"})
    public static class JobLimits extends Structure {
        public JobBasic basic = new JobBasic(); public long[] io = new long[6];
        public BaseTSD.SIZE_T processMemory, jobMemory, peakProcessMemory, peakJobMemory;
    }
    static IOException error(String action) { return new IOException(action + " (Windows error " + Kernel32.INSTANCE.GetLastError() + ")"); }
    static void check(boolean success, String action) throws IOException { if (!success) throw error(action); }
    static void status(int result, String action) throws IOException { if (result != 0) throw new IOException(action + " (Windows status " + result + ")"); }

    static void protectDirectory(Path directory, Pointer permittedSid, boolean writable) throws IOException {
        PointerByReference owner = new PointerByReference(), previous = new PointerByReference(), descriptor = new PointerByReference(), acl = new PointerByReference();
        status(Advapi32.INSTANCE.GetNamedSecurityInfo(directory.toString(), 1, 1, owner, null, null, null, previous), "Read directory owner");
        try {
            String ownerName = new WinNT.PSID(owner.getValue()).getSidString();
            String userName = currentUserSid().getSidString();
            String extra = permittedSid == null ? "" : "(A;OICI;" + (writable ? "FA" : "FRFX") + ";;;" + new WinNT.PSID(permittedSid).getSidString() + ")";
            String sddl = "D:P(A;OICI;FA;;;SY)(A;OICI;FA;;;BA)(A;OICI;FA;;;" + ownerName + ")(A;OICI;FA;;;" + userName + ")" + extra;
            check(Security.API.ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl, 1, descriptor, null), "Build protected directory ACL");
            check(Security.API.GetSecurityDescriptorDacl(descriptor.getValue(), new IntByReference(), acl, new IntByReference()), "Read protected directory ACL");
            status(Advapi32.INSTANCE.SetNamedSecurityInfo(directory.toString(), 1, 4 | 0x80000000, null, null, acl.getValue(), null), "Protect sandbox directory ACL");
        } finally {
            if (previous.getValue() != null) Kernel32.INSTANCE.LocalFree(previous.getValue());
            if (descriptor.getValue() != null) Kernel32.INSTANCE.LocalFree(descriptor.getValue());
        }
    }
    static void lowIntegrity(Path directory) throws IOException {
        PointerByReference descriptor = new PointerByReference(), acl = new PointerByReference();
        check(Security.API.ConvertStringSecurityDescriptorToSecurityDescriptorW("S:(ML;OICI;NW;;;LW)", 1, descriptor, null), "Build private temporary label");
        try {
            check(Security.API.GetSecurityDescriptorSacl(descriptor.getValue(), new IntByReference(), acl, new IntByReference()), "Read private temporary label");
            status(Advapi32.INSTANCE.SetNamedSecurityInfo(directory.toString(), 1, 0x10, null, null, null, acl.getValue()), "Label private temporary directory");
        } finally { Kernel32.INSTANCE.LocalFree(descriptor.getValue()); }
    }
    static WinNT.PSID currentUserSid() throws IOException {
        WinNT.HANDLEByReference token = new WinNT.HANDLEByReference();
        check(Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(), 8, token), "Read launcher user");
        try { return new WinNT.PSID(Advapi32Util.getTokenAccount(token.getValue()).sid); }
        finally { Kernel32.INSTANCE.CloseHandle(token.getValue()); }
    }
    static final class IsolatedDesktop implements AutoCloseable {
        final String name; private WinNT.HANDLE station, desktop;
        IsolatedDesktop(String profile, Pointer appSid, Pointer runtimeSid) throws IOException {
            String desktopName = "QF_" + profile.substring(profile.lastIndexOf('.') + 1);
            WinNT.HANDLEByReference token = new WinNT.HANDLEByReference();
            check(Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(), 8, token), "Read launcher identity");
            String owner; try { owner = Advapi32Util.getTokenAccount(token.getValue()).sidString; } finally { Kernel32.INSTANCE.CloseHandle(token.getValue()); }
            String packageName = new WinNT.PSID(appSid).getSidString();
            String capabilityName = new WinNT.PSID(runtimeSid).getSidString();
            PointerByReference descriptor = new PointerByReference(), stationDescriptor = new PointerByReference();
            check(Security.API.ConvertStringSecurityDescriptorToSecurityDescriptorW("D:(A;;GA;;;SY)(A;;GA;;;" + owner + ")(A;;GA;;;" + packageName + ")S:(ML;;NW;;;LW)", 1, descriptor, null), "Build isolated desktop ACL");
            check(Security.API.ConvertStringSecurityDescriptorToSecurityDescriptorW("D:(A;;GA;;;SY)(A;;GA;;;" + owner + ")(A;;GA;;;" + capabilityName + ")S:(ML;;NW;;;LW)", 1, stationDescriptor, null), "Build noninteractive station ACL");
            WinNT.HANDLE previous = Desktop.API.GetProcessWindowStation();
            try {
                WinBase.SECURITY_ATTRIBUTES security = new WinBase.SECURITY_ATTRIBUTES(); security.dwLength = new WinDef.DWORD(security.size()); security.lpSecurityDescriptor = descriptor.getValue();
                security.lpSecurityDescriptor = stationDescriptor.getValue();
                // Windows only permits administrators to name stations. The system-generated
                // noninteractive station is shared; each worker desktop has its own package SID ACL.
                station = Desktop.API.CreateWindowStationW(null, 0, 0x000f037f, security);
                if (station == null) throw error("Create isolated window station");
                Memory stationName = new Memory(1024);
                check(Desktop.API.GetUserObjectInformationW(station, 2, stationName, (int)stationName.size(), new IntByReference()), "Read noninteractive station name");
                name = stationName.getWideString(0) + "\\" + desktopName;
                check(Desktop.API.SetProcessWindowStation(station), "Select isolated window station");
                security.lpSecurityDescriptor = descriptor.getValue();
                desktop = Desktop.API.CreateDesktopW(desktopName, null, null, 0, 0x000f01ff, security);
                if (desktop == null) throw error("Create isolated desktop");
            } catch (IOException failure) { close(); throw failure; }
            finally { Desktop.API.SetProcessWindowStation(previous); Kernel32.INSTANCE.LocalFree(descriptor.getValue()); Kernel32.INSTANCE.LocalFree(stationDescriptor.getValue()); }
        }
        @Override public void close() { if (desktop != null) Desktop.API.CloseDesktop(desktop); if (station != null) Desktop.API.CloseWindowStation(station); }
    }
    static final class Capability implements AutoCloseable {
        private final PointerByReference groups = new PointerByReference(), sids = new PointerByReference();
        private final IntByReference groupCount = new IntByReference(), count = new IntByReference();
        Capability() throws IOException {
            check(Base.API.DeriveCapabilitySidsFromName("quizforge.extension.runtime.v1", groups, groupCount, sids, count), "Derive runtime capability");
            if (count.getValue() != 1) { close(); throw new IOException("Unexpected runtime capability SID count"); }
        }
        Pointer sid() { return sids.getValue().getPointer(0); }
        @Override public void close() { free(groups.getValue(), groupCount.getValue()); free(sids.getValue(), count.getValue()); }
        private void free(Pointer array, int length) {
            if (array == null) return;
            for (int i = 0; i < length; i++) Kernel32.INSTANCE.LocalFree(array.getPointer((long)i * Native.POINTER_SIZE));
            Kernel32.INSTANCE.LocalFree(array);
        }
    }
    static String commandLine(List<String> arguments) {
        // CommandLineToArgvW/CRT quoting, including trailing backslashes and embedded quotes.
        return arguments.stream().map(argument -> {
            StringBuilder result = new StringBuilder("\""); int slashes = 0;
            for (char character : argument.toCharArray()) {
                if (character == '\\') { slashes++; continue; }
                result.append("\\".repeat(character == '"' ? slashes * 2 + 1 : slashes)); slashes = 0; result.append(character);
            }
            return result.append("\\".repeat(slashes * 2)).append('"').toString();
        }).reduce((left, right) -> left + " " + right).orElseThrow();
    }
}
