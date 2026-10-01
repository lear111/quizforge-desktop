package io.quizforge.dev;

import java.io.IOException;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Development only. No dependency, runtime download, framework plugin, or workspace watcher. */
public final class QuizForgeLiveAgent {
    private static volatile Process compiler;
    private static volatile boolean stopping;
    private final Instrumentation instrumentation;
    private final Path repository = Path.of(System.getProperty("quizforge.liveJava.repo"));
    private final Path output = Path.of(System.getProperty("quizforge.liveJava.output"));
    private final Path buildDirectory = Path.of(System.getProperty("quizforge.liveJava.buildDirectory", "target"));
    private final List<String> modules = Arrays.asList(System.getProperty("quizforge.liveJava.modules").split(","));
    private Map<Path, String> sources;
    private Map<Path, String> installed;
    private boolean restartRequired;

    private QuizForgeLiveAgent(Instrumentation instrumentation) throws Exception {
        this.instrumentation = instrumentation;
        sources = sourceHashes();
        installed = classHashes(output);
    }

    public static void premain(String ignored, Instrumentation instrumentation) throws Exception {
        if (!instrumentation.isRedefineClassesSupported()) throw new IllegalStateException("Class redefinition unavailable");
        System.setProperty("quizforge.liveJava.enabled", "true");
        var agent = new QuizForgeLiveAgent(instrumentation);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopping = true;
            Process process = compiler;
            if (process != null) {
                process.descendants().forEach(handle -> handle.destroyForcibly());
                process.destroyForcibly();
            }
        }, "live-java-stop"));
        Thread watcher = new Thread(agent::watch, "live-java-compiler");
        watcher.setDaemon(true);
        watcher.start();
        log("READY pid=" + ProcessHandle.current().pid() + " (save Java to compile; Ctrl+Alt+R refreshes the page)");
    }

    private void watch() {
        Map<Path, String> pending = null;
        long lastChange = 0;
        while (!stopping) {
            try {
                Thread.sleep(500);
                Map<Path, String> current = sourceHashes();
                if (current.equals(sources)) { pending = null; continue; }
                if (!current.equals(pending)) { pending = current; lastChange = System.nanoTime(); continue; }
                if (System.nanoTime() - lastChange < TimeUnit.MILLISECONDS.toNanos(700)) continue;
                Set<Path> changed = differences(sources, current);
                boolean removed = !current.keySet().containsAll(sources.keySet());
                sources = current;
                pending = null;
                if (restartRequired || removed || changed.stream().anyMatch(this::requiresRestart)) {
                    restartRequired = true;
                    log("RESTART_REQUIRED: bootstrap/DI/build/schema/migration change; current application retained");
                    continue;
                }
                if (changed.stream().noneMatch(path -> path.toString().endsWith(".java"))) {
                    publishResources(changed);
                    continue;
                }
                if (!compile()) continue;
                // Ignore edits made during this build until the next build succeeds.
                if (!sourceHashes().equals(sources)) { log("Sources changed during compilation; awaiting next build"); continue; }
                install();
            } catch (InterruptedException interrupted) { return; }
            catch (Throwable failure) { log("FAILED: " + failure + "; current application retained"); }
        }
    }

    private boolean compile() throws Exception {
        Path logFile = output.resolve("compile.log");
        String maven = System.getProperty("quizforge.liveJava.maven");
        String command = "\"" + maven + "\" -B -pl " + modules.getLast()
                + " -am \"-Dquizforge.build.directory=" + buildDirectory + "\" -DskipTests compile";
        ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/d", "/s", "/c", "\"" + command + "\"")
                .directory(repository.toFile()).redirectErrorStream(true).redirectOutput(logFile.toFile());
        // JVM/agent arguments apply to the desktop only, never to the compiler's JVM.
        log("COMPILING (details: " + logFile + ")");
        compiler = builder.start();
        if (!compiler.waitFor(180, TimeUnit.SECONDS)) {
            compiler.descendants().forEach(handle -> handle.destroyForcibly());
            compiler.destroyForcibly();
            log("COMPILE_FAILED: timeout; previous running classes retained");
            return false;
        }
        int result = compiler.exitValue();
        compiler = null;
        if (result != 0) { log("COMPILE_FAILED: exit " + result + "; previous running classes retained; see " + logFile); return false; }
        return true;
    }

    private void install() throws Exception {
        Map<Path, String> next = new LinkedHashMap<>();
        Map<Path, byte[]> bytes = new LinkedHashMap<>();
        for (String module : modules) {
            Path classes = repository.resolve(module).resolve(buildDirectory).resolve("classes");
            if (!Files.isDirectory(classes)) continue;
            try (var paths = Files.walk(classes)) {
                for (Path file : paths.filter(Files::isRegularFile).toList()) {
                    Path relative = Path.of(module).resolve(classes.relativize(file));
                    byte[] content = Files.readAllBytes(file);
                    next.put(relative, hash(content));
                    if (!Objects.equals(next.get(relative), installed.get(relative))) bytes.put(relative, content);
                }
            }
        }
        if (!next.keySet().containsAll(installed.keySet())) {
            restartRequired = true;
            log("RESTART_REQUIRED: removed class/resource; current classes retained");
            return;
        }
        Set<String> names = new LinkedHashSet<>();
        Map<String, byte[]> definitions = new HashMap<>();
        for (var entry : bytes.entrySet()) {
            String relative = entry.getKey().subpath(1, entry.getKey().getNameCount()).toString().replace('\\', '/');
            if (relative.endsWith(".class")) {
                String name = relative.substring(0, relative.length() - 6).replace('/', '.');
                if (name.startsWith("io.quizforge.")) { names.add(name); definitions.put(name, entry.getValue()); }
            }
        }
        List<ClassDefinition> loaded = new ArrayList<>();
        for (Class<?> type : instrumentation.getAllLoadedClasses()) {
            byte[] content = definitions.get(type.getName());
            if (content != null && type.getClassLoader() == ClassLoader.getSystemClassLoader()) {
                if (!instrumentation.isModifiableClass(type)) throw new IllegalStateException("Unmodifiable: " + type.getName());
                loaded.add(new ClassDefinition(type, content));
            }
        }
        // The VM installs all loaded definitions together or rejects the complete batch.
        try { instrumentation.redefineClasses(loaded.toArray(ClassDefinition[]::new)); }
        catch (Throwable rejected) {
            restartRequired = true;
            log("RESTART_REQUIRED: class replacement rejected: " + rejected + "; previous classes retained");
            return;
        }
        for (var entry : bytes.entrySet()) {
            Path destination = output.resolve(entry.getKey());
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), "live-", ".tmp");
            Files.write(temporary, entry.getValue());
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        installed = next;
        log("RELOADED " + loaded.size() + " loaded classes / " + bytes.size() + " changed files; pid=" + ProcessHandle.current().pid());
        if (!bytes.isEmpty()) refresh(names);
    }

    private void publishResources(Set<Path> changed) throws Exception {
        for (Path source : changed) {
            Path module = source.getName(0);
            Path prefix = module.resolve("src/main/resources");
            if (!source.startsWith(prefix)) continue;
            Path relative = module.resolve(prefix.relativize(source));
            Path destination = output.resolve(relative);
            byte[] content = Files.readAllBytes(repository.resolve(source));
            Files.createDirectories(destination.getParent());
            Path temporary = Files.createTempFile(destination.getParent(), "resource-", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            installed.put(relative, hash(content));
        }
        log("RESOURCES_UPDATED " + changed.size() + " files (no Java compilation)");
        if (System.getenv("QUIZFORGE_LIVE_CSS_DIR") != null
                && changed.stream().allMatch(path -> path.toString().endsWith(".css"))) return;
        refresh(Set.of());
    }

    private static void refresh(Set<String> names) throws Exception {
        try {
            Class<?> refresh = Class.forName("io.quizforge.desktop.dev.DevelopmentUiReloader");
            refresh.getMethod("refresh", Set.class).invoke(null, names);
        } catch (ClassNotFoundException headlessProbe) { /* Agent smoke tests have no JavaFX. */ }
    }

    private Map<Path, String> sourceHashes() throws Exception {
        Map<Path, String> result = new TreeMap<>();
        for (String module : modules) {
            for (String directory : List.of("src/main/java", "src/main/resources")) {
                Path root = repository.resolve(module).resolve(directory);
                if (!Files.isDirectory(root)) continue;
                try (var paths = Files.walk(root)) {
                    for (Path file : paths.filter(Files::isRegularFile).toList())
                        result.put(repository.relativize(file), hash(Files.readAllBytes(file)));
                }
            }
            Path pom = repository.resolve(module).resolve("pom.xml");
            if (Files.isRegularFile(pom)) result.put(repository.relativize(pom), hash(Files.readAllBytes(pom)));
        }
        Path rootPom = repository.resolve("pom.xml");
        if (Files.isRegularFile(rootPom)) result.put(Path.of("pom.xml"), hash(Files.readAllBytes(rootPom)));
        return result;
    }

    private Map<Path, String> classHashes(Path root) throws Exception {
        Map<Path, String> result = new LinkedHashMap<>();
        for (String module : modules) {
            Path directory = root.resolve(module);
            if (!Files.isDirectory(directory)) continue;
            try (var paths = Files.walk(directory)) {
                for (Path file : paths.filter(Files::isRegularFile).toList())
                    result.put(root.relativize(file), hash(Files.readAllBytes(file)));
            }
        }
        return result;
    }

    private boolean requiresRestart(Path path) {
        String value = path.toString().replace('\\', '/');
        return value.endsWith("pom.xml") || value.contains("/bootstrap/") || value.contains("/config/")
                || value.contains("/db/migration/") || value.contains("/schema/") || value.contains("/META-INF/")
                || value.endsWith("module-info.java") || value.endsWith(".properties")
                || value.endsWith(".yaml") || value.endsWith(".yml");
    }

    private static Set<Path> differences(Map<Path, String> before, Map<Path, String> after) {
        Set<Path> all = new LinkedHashSet<>(before.keySet());
        all.addAll(after.keySet());
        all.removeIf(path -> Objects.equals(before.get(path), after.get(path)));
        return all;
    }

    private static String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void log(String message) { System.out.println("[LiveJava] " + message); }
}
