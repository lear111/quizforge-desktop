package io.quizforge.desktop.browser.webview2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;

/** Resolves the trusted native adapter independently of Maven/IDE working directories. */
final class WebView2LibraryPath {
    private WebView2LibraryPath() { }
    static Path resolve() throws IOException {
        Path location=null;
        try {location=Path.of(WebView2Browser.class.getProtectionDomain().getCodeSource().getLocation().toURI());}
        catch(Exception ignored) { /* Explicit configuration and working-directory lookup remain available. */ }
        return resolve(System.getProperty("quizforge.webview2.library"),System.getenv("QUIZFORGE_WEBVIEW2_LIBRARY"),
                Path.of(System.getProperty("user.dir")),location);
    }
    static Path resolve(String configured,String environment,Path workingDirectory,Path codeLocation) throws IOException {
        String override=configured!=null&&!configured.isBlank()?configured:environment;
        if(override!=null&&!override.isBlank()) {
            Path library=Path.of(override).toAbsolutePath().normalize();
            if(Files.isRegularFile(library))return library;
            throw new IOException("WebView2 原生组件路径无效："+library);
        }
        var roots=new LinkedHashSet<Path>();
        // Prefer the application's own location over a caller's working directory.
        if(codeLocation!=null)addParents(roots,Files.isDirectory(codeLocation)?codeLocation:codeLocation.getParent());
        addParents(roots,workingDirectory);
        for(Path root:roots)for(String relative:java.util.List.of("target/webview2-native/quizforge_webview2.dll",
                "native/quizforge_webview2.dll","quizforge_webview2.dll")) {
            Path library=root.resolve(relative);
            if(Files.isRegularFile(library))return library;
        }
        throw new IOException("缺少 WebView2 原生组件。开发环境请运行仓库 tools/Build-WebView2.ps1，或配置 quizforge.webview2.library 的绝对路径。");
    }
    private static void addParents(java.util.Set<Path> roots,Path directory) {
        for(Path current=directory==null?null:directory.toAbsolutePath().normalize();current!=null;current=current.getParent())roots.add(current);
    }
}
