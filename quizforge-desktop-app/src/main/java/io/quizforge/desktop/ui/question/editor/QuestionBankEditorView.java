package io.quizforge.desktop.ui.question.editor;

import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.source.QuestionSourceLinkService;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.dev.DevelopmentRefreshable;
import io.quizforge.desktop.ui.content.StagedContentResource;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
import io.quizforge.desktop.ui.question.shared.QuestionTypeCatalog;
import io.quizforge.desktop.ui.question.source.QuestionSourceListView;
import io.quizforge.desktop.ui.question.source.QuestionSourceNavigationAdapter;
import io.quizforge.desktop.ui.shared.EditorUi;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** One-question editor. Every control mutates the same portable bank model used by Practice. */
public final class QuestionBankEditorView extends VBox implements DevelopmentRefreshable {
    private final QuestionBankEditorModel model;
    private final WorkspaceId workspace;
    private final QuestionSourceLinkService sourceLinks;
    private final QuestionSourceNavigationAdapter sourceNavigation;
    private QuestionSourceListView sourceRows;
    private final Consumer<QuestionBank> save;
    private final io.quizforge.core.port.QuestionResourceInput existingResources;
    private final java.util.Map<String,StagedContentResource> imported=new java.util.LinkedHashMap<>();
    private final java.util.List<Runnable> validateFields=new java.util.ArrayList<>();
    private final VBox body = new VBox(14);
    private final VBox errors = new VBox(3);
    private int index;
    private BiConsumer<QuestionBank, Integer> onQuestionChange = (bank, selected) -> { };

    public QuestionBankEditorView(QuestionBank bank, WorkspaceId workspace,
            QuestionSourceLinkService sourceLinks,
            QuestionSourceNavigationAdapter sourceNavigation, Consumer<QuestionBank> save) {
        this(bank,workspace,sourceLinks,sourceNavigation,save,io.quizforge.core.port.QuestionResourceInput.NONE);
    }
    public QuestionBankEditorView(QuestionBank bank, WorkspaceId workspace, QuestionSourceLinkService sourceLinks,
            QuestionSourceNavigationAdapter sourceNavigation, Consumer<QuestionBank> save,
            io.quizforge.core.port.QuestionResourceInput existingResources) {
        model = new QuestionBankEditorModel(bank);
        this.workspace = workspace;
        this.sourceLinks = sourceLinks;
        this.sourceNavigation = sourceNavigation;
        this.save = save;
        this.existingResources=existingResources;
        setId("question-bank-editor");
        getStyleClass().add("qbank-editor");
        setMaxWidth(820);
        setSpacing(16);
        errors.managedProperty().bind(javafx.beans.binding.Bindings.isNotEmpty(errors.getChildren()));
        getChildren().addAll(EditorUi.toolbar("题库编辑", "qbank-save", this::save), errors, body);
        EditorUi.saveShortcut(this, this::save);
        render();
    }

    public boolean dirty() { return model.dirty(); }
    @Override public void refreshForDevelopment() {
        getChildren().set(0, EditorUi.toolbar("题库编辑", "qbank-save", this::save));
        render();
    }
    public void refreshSources() { if (sourceRows != null) sourceRows.refresh(); }

    public void jumpTo(int target) {
        if (target < 0 || target >= model.bank().questions().size()) return;
        index = target;
        render();
    }

    public void onQuestionChange(BiConsumer<QuestionBank, Integer> action) {
        onQuestionChange = action;
        onQuestionChange.accept(model.bank(), index);
    }

    private void save() {
        errors.getChildren().clear();
        try { validateFields.forEach(Runnable::run); save.accept(model.bank()); }
        catch (RuntimeException error) { showError(error.getMessage()); }
    }
    public void saveChanges() { save(); }

    private void showError(String message) {
        errors.getChildren().setAll(UiTheme.label(message == null ? "Could not save QuestionBank" : message,
                "editor-error"));
    }

    private void render() {
        onQuestionChange.accept(model.bank(), index);
        sourceRows = null;
        validateFields.clear();
        body.getChildren().clear();
        boolean essay = !model.bank().questions().isEmpty() && QuestionTypes.isEssay(model.bank().questions().get(index).type());
        getStyleClass().remove("essay-editor");
        if (essay) getStyleClass().add("essay-editor");

        Button previous = UiTheme.iconButton("arrow-left", "上一题", () -> { });
        previous.setId("qbank-editor-previous");
        previous.setDisable(index == 0);
        previous.setOnAction(event -> { index--; render(); });
        Button next = UiTheme.iconButton("arrow", "下一题", () -> { });
        next.setId("qbank-editor-next");
        next.setDisable(index >= model.bank().questions().size() - 1);
        next.setOnAction(event -> { index++; render(); });
        MenuButton add = EditorUi.menu("plus", "添加题目");
        add.setId("qbank-add-question");
        for (String type : QuestionTypeCatalog.editableTypes()) {
            MenuItem item = new MenuItem(typeName(type));
            item.setOnAction(event -> { index = model.addQuestion(type); render(); });
            add.getItems().add(item);
        }
        Label position = UiTheme.label(model.bank().questions().isEmpty() ? "0 / 0"
                : (index + 1) + " / " + model.bank().questions().size(), "muted");
        position.setId("qbank-editor-position");
        HBox navigation = new HBox(10, previous, position, next, add);
        navigation.setAlignment(Pos.CENTER_LEFT);
        navigation.getStyleClass().add("qbank-edit-navigation");
        body.getChildren().add(navigation);
        if (model.bank().questions().isEmpty()) {
            body.getChildren().add(UiTheme.quietState("还没有题目", "点击 +，选择单选题或多选题开始编辑。"));
            return;
        }
        Question question = model.bank().questions().get(index);
        Button duplicate = UiTheme.iconButton("copy", "复制题目", () -> { });
        duplicate.setId("qbank-duplicate-question");
        duplicate.setOnAction(event -> { index = model.duplicateQuestion(index); render(); });
        Button delete = UiTheme.iconButton("trash", "删除题目", () -> { });
        delete.setId("qbank-delete-question");
        delete.setOnAction(event -> {
            var remove = new ButtonType("删除", ButtonBar.ButtonData.OK_DONE);
            var cancel = new ButtonType("取消", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "题干、选项及相关内容将一并从当前题库中移除。", cancel, remove);
            confirm.setTitle("删除题目");
            confirm.setHeaderText("确定删除第 " + (index + 1) + " 题吗？");
            if (getScene() != null && getScene().getWindow() != null) {
                confirm.initOwner(getScene().getWindow());
                confirm.initModality(javafx.stage.Modality.WINDOW_MODAL);
            }
            UiTheme.apply(confirm);
            ((Button) confirm.getDialogPane().lookupButton(remove)).setDefaultButton(false);
            ((Button) confirm.getDialogPane().lookupButton(cancel)).setDefaultButton(true);
            if (confirm.showAndWait().orElse(cancel) != remove) return;
            model.deleteQuestion(index);
            index = Math.min(index, Math.max(0, model.bank().questions().size() - 1));
            render();
        });
        Label type = UiTheme.label(typeName(question.type()), "muted");
        type.setId("qbank-question-type");
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        navigation.getChildren().addAll(spacer, type, delete, duplicate);

        QuestionTypeCatalog.renderEditor(question.type(),new QuestionEditorContext(model,index,body,navigation,spacer,
                imported,validateFields,errors,resources(),()->getScene()==null?null:getScene().getWindow(),this::render));

        body.getChildren().add(UiTheme.label("引用来源", essay ? "essay-section-title" : "editor-caption"));
        VBox refs = new VBox(6);
        sourceRows = new QuestionSourceListView(question.sourceRefs(), workspace, sourceNavigation, (refIndex, row) -> {
            Button remove = UiTheme.iconButton("close", "移除引用", () -> { });
            remove.setId("qbank-remove-source-" + refIndex);
            remove.setOnAction(event -> { model.deleteSourceRef(index, refIndex); render(); });
            row.getChildren().add(remove);
        });
        refs.getChildren().add(sourceRows);
        TextField sourceLink = new TextField();
        sourceLink.setId("qbank-source-link");
        sourceLink.setPromptText("粘贴 Source Anchor 链接…");
        Button useLink = UiTheme.button("使用链接", "plus", "text-action", () -> { });
        useLink.setId("qbank-use-source-link");
        useLink.setOnAction(event -> {
            try {
                var ref = sourceLinks.resolve(workspace, sourceLink.getText());
                model.addSourceRef(index, ref);
                errors.getChildren().clear();
                render();
            } catch (RuntimeException error) { showError(error.getMessage()); }
        });
        HBox linkInput = new HBox(8, sourceLink, useLink);
        HBox.setHgrow(sourceLink, Priority.ALWAYS);
        refs.getChildren().add(linkInput);
        body.getChildren().add(refs);
    }

    public io.quizforge.core.port.QuestionResourceInput resources() {
        return resource->{
            var pending=imported.get(resource.id());
            return pending==null?existingResources.open(resource):pending.open();
        };
    }
    private static String typeName(String type) {return QuestionTypeCatalog.label(type);}

}
