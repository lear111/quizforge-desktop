package io.quizforge.core.document.navigation;

import io.quizforge.core.document.registered.NamedMarkdownAnchor;

/** Current-location address; unlike a QBank source reference it has no content revision. */
public record QuizForgeNavigationLink(String assetId, Target target) {
    public QuizForgeNavigationLink {
        if (assetId == null || !assetId.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid navigation assetId");
        if (target == null) throw new IllegalArgumentException("Navigation target is required");
    }

    public sealed interface Target permits AssetTarget, HeadingTarget, AnchorTarget { }

    public record AssetTarget() implements Target { }

    public record HeadingTarget(String headingText, int occurrence) implements Target {
        public HeadingTarget {
            if (headingText == null || headingText.isBlank())
                throw new IllegalArgumentException("Empty navigation heading");
            headingText = headingText.trim();
            if (occurrence < 1) throw new IllegalArgumentException("Invalid heading occurrence");
        }
    }

    public record AnchorTarget(String anchorName, int occurrence) implements Target {
        public AnchorTarget {
            anchorName = NamedMarkdownAnchor.validateName(anchorName);
            if (occurrence < 1) throw new IllegalArgumentException("Invalid anchor occurrence");
        }
    }

    public static QuizForgeNavigationLink asset(String assetId) {
        return new QuizForgeNavigationLink(assetId, new AssetTarget());
    }

    public static QuizForgeNavigationLink heading(String assetId, String text, int occurrence) {
        return new QuizForgeNavigationLink(assetId, new HeadingTarget(text, occurrence));
    }

    public static QuizForgeNavigationLink anchor(String assetId, String name, int occurrence) {
        return new QuizForgeNavigationLink(assetId, new AnchorTarget(name, occurrence));
    }
}
