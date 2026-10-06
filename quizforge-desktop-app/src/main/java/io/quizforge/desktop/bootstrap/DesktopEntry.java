package io.quizforge.desktop.bootstrap;

/** Classpath entry for the standard desktop application, including isolated developer profiles. */
public final class DesktopEntry {
    private DesktopEntry() { }
    public static void main(String[] args) {
        javafx.application.Application.launch(DesktopApplication.class, args);
    }
}
