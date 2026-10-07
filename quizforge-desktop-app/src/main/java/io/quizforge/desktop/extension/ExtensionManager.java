package io.quizforge.desktop.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizforge.core.question.type.QuestionTypeDefinition;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition;
import io.quizforge.infrastructure.extension.ExtensionPackageStore;
import io.quizforge.infrastructure.extension.ExtensionPackageStore.InstalledExtension;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;

/** Desktop composition boundary for portable extension packages and synchronous rule adapters. */
public final class ExtensionManager implements AutoCloseable {
    private static final ExtensionManager DEFAULT = new ExtensionManager();
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, InstalledExtension> active = new LinkedHashMap<>();
    private final Map<String, ExtensionRuleRuntime> runtimes = new LinkedHashMap<>();
    private final Map<String, Map<String,Object>> scriptPackages = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();
    private final Map<Path, ExtensionLiveDevelopment> development = new LinkedHashMap<>();
    private final Map<String, Map<String,Object>> developmentPackages = new LinkedHashMap<>();
    private final Map<String, Path> developmentOwners = new LinkedHashMap<>();
    // Closed WebViews must not be retained by the global manager.
    private final Map<WebEngine, Map<String,String>> pages = new java.util.WeakHashMap<>();
    private final Set<java.util.function.Consumer<Map<String,Object>>> messagePages=new java.util.HashSet<>();
    /** Message browsers explicitly unregister when their tab is released. */
    public Runnable registerMessagePage(java.util.function.Consumer<Map<String,Object>> page){requireFx();messagePages.add(page);return ()->{requireFx();messagePages.remove(page);};}
    public String currentPackagesJson(){requireFx();var current=new LinkedHashMap<>(scriptPackages);current.putAll(developmentPackages);try{return json.writeValueAsString(current.values());}catch(IOException failure){throw new IllegalStateException(failure);}}
    private void publishMessage(Map<String,Object> message){for(var page:List.copyOf(messagePages)){try{page.accept(message);}catch(RuntimeException failure){messagePages.remove(page);try{page.accept(Map.of("kind","stop"));}catch(RuntimeException ignored){}failures.add("题型页面更新失败，已停止页面："+failure.getMessage());}}}
    private final javafx.beans.property.ReadOnlyStringWrapper developmentStatus = new javafx.beans.property.ReadOnlyStringWrapper("");
    private ExtensionPackageStore store;
    private io.quizforge.infrastructure.extension.ExtensionPermissionStore permissions;
    private long generation;
    public static ExtensionManager getDefault() { return DEFAULT; }
    public CompletionStage<Void> initialize(Path root) {
        requireFx();
        close(); failures.clear();
        store = new ExtensionPackageStore(root, Set.of());
        permissions = new io.quizforge.infrastructure.extension.ExtensionPermissionStore(root);
        var startupStore=store;long startupGeneration=generation;
        // File integrity/sample checks run off the UI thread. Register every pinned
        // version, but do not start its isolated JVM until a rule is actually used.
        return CompletableFuture.supplyAsync(()->{
            try{return startupStore.scanInstalled();}
            catch(IOException failure){throw new java.util.concurrent.CompletionException(failure);}
        }).handleAsync((scan,failure)->{
            if(startupGeneration!=generation)return null;
            if(failure!=null){failures.add(failure.getMessage());return null;}
            failures.addAll(scan.failures());
            for(var extension:scan.installed()) {
                try{activate(extension);}
                catch(IOException|RuntimeException problem){failures.add(extension.manifest().id()+": "+problem.getMessage());}
            }
            return (Void)null;
        },Platform::runLater);
    }
    public CompletionStage<InstalledExtension> install(Path qfext) {
        requireFx(); if (store == null) throw new IllegalStateException("Initialize the extension manager first");
        // Activate only on the next startup, preserving rules for every in-progress session.
        try { return CompletableFuture.completedFuture(store.install(qfext)); }
        catch (IOException failure) { return CompletableFuture.failedFuture(failure); }
    }
    public ExtensionPackageStore.PackageInspection inspect(Path qfext) throws IOException {
        requireFx();if(store==null)throw new IllegalStateException("Initialize the extension manager first");
        return store.inspect(qfext);
    }
    public CompletionStage<InstalledExtension> install(Path qfext,String reviewedHash,Map<String,Set<String>> approved) {
        requireFx();if(store==null)throw new IllegalStateException("Initialize the extension manager first");
        try {
            var installed=store.install(qfext,java.util.Objects.requireNonNull(reviewedHash));
            setPermissions(installed,approved);
            return CompletableFuture.completedFuture(installed);
        } catch(IOException|IllegalArgumentException failure) {return CompletableFuture.failedFuture(failure);}
    }
    private InstalledExtension checkedExtension(InstalledExtension candidate) throws IOException {
        requireFx();if(store==null)throw new IllegalStateException("Initialize the extension manager first");
        var installed=store.scanInstalled().installed().stream().filter(extension->key(extension).equals(key(candidate))).findFirst()
                .orElseThrow(()->new IOException("扩展已不存在或内容损坏，请重新打开扩展管理"));
        if(!installed.sha256().equals(candidate.sha256()))throw new IOException("扩展包已变化，请重新确认权限");
        return installed;
    }
    public Map<String,Set<String>> grantedPermissions(InstalledExtension extension) throws IOException {
        return permissions.grants(checkedExtension(extension));
    }
    /** Persist first, then update existing page policies without replacing a question or its draft. */
    public void setPermissions(InstalledExtension candidate,Map<String,Set<String>> approved) throws IOException {
        var installed=checkedExtension(candidate);permissions.approve(installed,approved);
        var granted=permissions.grants(installed);String packageKey=key(installed);
        for(var packages:List.of(scriptPackages,developmentPackages)) {
            var previous=packages.get(packageKey);if(previous==null)continue;
            var next=new LinkedHashMap<String,Object>(previous);next.put("grantedPermissions",granted);packages.put(packageKey,next);
        }
        String encoded=json.writeValueAsString(Map.of("id",installed.manifest().id(),"version",installed.manifest().version(),"sha256",installed.sha256(),"grantedPermissions",granted));
        publishMessage(Map.of("kind","permissions","value",json.readTree(encoded)));
        for(WebEngine engine:List.copyOf(pages.keySet())) {
            JSObject window=null;
            try {
                window=(JSObject)engine.executeScript("window");window.setMember("__quizforgePermissions",encoded);
                engine.executeScript("window.questionExtensions.updatePermissions(JSON.parse(window.__quizforgePermissions))");
            } catch(RuntimeException failure) {
                // If a live page cannot receive revocation, stop its sandbox rather than leave stale capabilities.
                try {engine.executeScript("window.questionExtensions?.denyAllPermissions?.();document.querySelectorAll('.qf-type-frame').forEach(function(frame){if(frame.dataset.qfRemotePage)window.nativePagesHost?.closePage(frame.dataset.qfRemotePage);frame.remove();})");}
                catch(RuntimeException ignored) { /* A closed WebView no longer has an active page. */ }
                failures.add("权限已保存；部分题型页面已停止，请重新打开："+failure.getMessage());
            } finally {if(window!=null)try{window.removeMember("__quizforgePermissions");}catch(RuntimeException ignored){}}
        }
    }
    private void activate(InstalledExtension installed) throws IOException {
        requireFx();
        String id=installed.manifest().id();
        if(runtimes.containsKey(key(installed)))return;
        var assets=store.loadAssets(installed);
        String sources=assets.stream().map(ExtensionPackageStore.TypeAssets::rulesSource).distinct().collect(java.util.stream.Collectors.joining("\n;\n"));
        Map<String,Map<String,Object>> templates=new LinkedHashMap<>();
        Map<String,io.quizforge.infrastructure.extension.ExtensionSchemaValidator> validators=new LinkedHashMap<>();
        for(var asset:assets)templates.put(asset.type().id(),json.readValue(asset.defaultQuestionSource(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {}));
        for(var asset:assets)validators.put(asset.type().id(),new io.quizforge.infrastructure.extension.ExtensionSchemaValidator(asset.type().id(),asset.questionSchemaSource(),asset.answerSchemaSource()));
        ExtensionRuleRuntime runtime=ExtensionRuleRuntime.lazy(sources,templates);
        List<String> registered=new ArrayList<>();
        try {
            List<ExternalQuestionTypeDefinition> definitions=new ArrayList<>();
            for(var type:installed.manifest().types())definitions.add(new ExternalQuestionTypeDefinition(type.id(),type.label(),
                    QuestionTypeDefinition.Family.valueOf(type.family()),installed.manifest().version(),installed.manifest().id(),
                    type.dataVersion(),runtime.rules(type.id()),templates.get(type.id()),validators.get(type.id())));
            var pageBundle=bundle(installed,assets);
            for(var definition:definitions){QuestionTypes.registerVersion(definition);registered.add(definition.id());}
            runtimes.put(key(installed),runtime);
            var previous=active.get(id);
            if(previous==null||compareVersions(installed.manifest().version(),previous.manifest().version())>0)active.put(id,installed);
            scriptPackages.put(key(installed),pageBundle);
        }catch(RuntimeException failure){
            for(String type:registered)QuestionTypes.unregisterVersion(type,installed.manifest().version());
            runtime.close();throw failure;
        }
    }
    /** Host diagnostics for startup/lazy-loading verification; not exposed to extension pages. */
    int runningRuleRuntimeCount(){requireFx();return (int)runtimes.values().stream().filter(runtime->runtime.workerPid()>0).count();}
    public List<InstalledExtension> loaded() { return List.copyOf(active.values()); }
    public List<InstalledExtension> diskInstalled() throws IOException { return store == null ? List.of() : store.listInstalled(); }
    public List<String> failures() { return List.copyOf(failures); }
    public javafx.beans.property.ReadOnlyStringProperty developmentStatusProperty() { return developmentStatus.getReadOnlyProperty(); }
    public CompletionStage<Void> loadDevelopmentDirectory(Path directory) {
        requireFx(); Path path = directory.toAbsolutePath().normalize();
        try {
            var source = development.get(path);
            if (source == null) {
                source = new ExtensionLiveDevelopment(path, candidate -> publishDevelopment(path, candidate), developmentStatus::set);
                development.put(path, source);
            }
            return source.refresh();
        } catch (IOException failure) { return CompletableFuture.failedFuture(failure); }
    }
    private void publishDevelopment(Path directory, io.quizforge.infrastructure.extension.ExtensionDevelopmentSource.Candidate candidate) {
        requireFx(); String key = candidate.manifest().id() + "@" + candidate.manifest().version();
        var installed = scriptPackages.get(key);
        if (installed == null || !candidate.manifest().equals(installed.get("manifest")))
            throw new IllegalArgumentException("主浏览区预览需要相同 ID、版本和清单的已安装扩展；新题型请先安装或使用独立开发预览");
        runtimes.get(key).validatePageScripts(candidate.assets().stream().flatMap(asset -> java.util.stream.Stream.of(asset.editorSource(), asset.rendererSource())).toList());
        var live = new LinkedHashMap<String,Object>(installed);
        // Core templates, schemas and rules stay fixed for active sessions and archived attempts.
        var originalAssets = (List<?>) installed.get("assets");
        var viewAssets = new ArrayList<Map<String,Object>>();
        for (var asset : candidate.assets()) {
            var original = originalAssets.stream().map(item -> (Map<?,?>) item).filter(item -> asset.type().id().equals(item.get("typeId"))).findFirst().orElseThrow();
            var view = new LinkedHashMap<String,Object>(); original.forEach((name,value) -> view.put((String)name,value));
            view.put("editorHtml", asset.editorHtml()); view.put("rendererHtml", asset.rendererHtml());
            view.put("editorSource", asset.editorSource()); view.put("rendererSource", asset.rendererSource()); view.put("stylesSource", asset.stylesSource());
            viewAssets.add(view);
        }
        live.put("assets", viewAssets); live.put("revision", candidate.revision());
        Path previous = developmentOwners.put(key, directory);
        if (previous != null && !previous.equals(directory)) {
            var old = development.remove(previous); if (old != null) old.close();
        }
        developmentPackages.put(key, live); publishToPages(live);
    }
    public void stopDevelopment() {
        requireFx(); development.values().forEach(ExtensionLiveDevelopment::close); development.clear();
        var keys = List.copyOf(developmentPackages.keySet()); developmentPackages.clear();
        developmentOwners.clear();
        keys.forEach(key -> publishToPages(scriptPackages.get(key)));
        developmentStatus.set("主浏览区实时预览已停止，已恢复安装版本");
    }
    private void publishToPages(Map<String,Object> bundle) {
        publishMessage(Map.of("kind","development","bundle",bundle));
        for (WebEngine engine : List.copyOf(pages.keySet())) {
            try { applyDevelopment(engine, bundle); }
            catch (RuntimeException failure) { developmentStatus.set("实时预览更新失败：" + failure.getMessage()); }
        }
    }
    private void applyDevelopment(WebEngine engine, Map<String,Object> bundle) {
        if (!Boolean.TRUE.equals(engine.executeScript("typeof window.questionExtensions?.replaceDevelopment === 'function'"))) return;
        String key = ((io.quizforge.infrastructure.extension.ExtensionManifest) bundle.get("manifest")).id();
        String revision = (String) bundle.getOrDefault("revision", bundle.get("sha256"));
        var applied = pages.computeIfAbsent(engine, ignored -> new LinkedHashMap<>());
        if (revision.equals(applied.get(key))) return;
        JSObject window = (JSObject) engine.executeScript("window");
        try {
            window.setMember("__quizforgeDevelopment", json.writeValueAsString(bundle));
            engine.executeScript("(function(bundle){window.__qfExtensionsReady=Promise.resolve(window.__qfExtensionsReady).then(function(){return window.questionExtensions.replaceDevelopment(bundle);});window.__qfExtensionsReady.catch(function(error){console.error(error);});})(JSON.parse(window.__quizforgeDevelopment));");
            applied.put(key, revision);
        } catch (IOException failure) { throw new IllegalStateException(failure); }
        finally { window.removeMember("__quizforgeDevelopment"); }
    }
    public String packagesJson() {
        try { return json.writeValueAsString(scriptPackages.values()); }
        catch (IOException failure) { throw new IllegalStateException(failure); }
    }
    /** Call after each local editor/practice/history page has installed its shared extension SDK. */
    public void installScripts(javafx.scene.web.WebView view) {
        ExtensionPageBridge.install(view);
        WebEngine engine=view.getEngine();
        requireFx(); if (scriptPackages.isEmpty()) return;
        pages.put(engine, new LinkedHashMap<>());
        JSObject window = (JSObject) engine.executeScript("window"); window.setMember("__quizforgeExtensions", packagesJson());
        try { engine.executeScript("(function(packages){window.__quizforgeInstalledPackages=window.__quizforgeInstalledPackages||Object.create(null);window.__qfExtensionsReady=Promise.resolve(window.__qfExtensionsReady).then(async function(){for(const p of packages){var key=p.manifest.id+'@'+p.manifest.version+':'+p.sha256;if(!window.__quizforgeInstalledPackages[key]){await window.questionExtensions.install(p);window.__quizforgeInstalledPackages[key]=true;}}});window.__qfExtensionsReady.catch(function(error){console.error(error);});})(JSON.parse(window.__quizforgeExtensions));"); }
        finally { window.removeMember("__quizforgeExtensions"); }
        developmentPackages.values().forEach(bundle -> applyDevelopment(engine, bundle));
    }
    public Map<String,Object> packageForType(String typeId) {
        for (var entry : active.entrySet()) for (var type : entry.getValue().manifest().types()) if (type.id().equals(typeId)) return Map.copyOf(scriptPackages.get(key(entry.getValue())));
        return Map.of();
    }
    private static String key(InstalledExtension extension) { return extension.manifest().id() + "@" + extension.manifest().version(); }
    private Map<String,Object> bundle(InstalledExtension installed, List<ExtensionPackageStore.TypeAssets> assets) {
        Map<String,Object> bundle = new LinkedHashMap<>(); bundle.put("manifest", installed.manifest());
        try {bundle.put("grantedPermissions",permissions.grants(installed));}
        catch(IOException|RuntimeException failure) {
            failures.add(installed.manifest().id()+"：无法读取权限，已拒绝受保护操作："+failure.getMessage());
            bundle.put("grantedPermissions",Map.of());
        }
        bundle.put("sha256", installed.sha256());
        bundle.put("rendererSource", assets.stream().map(ExtensionPackageStore.TypeAssets::rendererSource).distinct().collect(java.util.stream.Collectors.joining("\n;\n")));
        bundle.put("editorSource", assets.stream().map(ExtensionPackageStore.TypeAssets::editorSource).distinct().collect(java.util.stream.Collectors.joining("\n;\n")));
        bundle.put("stylesSource", assets.stream().map(ExtensionPackageStore.TypeAssets::stylesSource).distinct().collect(java.util.stream.Collectors.joining("\n")));
        bundle.put("rulesSource", assets.stream().map(ExtensionPackageStore.TypeAssets::rulesSource).distinct().collect(java.util.stream.Collectors.joining("\n;\n")));
        bundle.put("assets", assetBundles(assets));
        return bundle;
    }
    private static List<Map<String,Object>> assetBundles(List<ExtensionPackageStore.TypeAssets> assets) {
        ObjectMapper json = new ObjectMapper();
        return assets.stream().map(asset -> {
            Map<String,Object> item=new LinkedHashMap<>();item.put("typeId",asset.type().id());
            item.put("editorHtml",asset.editorHtml());item.put("rendererHtml",asset.rendererHtml());
            item.put("editorSource",asset.editorSource());item.put("rendererSource",asset.rendererSource());
            item.put("stylesSource",asset.stylesSource());item.put("rulesSource",asset.rulesSource());
            item.put("questionSchemaSource",asset.questionSchemaSource());item.put("answerSchemaSource",asset.answerSchemaSource());
            try { item.put("defaultQuestion",json.readTree(asset.defaultQuestionSource())); }
            catch(IOException failure){throw new IllegalArgumentException("Invalid defaultQuestion",failure);}
            return item;
        }).toList();
    }
    public String label(String typeId) {
        for (var extension : active.values()) for (var type : extension.manifest().types()) if (type.id().equals(typeId)) return type.label();
        return typeId;
    }
    @Override public void close() {
        requireFx(); generation++;
        publishMessage(Map.of("kind","stop"));messagePages.clear();
        ExtensionPageBridge.closeAll();
        development.values().forEach(ExtensionLiveDevelopment::close); development.clear(); developmentPackages.clear(); developmentOwners.clear(); pages.clear(); developmentStatus.set("");
        for (var runtime : runtimes.values()) runtime.close();
        for (var bundle : scriptPackages.values()) {
            var manifest = (io.quizforge.infrastructure.extension.ExtensionManifest) bundle.get("manifest");
            for (var type : manifest.types()) QuestionTypes.unregisterVersion(type.id(), manifest.version());
        }
        active.clear(); runtimes.clear(); scriptPackages.clear();
    }
    private static int compareVersions(String left, String right) {
        return io.quizforge.core.question.type.extension.ExtensionVersion.compare(left, right);
    }
    private static void requireFx() { if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Use the JavaFX application thread"); }
}
