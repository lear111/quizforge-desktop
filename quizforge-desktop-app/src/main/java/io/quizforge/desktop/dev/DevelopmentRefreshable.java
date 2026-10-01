package io.quizforge.desktop.dev;

/** A controlled refresh entry for development, preserving the owning view's state. */
public interface DevelopmentRefreshable {
    void refreshForDevelopment();
    default boolean shouldRefreshForDevelopment(){return true;}
}
