package io.quizforge.desktop.extension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Private pipe workers inherit system essentials, never application settings or provider keys. */
final class ExtensionWorkerProcess {
    private ExtensionWorkerProcess() {}
    static Process start(Class<?> entry, String... arguments) throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) return WindowsExtensionSandbox.start(entry, arguments);
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString());
        command.addAll(List.of("-Xmx128m", "-XX:MaxDirectMemorySize=32m", "-Dfile.encoding=UTF-8", "-Dprism.order=sw"));
        String modulePath = System.getProperty("jdk.module.path");
        if (modulePath != null && !modulePath.isBlank()) command.addAll(List.of("--module-path", modulePath, "--add-modules", entry==ExtensionPageWorker.class?"javafx.web,javafx.swing,java.desktop":"javafx.web"));
        command.addAll(List.of("-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")), entry.getName(), Long.toString(ProcessHandle.current().pid())));
        command.addAll(List.of(arguments));
        var builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);
        cleanEnvironment(builder);
        return builder.start();
    }
    static void cleanEnvironment(ProcessBuilder builder) {
        builder.environment().keySet().removeIf(name -> !Set.of("SYSTEMROOT", "WINDIR", "TEMP", "TMP", "PATH", "USERPROFILE", "APPDATA", "LOCALAPPDATA", "DISPLAY", "WAYLAND_DISPLAY", "XDG_RUNTIME_DIR", "HOME", "LANG").contains(name.toUpperCase(Locale.ROOT)));
    }
    static void watchParent(long parent) {
        if (Boolean.getBoolean("quizforge.worker.jobSupervised")) return; // AppContainer cannot inspect the privileged host; the trusted job supervisor does it.
        Thread monitor = new Thread(() -> {
            while (ProcessHandle.of(parent).map(ProcessHandle::isAlive).orElse(false)) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { return; }
            }
            Runtime.getRuntime().halt(0);
        }, "qf-worker-parent-monitor");
        monitor.setDaemon(true); monitor.start();
    }
}
