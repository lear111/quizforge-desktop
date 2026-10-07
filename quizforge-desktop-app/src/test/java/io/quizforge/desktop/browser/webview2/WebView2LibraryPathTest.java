package io.quizforge.desktop.browser.webview2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WebView2LibraryPathTest {
    @TempDir Path temporary;
    @Test void moduleWorkingDirectoryFindsRepositoryAdapter() throws Exception {
        Path repository=Files.createDirectories(temporary.resolve("repository with spaces"));
        Path module=Files.createDirectories(repository.resolve("quizforge-desktop-app"));
        Path library=repository.resolve("target/webview2-native/quizforge_webview2.dll");
        Files.createDirectories(library.getParent());Files.write(library,new byte[]{1});
        assertEquals(library,WebView2LibraryPath.resolve(null,null,module,null));
    }
    @Test void codeLocationFindsPackagedLibraryFromUnrelatedWorkingDirectory() throws Exception {
        Path app=Files.createDirectories(temporary.resolve("application"));
        Path nativeDirectory=Files.createDirectories(app.resolve("native"));
        Path library=Files.write(nativeDirectory.resolve("quizforge_webview2.dll"),new byte[]{1});
        Path jar=Files.write(Files.createDirectories(app.resolve("lib")).resolve("desktop.jar"),new byte[]{1});
        Path working=Files.createDirectories(temporary.resolve("elsewhere"));
        Files.write(working.resolve("quizforge_webview2.dll"),new byte[]{2});
        assertEquals(library,WebView2LibraryPath.resolve(null,null,working,jar),"The application library takes precedence over an unrelated working directory");
    }
    @Test void explicitConfigurationIsHonoredAndInvalidOverrideDoesNotSilentlyFallBack() throws Exception {
        Path library=Files.write(temporary.resolve("adapter.dll"),new byte[]{1});
        assertEquals(library,WebView2LibraryPath.resolve(library.toString(),"wrong.dll",temporary,null));
        assertEquals(library,WebView2LibraryPath.resolve(null,library.toString(),temporary,null));
        assertThrows(IOException.class,()->WebView2LibraryPath.resolve(temporary.resolve("missing.dll").toString(),library.toString(),temporary,null));
    }
}
