package io.quizforge.desktop.browser.webview2;

import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.infrastructure.json.DocumentJson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Explicit package imports for opt-in acceptance tools only; never called by DesktopApplication. */
public final class ExternalExtensionAcceptance {
    private ExternalExtensionAcceptance() { }
    public static CompletionStage<Void> initialize(ExtensionManager manager, Path root, List<String> arguments) {
        var directory = arguments.stream().filter(value -> value.startsWith("--extensions="))
                .map(value -> Path.of(value.substring("--extensions=".length()))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Specify --extensions=<external .qfext directory>; no packages are bundled."));
        return initialize(manager, root, directory);
    }
    public static CompletionStage<Void> initialize(ExtensionManager manager, Path root, Path packages) {
        return manager.initialize(root).thenCompose(nothing -> {
            if (!manager.loaded().isEmpty() || !manager.failures().isEmpty())
                throw new IllegalStateException("Acceptance must begin with an empty extension profile");
            var receipts = new ArrayList<Map<String, Object>>();
            CompletionStage<Void> imports = CompletableFuture.completedFuture(null);
            try (var files = Files.list(packages)) {
                for (var path : files.filter(file -> file.getFileName().toString().endsWith(".qfext")).sorted().toList()) {
                    // The acceptance driver supplies explicit grants. Real users review these in the import dialog.
                    var review = manager.inspect(path);
                    var approved = new LinkedHashMap<String, Set<String>>();
                    review.manifest().types().forEach(type -> approved.put(type.id(), type.permissions()));
                    imports = imports.thenCompose(value -> manager.install(path, review.sha256(), approved).thenAccept(installed ->
                            receipts.add(Map.of("id", installed.manifest().id(), "sha256", installed.sha256(), "approved", approved))));
                }
            } catch (Exception failure) { return CompletableFuture.failedFuture(failure); }
            return imports.thenCompose(value -> {
                if (!manager.loaded().isEmpty()) throw new IllegalStateException("Import must not activate before restart");
                if (receipts.isEmpty()) throw new IllegalArgumentException("No external .qfext packages in " + packages);
                try {
                    Files.writeString(root.getParent().resolve("extension-imports.json"), DocumentJson.mapper().writerWithDefaultPrettyPrinter()
                            .writeValueAsString(Map.of("emptyStartup", true, "inactiveBeforeRestart", true, "source", packages.toAbsolutePath().toString(), "imports", receipts)));
                } catch (Exception failure) { return CompletableFuture.failedFuture(failure); }
                return manager.initialize(root).thenAccept(restarted -> {
                    if (!manager.failures().isEmpty()) throw new IllegalStateException(manager.failures().toString());
                });
            });
        });
    }
}
