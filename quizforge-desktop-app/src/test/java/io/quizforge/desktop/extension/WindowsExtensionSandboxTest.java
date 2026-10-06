package io.quizforge.desktop.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.*;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WindowsExtensionSandboxTest {
    @TempDir Path fixtures;
    @Test void invalidIsolationConfigurationFailsWithoutStartingAnOrdinaryWorker() throws Exception {
        Path configuration = fixtures.resolve("invalid-launch.json");
        new ObjectMapper().writeValue(configuration.toFile(), new WindowsSandboxLauncher.Configuration(ProcessHandle.current().pid(), "invalid/profile/name", fixtures.toString(), fixtures.toString(), List.of(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(), "-version"), Map.of()));
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(), "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), WindowsSandboxLauncher.class.getName(), configuration.toString()).redirectError(ProcessBuilder.Redirect.DISCARD);
        ExtensionWorkerProcess.cleanEnvironment(builder); Process process = builder.start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS)); assertEquals(125, process.exitValue());
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(output.startsWith("QF-SANDBOX-FAILED:"), output); assertFalse(output.contains("QF-SANDBOX-READY"));
        } finally { process.destroyForcibly(); }
    }
    @Test void nativeRestrictionsApplyToJvmAndTerminationKillsTheContainedProcess() throws Exception {
        Path sentinel = Files.writeString(fixtures.resolve("host-only.txt"), "host sentinel");
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Process process = ExtensionWorkerProcess.start(Probe.class, sentinel.toString(), Integer.toString(server.getLocalPort()));
            try {
                var line = CompletableFuture.supplyAsync(() -> { try { return new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)).readLine(); } catch (IOException failure) { throw new UncheckedIOException(failure); } });
                String encoded = line.get(60, TimeUnit.SECONDS);
                if (encoded == null) fail("Sandbox probe exited: " + new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
                var result = new ObjectMapper().readValue(encoded, Map.class);
                assertEquals(true, result.get("appContainer")); assertEquals(4096, result.get("integrity")); assertEquals(1, result.get("capabilityCount")); assertEquals(true, result.get("privateTempWritable"));
                for (String restriction : List.of("hostReadDenied", "hostWriteDenied", "runtimeWriteDenied", "networkDenied", "childDenied", "hostDesktopDenied")) assertEquals(true, result.get(restriction), restriction + ": " + result);
                // Windows permits creating/binding a socket; isolation is applied to traffic.
                assertThrows(IOException.class, () -> { try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), ((Number)result.get("listenerPort")).intValue()), 1500); } }, "Unprivileged network traffic cannot reach the contained listener");
                Process other = ExtensionWorkerProcess.start(Probe.class, (String)result.get("privateFile"), Integer.toString(server.getLocalPort()));
                try {
                    var second = CompletableFuture.supplyAsync(() -> { try { return new BufferedReader(new InputStreamReader(other.getInputStream(), StandardCharsets.UTF_8)).readLine(); } catch (IOException failure) { throw new UncheckedIOException(failure); } }).get(30, TimeUnit.SECONDS);
                    var isolated = new ObjectMapper().readValue(second, Map.class);
                    assertEquals(true, isolated.get("hostReadDenied"), "A second worker cannot read the first worker's temporary data");
                    assertEquals(true, isolated.get("hostWriteDenied"));
                } finally { other.destroyForcibly(); assertTrue(other.waitFor(10, TimeUnit.SECONDS)); }
                assertEquals("host sentinel", Files.readString(sentinel));
                long child = ((Number)result.get("pid")).longValue(); assertNotEquals(process.pid(), child);
                process.destroyForcibly(); assertTrue(process.waitFor(10, TimeUnit.SECONDS));
                long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false) && System.nanoTime() < end) Thread.sleep(50);
                assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false), "Closing the supervisor must kill the contained JVM");
            } finally { process.destroyForcibly(); }
        }
    }
    public static final class Probe {
        public static void main(String[] args) throws Exception {
            var result = new LinkedHashMap<String,Object>(); result.put("pid", ProcessHandle.current().pid());
            Path temporary = Path.of(System.getProperty("java.io.tmpdir"));
            try { Files.writeString(temporary.resolve("allowed.txt"), "ok"); result.put("privateTempWritable", true); }
            catch (Throwable failure) { System.out.println(new ObjectMapper().writeValueAsString(Map.of("tempFailure", failure.toString(), "fileWritable", temporary.toFile().canWrite()))); System.out.flush(); throw failure; }
            WinNT.HANDLEByReference token = new WinNT.HANDLEByReference();
            WindowsSandboxNative.check(Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(), 8, token), "Query probe token");
            try (Memory value = new Memory(4)) {
                WindowsSandboxNative.check(WindowsSandboxNative.Security.API.GetTokenInformation(token.getValue(), 29, value, 4, new IntByReference()), "Query AppContainer token"); result.put("appContainer", value.getInt(0) == 1);
                try (Memory information = new Memory(256)) {
                    WindowsSandboxNative.check(WindowsSandboxNative.Security.API.GetTokenInformation(token.getValue(), 25, information, 256, new IntByReference()), "Query integrity");
                    Pointer sid = information.getPointer(0); result.put("integrity", sid.getInt(8 + (Byte.toUnsignedInt(sid.getByte(1)) - 1) * 4L));
                    WindowsSandboxNative.check(WindowsSandboxNative.Security.API.GetTokenInformation(token.getValue(), 30, information, 256, new IntByReference()), "Query capabilities"); result.put("capabilityCount", information.getInt(0));
                }
            } finally { Kernel32.INSTANCE.CloseHandle(token.getValue()); }
            Path host = Path.of(args[1]);
            result.put("hostReadDenied", denied(() -> Files.readString(host)));
            result.put("hostWriteDenied", denied(() -> Files.writeString(host, "unauthorized")));
            Path runtime = Path.of(System.getProperty("java.home"));
            Files.readAllBytes(runtime.resolve("conf/security/java.security"));
            result.put("runtimeWriteDenied", denied(() -> Files.writeString(runtime.resolve("unauthorized.txt"), "no")));
            result.put("networkDenied", denied(() -> { try (Socket socket = new Socket()) { socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), Integer.parseInt(args[2])), 1500); } }));
            ServerSocket listener = new ServerSocket(0); result.put("listenerPort", listener.getLocalPort());
            result.put("childDenied", denied(() -> { Process child = new ProcessBuilder(runtime.resolve("bin/java.exe").toString(), "-version").start(); child.destroyForcibly(); }));
            WinNT.HANDLE desktop = WindowsSandboxNative.Desktop.API.OpenInputDesktop(0, false, 1); result.put("hostDesktopDenied", desktop == null); if (desktop != null) WindowsSandboxNative.Desktop.API.CloseDesktop(desktop);
            result.put("privateFile", temporary.resolve("allowed.txt").toString());
            System.out.println(new ObjectMapper().writeValueAsString(result)); System.out.flush(); Thread.sleep(120_000);
        }
        private interface Attempt { void run() throws Exception; }
        private static boolean denied(Attempt action) throws Exception { try { action.run(); return false; } catch (IOException | SecurityException expected) { return true; } }
    }
}
