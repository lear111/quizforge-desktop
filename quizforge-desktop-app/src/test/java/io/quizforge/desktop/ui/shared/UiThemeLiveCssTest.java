package io.quizforge.desktop.ui.shared;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiThemeLiveCssTest {
    @TempDir Path styles;

    @Test void developmentStylesheetUrlChangesWhenSourceCssChanges() throws Exception {
        String previous = System.getProperty("quizforge.ui.liveCssDir");
        try {
            System.setProperty("quizforge.ui.liveCssDir", styles.toString());
            Path workspace = styles.resolve("workspace.css");
            Path markdown = styles.resolve("markdown-preview.css");
            Files.writeString(workspace, ".workspace-sidebar { -fx-padding: 1; }");
            Files.writeString(markdown, ".markdown-preview { -fx-padding: 2; }");

            String first = UiTheme.stylesheet();
            assertTrue(UiTheme.liveCssEnabled());
            assertEquals(Files.readString(workspace), decode(first));
            assertEquals(Files.readString(markdown), decode(UiTheme.markdownStylesheet()));

            Files.writeString(workspace, ".workspace-sidebar { -fx-padding: 3; }");
            assertNotEquals(first, UiTheme.stylesheet());
            assertEquals(Files.readString(workspace), decode(UiTheme.stylesheet()));
        } finally {
            if (previous == null) System.clearProperty("quizforge.ui.liveCssDir");
            else System.setProperty("quizforge.ui.liveCssDir", previous);
        }
    }

    @Test void directMavenLaunchFindsSourceCssWithoutLauncherFlag() throws Exception {
        Path source = Files.createDirectories(styles.resolve(
                "quizforge-desktop-app/src/main/resources/styles"));
        Files.writeString(source.resolve("workspace.css"), "");
        Files.writeString(source.resolve("markdown-preview.css"), "");

        String application = "io.quizforge.desktop.bootstrap.DesktopApplication";
        assertEquals(source, UiTheme.developmentCssDirectory(styles, application,
                "quizforge-desktop-app/target/classes"));
        assertEquals(source, UiTheme.developmentCssDirectory(styles, application + " --debug",
                "quizforge-desktop-app\\target\\classes"));
        assertNull(UiTheme.developmentCssDirectory(styles, application, "quizforge-desktop-app.jar"));
        assertNull(UiTheme.developmentCssDirectory(styles, "org.apache.maven.surefire.booter.ForkedBooter",
                "quizforge-desktop-app/target/classes"));
    }

    private String decode(String stylesheet) {
        String prefix = "data:text/css;base64,";
        assertTrue(stylesheet.startsWith(prefix));
        return new String(Base64.getDecoder().decode(stylesheet.substring(prefix.length())),
                StandardCharsets.UTF_8);
    }
}
