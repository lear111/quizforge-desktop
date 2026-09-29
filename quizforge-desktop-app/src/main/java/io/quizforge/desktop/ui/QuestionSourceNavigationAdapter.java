package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;

import io.quizforge.core.document.navigation.QuizForgeNavigationLink;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.List;
import java.util.function.Consumer;

/** Bridges revision-sensitive source inspection to current-location navigation without writing assets. */
final class QuestionSourceNavigationAdapter {
    record Source(SourceRef ref, String label,
            QuestionBankReferenceResolver.Status status, String message, boolean navigable) { }

    private final QuestionBankReferenceResolver resolver;
    private final QuestionSourceLinkService names;
    private final Consumer<QuizForgeNavigationLink> navigate;
    private final Consumer<String> feedback;

    QuestionSourceNavigationAdapter(QuestionBankReferenceResolver resolver, QuestionSourceLinkService names,
            Consumer<QuizForgeNavigationLink> navigate, Consumer<String> feedback) {
        this.resolver = resolver;
        this.names = names;
        this.navigate = navigate;
        this.feedback = feedback;
    }

    List<Source> inspect(WorkspaceId workspace, List<SourceRef> refs) {
        try {
            return resolver.resolveCurrentRefs(workspace, refs).stream().map(resolved -> {
                var ref = resolved.sourceRef();
                boolean named = ref.anchorName() != null;
                boolean usable = named && !resolved.ambiguous()
                        && (resolved.status() == QuestionBankReferenceResolver.Status.EXACT_MATCH
                            || resolved.status() == QuestionBankReferenceResolver.Status.DIFFERENT_REVISION);
                String message = message(resolved.status());
                if (resolved.ambiguous()) message = "来源身份冲突";
                else if (!named && message.isEmpty()) message = "旧版节点引用";
                return new Source(ref, names.displayName(workspace, ref), resolved.status(), message, usable);
            }).toList();
        } catch (RuntimeException error) {
            return refs.stream().map(ref -> new Source(ref,
                    ref.documentTitle() + " · " + (ref.anchorName() == null ? ref.sectionTitle() : ref.anchorName()),
                    QuestionBankReferenceResolver.Status.UNAVAILABLE_DOCUMENT, "来源暂不可读取", false)).toList();
        }
    }

    void open(WorkspaceId workspace, SourceRef ref) {
        Source current = inspect(workspace, List.of(ref)).getFirst();
        if (!current.navigable()) {
            feedback.accept(current.message());
            return;
        }
        // The historical contentId remains on the SourceRef and is deliberately absent from navigation.
        try {
            navigate.accept(QuizForgeNavigationLink.anchor(ref.documentAssetId(), ref.anchorName(), ref.occurrence()));
        } catch (RuntimeException error) {
            feedback.accept("来源暂时无法打开。");
        }
    }

    private static String message(QuestionBankReferenceResolver.Status status) {
        return switch (status) {
            case EXACT_MATCH -> "";
            case DIFFERENT_REVISION -> "来源已修改";
            case MISSING_DOCUMENT, MISSING -> "来源文档缺失";
            case MISSING_ANCHOR, MISSING_NODE -> "来源位置缺失";
            case ORPHAN_ANCHOR -> "来源锚点无有效内容";
            case UNAVAILABLE_DOCUMENT -> "来源暂不可读取";
            case EXACT_CONTENT_MATCH -> "来源文档缺失";
        };
    }
}
