package io.quizforge.desktop.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Prepares an immutable runtime copy; question data is sent only through private pipes. */
final class WindowsExtensionSandbox {
    private static final Object LOCK = new Object();
    private static final ExecutorService CLEANUP = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "qf-sandbox-cleanup"); thread.setDaemon(true); return thread; });
    private static final String CAPTURED_CLASSPATH = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    private static final String CAPTURED_MODULEPATH = System.getProperty("jdk.module.path", "");
    private static boolean recovered;
    private static boolean securedRoot;
    private static RuntimeFiles preparedRuntime;
    private record RuntimeFiles(Path root, Path java, String classpath, String modulepath) {}
    private WindowsExtensionSandbox() {}
    static Process start(Class<?> entry, String... arguments) throws IOException {
        long beginning = System.nanoTime();
        RuntimeFiles runtime = prepareRuntime();
        Path runs = root().resolve("runs"); safeDirectories(runs);
        synchronized (LOCK) { if (!recovered) { recover(runs); recovered = true; } }
        Path directory = Files.createDirectory(runs.resolve(UUID.randomUUID().toString()));
        Files.writeString(runs.resolve(directory.getFileName() + ".owner"), Long.toString(ProcessHandle.current().pid()), StandardOpenOption.CREATE_NEW);
        String profile = "QuizForge.Extension." + directory.getFileName();
        try {
            Path temporary = Files.createDirectory(directory.resolve("tmp")), home = Files.createDirectory(directory.resolve("home"));
            var command = new ArrayList<String>(); command.add(runtime.java.toString());
            command.addAll(List.of("-Xmx128m", "-XX:MaxDirectMemorySize=32m", "-Dfile.encoding=UTF-8", "-Dprism.order=sw", "-Dquizforge.worker.jobSupervised=true", "-Djava.io.tmpdir=" + temporary, "-Duser.home=" + home,
                    "-Djava.library.path=" + runtime.root.resolve("natives"), "-Djna.boot.library.path=" + runtime.root.resolve("natives"), "-Djna.nounpack=true"));
            if (!runtime.modulepath.isBlank()) command.addAll(List.of("--module-path", runtime.modulepath, "--add-modules", entry == ExtensionPageWorker.class ? "javafx.web,javafx.swing,java.desktop" : "javafx.web"));
            command.addAll(List.of("-cp", runtime.classpath, entry.getName(), Long.toString(ProcessHandle.current().pid()))); command.addAll(List.of(arguments));
            Map<String,String> environment = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (String name : List.of("SYSTEMROOT", "WINDIR")) { String value = System.getenv(name); if (value != null) environment.put(name, value); }
            environment.put("PATH", runtime.java.getParent().toString()); environment.put("TEMP", temporary.toString()); environment.put("TMP", temporary.toString());
            for (String name : List.of("USERPROFILE", "APPDATA", "LOCALAPPDATA")) environment.put(name, home.toString());
            var configuration = new WindowsSandboxLauncher.Configuration(ProcessHandle.current().pid(), profile, directory.toString(), runtime.root.toString(), command, environment);
            Path configurationFile = directory.resolve("launch.json"); new ObjectMapper().writeValue(configurationFile.toFile(), configuration);
            ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(), "-Xmx48m", "-Dfile.encoding=UTF-8", "-cp", CAPTURED_CLASSPATH, WindowsSandboxLauncher.class.getName(), configurationFile.toString());
            ExtensionWorkerProcess.cleanEnvironment(builder);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process supervisor = builder.start();
            if (Boolean.getBoolean("quizforge.sandbox.timings")) System.err.println("Sandbox preparation ms=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beginning));
            supervisor.onExit().thenRun(() -> CLEANUP.execute(() -> cleanup(directory, profile)));
            return new SupervisedProcess(supervisor, beginning);
        } catch (Throwable failure) {
            cleanup(directory, profile);
            if (failure instanceof IOException io) throw io;
            throw new IOException("Windows extension sandbox unavailable", failure);
        }
    }
    private static final class SupervisedProcess extends Process {
        private final Process delegate; private final InputStream input;
        SupervisedProcess(Process delegate, long beginning) {
            this.delegate = delegate;
            input = new FilterInputStream(delegate.getInputStream()) {
                private boolean ready;
                private void ensureReady() throws IOException {
                    if (ready) return;
                    var bytes = new ByteArrayOutputStream(); int value;
                    while ((value = in.read()) != -1 && value != '\n' && bytes.size() < 2048) bytes.write(value);
                    String status = bytes.toString(StandardCharsets.UTF_8).strip();
                    if (!status.equals("QF-SANDBOX-READY")) throw new IOException("Windows sandbox unavailable: " + status);
                    ready = true;
                    if (Boolean.getBoolean("quizforge.sandbox.timings")) System.err.println("Sandbox isolated-process handshake ms=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - beginning));
                }
                @Override public int read() throws IOException { ensureReady(); return in.read(); }
                @Override public int read(byte[] buffer, int offset, int length) throws IOException { ensureReady(); return in.read(buffer, offset, length); }
            };
        }
        @Override public OutputStream getOutputStream() { return delegate.getOutputStream(); }
        @Override public InputStream getInputStream() { return input; }
        @Override public InputStream getErrorStream() { return delegate.getErrorStream(); }
        @Override public int waitFor() throws InterruptedException { return delegate.waitFor(); }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException { return delegate.waitFor(timeout, unit); }
        @Override public int exitValue() { return delegate.exitValue(); }
        @Override public void destroy() { delegate.destroyForcibly(); }
        @Override public Process destroyForcibly() { delegate.destroyForcibly(); return this; }
        @Override public boolean isAlive() { return delegate.isAlive(); }
        @Override public long pid() { return delegate.pid(); }
        @Override public ProcessHandle toHandle() { return delegate.toHandle(); }
        @Override public CompletableFuture<Process> onExit() { return delegate.onExit().thenApply(ignored -> this); }
    }
    private static Path root() throws IOException {
        String local = System.getenv("LOCALAPPDATA"); if (local == null || local.isBlank()) throw new IOException("Sandbox cache location unavailable");
        return Path.of(local).toAbsolutePath().normalize().resolve("QuizForge/extension-sandbox-v1");
    }
    private static RuntimeFiles prepareRuntime() throws IOException {
        synchronized (LOCK) {
            // One immutable runtime snapshot per host lifetime. Packages still get
            // separate restricted processes/jobs; restarting the app picks up new binaries.
            if(preparedRuntime!=null){rejectLink(preparedRuntime.root);return preparedRuntime;}
            if (!securedRoot) {
                Path cacheRoot = root(); safeDirectories(cacheRoot);
                WindowsSandboxNative.protectDirectory(cacheRoot, null, false); securedRoot = true;
            }
            Path javaHome = Path.of(System.getProperty("java.home")).toAbsolutePath().normalize();
            List<Path> classpath = paths(CAPTURED_CLASSPATH), modulepath = paths(CAPTURED_MODULEPATH);
            List<Path> inputs = new ArrayList<>();
            for (String name : List.of("bin", "lib", "conf")) { Path path = javaHome.resolve(name); if (Files.exists(path)) inputs.add(path); }
            inputs.addAll(classpath); inputs.addAll(modulepath);
            MessageDigest digest; try { digest = MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
            digest.update("native-layout-v3-worker-resources".getBytes(StandardCharsets.UTF_8));
            // Metadata fingerprint selects the snapshot; workers cannot write to it or to its inputs.
            for (Path path : inputs) fingerprint(path, digest);
            Path runtimes = root().resolve("runtimes"); safeDirectories(runtimes);
            Path snapshot = runtimes.resolve(HexFormat.of().formatHex(digest.digest()));
            if (!Files.exists(snapshot)) {
                Path building = Files.createDirectory(runtimes.resolve("building-" + UUID.randomUUID()));
                try {
                    for (String name : List.of("bin", "lib", "conf")) if (Files.exists(javaHome.resolve(name))) copy(javaHome.resolve(name), building.resolve("jre/" + name));
                    for (int i = 0; i < classpath.size(); i++) copy(classpath.get(i), building.resolve("cp/" + i + (Files.isDirectory(classpath.get(i)) ? "" : ".jar")), true);
                    for (int i = 0; i < modulepath.size(); i++) copy(modulepath.get(i), building.resolve("mp/" + i + (Files.isDirectory(modulepath.get(i)) ? "" : ".jar")), true);
                    for (Path input : inputs) if (Files.isRegularFile(input) && input.getFileName().toString().endsWith(".jar")) nativeLibraries(input, building.resolve("natives"));
                    try (var capability = new WindowsSandboxNative.Capability()) { WindowsSandboxNative.protectDirectory(building, capability.sid(), false); }
                    try { Files.move(building, snapshot, StandardCopyOption.ATOMIC_MOVE); }
                    catch (FileAlreadyExistsException competingHost) { deleteTree(building); }
                } catch (Throwable failure) { deleteTree(building); throw failure; }
            }
            rejectLink(snapshot);
            String cp = remap(snapshot, "cp", classpath), mp = remap(snapshot, "mp", modulepath);
            preparedRuntime=new RuntimeFiles(snapshot, snapshot.resolve("jre/bin/javaw.exe"), cp, mp);
            return preparedRuntime;
        }
    }
    private static void nativeLibraries(Path jar, Path directory) throws IOException {
        Files.createDirectories(directory);
        String architecture = switch (System.getProperty("os.arch")) { case "amd64", "x86_64" -> "x86-64"; case "aarch64" -> "aarch64"; default -> "x86"; };
        try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement(); String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".dll")) continue;
                if (name.contains("/") && !name.equals("com/sun/jna/win32-" + architecture + "/jnidispatch.dll")) continue;
                Path target = directory.resolve(Path.of(name).getFileName());
                try (var bytes = zip.getInputStream(entry)) {
                    if (Files.exists(target)) {
                        if (!Arrays.equals(Files.readAllBytes(target), bytes.readAllBytes())) throw new IOException("Conflicting native runtime libraries");
                    } else Files.copy(bytes, target);
                }
            }
        }
    }
    private static List<Path> paths(String path) throws IOException {
        var result = new ArrayList<Path>();
        for (String part : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (part.isBlank()) continue;
            if (part.endsWith("*")) {
                Path directory = Path.of(part.substring(0, part.length() - 1)).toAbsolutePath().normalize(); rejectLink(directory);
                try (var files = Files.list(directory)) { result.addAll(files.filter(file -> file.getFileName().toString().endsWith(".jar")).sorted().toList()); }
            } else { Path source = Path.of(part).toAbsolutePath().normalize(); if (Files.exists(source)) result.add(source); }
        }
        return result;
    }
    private static String remap(Path snapshot, String kind, List<Path> originals) {
        var paths = new ArrayList<String>(); for (int i = 0; i < originals.size(); i++) paths.add(snapshot.resolve(kind + "/" + i + (Files.isDirectory(originals.get(i)) ? "" : ".jar")).toString()); return String.join(File.pathSeparator, paths);
    }
    private static void fingerprint(Path source, MessageDigest digest) throws IOException {
        rejectLink(source);
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                rejectLink(path); var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                digest.update((path + ":" + attributes.size() + ":" + attributes.lastModifiedTime().toMillis() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
    }
    private static void copy(Path source, Path destination) throws IOException {
        copy(source, destination, false);
    }
    private static boolean workerResource(Path relative) {
        String name = relative.toString().replace('\\', '/');
        return name.endsWith(".class") || name.endsWith(".dll") || relative.getNameCount() == 1 && name.endsWith(".jar") || name.startsWith("META-INF/services/") || name.equals("editor/draft-canvas/extension-rules-runtime.js");
    }
    private static void copy(Path source, Path destination, boolean restrictResources) throws IOException {
        boolean restrictDirectory = restrictResources && Files.isDirectory(source);
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException { rejectLink(directory); Files.createDirectories(destination.resolve(source.relativize(directory))); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Path relative = source.relativize(file); if (restrictDirectory && !workerResource(relative)) return FileVisitResult.CONTINUE;
                rejectLink(file); Path target = destination.resolve(relative); Files.createDirectories(target.getParent()); Files.copy(file, target); return FileVisitResult.CONTINUE;
            }
        });
    }
    private static void safeDirectories(Path directory) throws IOException {
        for (Path path = directory; path != null; path = path.getParent()) if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) rejectLink(path);
        Files.createDirectories(directory); for (Path path = directory; path != null; path = path.getParent()) rejectLink(path);
    }
    private static void rejectLink(Path path) throws IOException {
        var attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther()) throw new IOException("Sandbox paths must not contain reparse points");
    }
    private static void cleanup(Path directory, String profile) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                WindowsSandboxNative.UserEnv.API.DeleteAppContainerProfile(profile);
                deleteTree(directory); Files.deleteIfExists(directory.resolveSibling(directory.getFileName() + ".owner")); return;
            } catch (Throwable failure) { try { Thread.sleep(250); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; } }
        }
    }
    private static void recover(Path runs) throws IOException {
        // Owner markers are outside the worker's writable directory. Never trust child launch.json
        // to choose a cleanup path/profile, and never delete another live host's files.
        try (var files = Files.list(runs)) {
            for (Path marker : files.filter(path -> path.getFileName().toString().endsWith(".owner")).limit(100).toList()) {
                try {
                    rejectLink(marker); if (Files.size(marker) > 32) continue;
                    String name = marker.getFileName().toString().replace(".owner", ""); UUID.fromString(name);
                    long parent = Long.parseLong(Files.readString(marker));
                    if (ProcessHandle.of(parent).map(ProcessHandle::isAlive).orElse(false)) continue;
                    Path directory = runs.resolve(name).toAbsolutePath().normalize();
                    if (!directory.getParent().equals(runs.toAbsolutePath().normalize())) continue;
                    CLEANUP.execute(() -> cleanup(directory, "QuizForge.Extension." + name));
                } catch (IllegalArgumentException | IOException invalidMarker) { /* Preserve anything not recognized as our own abandoned run. */ }
            }
        }
    }
    private static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
        // Called only with a directory created above; never follow junctions during cleanup.
        rejectLink(directory);
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path folder, IOException failure) throws IOException { if (failure != null) throw failure; Files.delete(folder); return FileVisitResult.CONTINUE; }
        });
    }
}
