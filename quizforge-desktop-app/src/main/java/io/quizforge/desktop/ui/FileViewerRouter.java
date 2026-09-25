package io.quizforge.desktop.ui;

import io.quizforge.core.question.QuestionBankFile;
import io.quizforge.core.question.QuestionBankReferenceResolver;
import io.quizforge.core.workspace.OpenedWorkspaceFile;
import io.quizforge.core.workspace.WorkspaceFileEntry;
import io.quizforge.core.workspace.WorkspaceFileKind;
import io.quizforge.core.workspace.WorkspaceId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

/** Small explicit routing table for the file-first read-only viewers. */
final class FileViewerRouter {
    private final Map<WorkspaceFileKind, BiFunction<WorkspaceId, OpenedWorkspaceFile, Node>> routes =
            new EnumMap<>(WorkspaceFileKind.class);
    private final SafeMarkdownPreview preview = new SafeMarkdownPreview();
    private final QuestionBankReferenceResolver references;

    FileViewerRouter(QuestionBankReferenceResolver references) {
        this.references = references;
        routes.put(WorkspaceFileKind.DIRECTORY, this::folder);
        routes.put(WorkspaceFileKind.MARKDOWN, this::markdown);
        routes.put(WorkspaceFileKind.STANDARD_DOCUMENT, this::standardDocument);
        routes.put(WorkspaceFileKind.QUESTION_BANK, this::questionBank);
        routes.put(WorkspaceFileKind.INVALID_STANDARD_DOCUMENT, this::invalid);
        routes.put(WorkspaceFileKind.INVALID_QUESTION_BANK, this::invalid);
        routes.put(WorkspaceFileKind.OTHER, this::unsupported);
    }

    Node view(WorkspaceId workspaceId, OpenedWorkspaceFile file) {
        return routes.get(file.entry().kind()).apply(workspaceId, file);
    }

    Node welcome() {
        return UiTheme.emptyState("file", "Select a file from the workspace",
                "Choose any file in the tree to preview it here.");
    }

    private Node folder(WorkspaceId ignored, OpenedWorkspaceFile file) {
        return page(file.entry().name(), "Folder", file.entry().relativePath());
    }

    private Node markdown(WorkspaceId ignored, OpenedWorkspaceFile file) {
        VBox page = page(file.entry().name(), "Markdown", file.entry().relativePath());
        page.getChildren().add(markdownTabs(file.sourceText()));
        return page;
    }

    private Node standardDocument(WorkspaceId ignored, OpenedWorkspaceFile file) {
        WorkspaceFileEntry entry = file.entry();
        VBox page = page(entry.title(), "Standard Document  ·  VALID", entry.relativePath());
        page.getChildren().addAll(UiTheme.label("assetId: " + entry.assetId(), "muted"),
                UiTheme.label("contentId: " + entry.contentId(), "muted"),
                markdownTabs(file.sourceText()));
        return page;
    }

    private Node questionBank(WorkspaceId workspaceId, OpenedWorkspaceFile file) {
        QuestionBankFile bank = file.questionBank();
        VBox page = page(bank.title(), "Question Bank", file.entry().relativePath());
        page.getChildren().addAll(UiTheme.label("assetId: " + bank.id(), "muted"),
                UiTheme.label("Questions: " + bank.questions().size(), "muted"),
                UiTheme.label("Source Documents: " + bank.sourceDocuments().size(), "muted"));
        Map<String, QuestionBankReferenceResolver.Resolution> resolutions = new HashMap<>();
        for (var resolution : references.resolve(workspaceId, bank)) {
            resolutions.put(resolution.source().assetId(), resolution);
            page.getChildren().add(UiTheme.label("Reference Status · " + resolution.source().title()
                    + ": " + resolution.status() + (resolution.ambiguous()
                    ? " (ambiguous: " + resolution.candidates().size() + " candidates)" : ""), "muted"));
        }
        int index = 1;
        for (QuestionBankFile.Entry question : bank.questions()) {
            VBox card = new VBox(9, UiTheme.label(index++ + ". " + question.type(), "eyebrow"),
                    UiTheme.label(question.stem(), "question-stem"));
            card.getStyleClass().add("question-card");
            List<String> correct = question.data().correctOptionIds();
            for (int i = 0; i < question.data().options().size(); i++) {
                var option = question.data().options().get(i);
                String label = Character.toString('A' + i);
                card.getChildren().add(UiTheme.label(label + ". " + option.content(), "question-option"));
            }
            VBox answer = new VBox(7, UiTheme.label("Correct answer: " + answerLabels(question), "answer"),
                    UiTheme.label(question.analysis(), "muted"));
            TitledPane reveal = new TitledPane("Answer & analysis", answer);
            reveal.setExpanded(false);
            card.getChildren().add(reveal);
            for (QuestionBankFile.SourceRef ref : question.sourceRefs()) {
                var status = resolutions.get(ref.documentAssetId());
                card.getChildren().add(UiTheme.label("Source: " + ref.documentTitle() + " → "
                        + ref.sectionTitle() + "  ·  " + ref.documentAssetId() + " / " + ref.sectionId()
                        + "  ·  " + (status == null ? "MISSING" : status.status()), "muted"));
            }
            page.getChildren().add(card);
        }
        return page;
    }

    private String answerLabels(QuestionBankFile.Entry question) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < question.data().options().size(); i++) {
            if (!question.data().correctOptionIds().contains(question.data().options().get(i).id())) continue;
            if (!out.isEmpty()) out.append(", ");
            out.append((char) ('A' + i));
        }
        return out.toString();
    }

    private Node invalid(WorkspaceId ignored, OpenedWorkspaceFile file) {
        VBox page = page(file.entry().name(), "Invalid QuizForge asset", file.entry().relativePath());
        page.getChildren().add(UiTheme.label(file.entry().issue() == null
                ? "This file failed format validation." : file.entry().issue(), "warning"));
        return page;
    }

    private Node unsupported(WorkspaceId ignored, OpenedWorkspaceFile file) {
        VBox page = page(file.entry().name(), "Unsupported file", file.entry().relativePath());
        page.getChildren().add(UiTheme.label("A viewer for this file type is not available yet.", "muted"));
        return page;
    }

    private TabPane markdownTabs(String source) {
        Tab previewTab = new Tab("Preview", preview.view(source));
        Tab sourceTab = new Tab("Source", sourceArea(source));
        TabPane tabs = new TabPane(previewTab, sourceTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("workspace-tabs");
        tabs.setId("file-view-tabs");
        previewTab.getContent().setId("viewer-preview");
        sourceTab.getContent().setId("viewer-source");
        return tabs;
    }

    private Node sourceArea(String source) {
        TextArea text = new TextArea(source);
        text.setEditable(false);
        text.setWrapText(false);
        text.getStyleClass().add("document-editor");
        return text;
    }

    private VBox page(String title, String type, String path) {
        VBox page = new VBox(12, UiTheme.label(title, "section-title"),
                UiTheme.label(type, "eyebrow"), UiTheme.label("Path: " + path, "muted"));
        page.getStyleClass().add("file-viewer-page");
        return page;
    }
}
