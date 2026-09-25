package io.quizforge.desktop.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quizforge.infrastructure.filesystem.QuizForgeDataDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class DesktopConfigurationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void contextStartsAndCloses() throws Exception {
        String previous = System.getProperty(QuizForgeDataDirectory.OVERRIDE_PROPERTY);
        System.setProperty(QuizForgeDataDirectory.OVERRIDE_PROPERTY, temporaryDirectory.toString());
        try {
            AnnotationConfigApplicationContext context =
                    new AnnotationConfigApplicationContext(DesktopConfiguration.class);

            assertTrue(context.isActive());
            assertTrue(context.containsBean("desktopConfiguration"));
            assertTrue(Files.exists(temporaryDirectory.resolve("quizforge.db")));

            context.close();
            assertFalse(context.isActive());
        } finally {
            if (previous == null) {
                System.clearProperty(QuizForgeDataDirectory.OVERRIDE_PROPERTY);
            } else {
                System.setProperty(QuizForgeDataDirectory.OVERRIDE_PROPERTY, previous);
            }
        }
    }
}
