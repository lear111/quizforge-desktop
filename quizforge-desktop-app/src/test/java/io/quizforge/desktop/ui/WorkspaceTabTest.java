package io.quizforge.desktop.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class WorkspaceTabTest {
    @Test void tabIdentityUsesNormalizedRelativePath() {
        assertEquals("Java/Collections.md", WorkspaceTab.normalize("Java\\Collections.md"));
        assertThrows(IllegalArgumentException.class, () -> WorkspaceTab.normalize("C:\\Users\\A.md"));
        assertThrows(IllegalArgumentException.class, () -> WorkspaceTab.normalize("../A.md"));
    }
}
