package io.quizforge.dev;

/** Plain launcher avoids the Java launcher special case for Application subclasses. */
public final class QuizForgeDevLauncher {
    public static void main(String[] args) throws Exception {
        Class.forName("io.quizforge.desktop.bootstrap.DesktopApplication")
                .getMethod("main", String[].class).invoke(null, (Object) args);
    }
}
