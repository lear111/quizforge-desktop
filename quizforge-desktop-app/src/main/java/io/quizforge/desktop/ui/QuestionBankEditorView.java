package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;

import io.quizforge.core.workspace.WorkspaceId;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;

/** One-question editor. Every control mutates the same portable bank model used by Practice. */
final class QuestionBankEditorView extends VBox {
    private final QuestionBankEditorModel model;
    private final WorkspaceId workspace;
    private final QuestionSourceLinkService sourceLinks;
    private final QuestionSourceNavigationAdapter sourceNavigation;
    private QuestionSourceListView sourceRows;
    private final Consumer<QuestionBank> save;
    private final io.quizforge.core.port.QuestionResourceInput existingResources;
    private final java.util.Map<String,io.quizforge.infrastructure.filesystem.qbank.QBankImageImporter.ImportedImage> imported=new java.util.LinkedHashMap<>();
    private final java.util.List<Runnable> validateFields=new java.util.ArrayList<>();
    private final VBox body = new VBox(14);
    private final VBox errors = new VBox(3);
    private int index;
    private BiConsumer<QuestionBank, Integer> onQuestionChange = (bank, selected) -> { };

    QuestionBankEditorView(QuestionBank bank, WorkspaceId workspace,
            QuestionSourceLinkService sourceLinks,
            QuestionSourceNavigationAdapter sourceNavigation, Consumer<QuestionBank> save) {
        this(bank,workspace,sourceLinks,sourceNavigation,save,io.quizforge.core.port.QuestionResourceInput.NONE);
    }
    QuestionBankEditorView(QuestionBank bank, WorkspaceId workspace, QuestionSourceLinkService sourceLinks,
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

    boolean dirty() { return model.dirty(); }
    void refreshForDevelopment() {
        getChildren().set(0, EditorUi.toolbar("题库编辑", "qbank-save", this::save));
        render();
    }
    void refreshSources() { if (sourceRows != null) sourceRows.refresh(); }

    void jumpTo(int target) {
        if (target < 0 || target >= model.bank().questions().size()) return;
        index = target;
        render();
    }

    void onQuestionChange(BiConsumer<QuestionBank, Integer> action) {
        onQuestionChange = action;
        onQuestionChange.accept(model.bank(), index);
    }

    private void save() {
        errors.getChildren().clear();
        try { validateFields.forEach(Runnable::run); save.accept(model.bank()); }
        catch (RuntimeException error) { showError(error.getMessage()); }
    }

    private void showError(String message) {
        errors.getChildren().setAll(UiTheme.label(message == null ? "Could not save QuestionBank" : message,
                "editor-error"));
    }

    private void render() {
        onQuestionChange.accept(model.bank(), index);
        sourceRows = null;
        validateFields.clear();
        body.getChildren().clear();
        boolean essay = !model.bank().questions().isEmpty() && "ESSAY".equals(model.bank().questions().get(index).type());
        getStyleClass().remove("essay-editor");
        if (essay) getStyleClass().add("essay-editor");
        TextField bankTitle = new TextField(model.bank().title());
        bankTitle.setId("qbank-title");
        bankTitle.setPromptText("题库标题");
        bankTitle.getStyleClass().add("editor-document-title");
        bankTitle.textProperty().addListener((obs, old, text) -> model.setTitle(text));
        body.getChildren().add(bankTitle);

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
        for (String type : List.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "ESSAY")) {
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
        MenuButton actions = EditorUi.menu("more", "题目操作");
        actions.setId("qbank-question-actions");
        MenuItem duplicate = new MenuItem("复制题目");
        duplicate.setOnAction(event -> { index = model.duplicateQuestion(index); render(); });
        MenuItem delete = new MenuItem("删除题目");
        delete.setOnAction(event -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "This question and its options will be removed.", ButtonType.CANCEL, ButtonType.OK);
            confirm.setHeaderText("Delete this question?");
            UiTheme.apply(confirm);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            model.deleteQuestion(index);
            index = Math.min(index, Math.max(0, model.bank().questions().size() - 1));
            render();
        });
        actions.getItems().addAll(duplicate, delete);
        ChoiceBox<String> type = new ChoiceBox<>(FXCollections.observableArrayList("SINGLE_CHOICE", "MULTIPLE_CHOICE", "ESSAY"));
        type.setId("qbank-question-type");
        type.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String value) { return typeName(value); }
            @Override public String fromString(String value) { return "单选题".equals(value) ? "SINGLE_CHOICE" : "作文题".equals(value)?"ESSAY":"MULTIPLE_CHOICE"; }
        });
        type.setValue(question.type());
        type.valueProperty().addListener((obs, old, value) -> { try {model.setType(index, value);render();} catch(RuntimeException failure){render();showError(failure.getMessage());} });
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        navigation.getChildren().addAll(spacer, type, actions);

        if("ESSAY".equals(question.type())) {
            navigation.getChildren().remove(actions);
            Button deleteButton = new Button("删除题目");deleteButton.setId("qbank-delete-question");
            deleteButton.getStyleClass().add("essay-toolbar-action");deleteButton.setOnAction(e -> delete.fire());
            Button duplicateButton = new Button("复制题目");duplicateButton.setId("qbank-duplicate-question");
            duplicateButton.getStyleClass().add("essay-toolbar-action");duplicateButton.setOnAction(e -> duplicate.fire());
            navigation.getChildren().addAll(deleteButton, duplicateButton);
            essayFields(question, navigation, spacer);
        }
        else {
        TextArea stem = area(QuestionText.prompt(question), "qbank-question-stem", 3);
        stem.textProperty().addListener((obs, old, value) -> model.setStem(index, value));
        stem.setPromptText("输入题干…");
        body.getChildren().addAll(UiTheme.label("题干", "editor-caption"), stem,
                UiTheme.label("选项 · 勾选正确答案", "editor-caption"));
        ToggleGroup correctGroup = new ToggleGroup();
        VBox options = new VBox(8);
        for (int i = 0; i < question.choicePayload().options().size(); i++) {
            final int optionIndex = i;
            var option = question.choicePayload().options().get(i);
            TextField content = new TextField(QuestionText.option(option));
            content.setId("qbank-option-" + i);
            content.textProperty().addListener((obs, old, value) -> model.setOptionContent(index, optionIndex, value));
            HBox.setHgrow(content, Priority.ALWAYS);
            javafx.scene.control.ButtonBase correct;
            if ("SINGLE_CHOICE".equals(question.type())) {
                RadioButton radio = new RadioButton();
                radio.setToggleGroup(correctGroup);
                radio.setSelected(question.choiceAnswerSpec().correctOptionIds().contains(option.id()));
                radio.setOnAction(event -> model.setCorrect(index, option.id(), true));
                correct = radio;
            } else {
                CheckBox check = new CheckBox();
                check.setSelected(question.choiceAnswerSpec().correctOptionIds().contains(option.id()));
                check.setOnAction(event -> model.setCorrect(index, option.id(), check.isSelected()));
                correct = check;
            }
            correct.setId("qbank-correct-" + i);
            correct.setAccessibleText("将选项 " + (char) ('A' + i) + " 设为正确答案");
            Button remove = UiTheme.iconButton("close", "删除选项", () -> { });
            remove.setId("qbank-remove-option-" + i);
            remove.setOnAction(event -> { model.deleteOption(index, optionIndex); render(); });
            HBox row = new HBox(10, correct, UiTheme.label(String.valueOf((char) ('A' + i)), "muted"), content, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            content.setMinWidth(40);
            options.getChildren().add(row);
        }
        Button addOption = UiTheme.button("添加选项", "plus", "text-action", () -> { });
        addOption.setId("qbank-add-option");
        addOption.setOnAction(event -> { model.addOption(index); render(); });
        options.getChildren().add(addOption);
        body.getChildren().add(options);
        }

        if (!"ESSAY".equals(question.type())) {
            TextArea analysis = area(QuestionText.analysis(question), "qbank-analysis", 4);
            analysis.textProperty().addListener((obs, old, value) -> model.setAnalysis(index, value));
            body.getChildren().addAll(UiTheme.label("解析", "editor-caption"), analysis);
        }
        body.getChildren().add(UiTheme.label("引用来源", "editor-caption"));
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

    private TextArea area(String value, String id, int rows) {
        return EditorUi.content(value, id, false);
    }
    io.quizforge.core.port.QuestionResourceInput resources() {
        return resource->{
            var pending=imported.get(resource.id());
            return pending==null?existingResources.open(resource):pending.open();
        };
    }
    private void essayFields(Question question, HBox navigation, javafx.scene.layout.Region spacer) {
        TextField score=new TextField(question.scoreSpec().defaultMaxScore().toPlainString());score.setId("essay-max-score");score.setPrefColumnCount(5);
        var payload=question.essayPayload();
        TextField placeholder=new TextField(payload.placeholder()==null?"":payload.placeholder());placeholder.setId("essay-placeholder");placeholder.setPromptText("作答提示（可选）");
        Runnable commit=()->{
            model.setMaxScore(index,new java.math.BigDecimal(score.getText().trim()));
            model.setEssayPayload(index,new EssayPayload(placeholder.getText().isEmpty()?null:placeholder.getText()));
        };
        validateFields.add(commit);
        for(var field:List.of(score,placeholder)) field.textProperty().addListener((o,a,b)->{
            try{commit.run();errors.getChildren().clear();}catch(RuntimeException error){showError("请填写有效的分值。");}
        });
        score.getStyleClass().add("essay-score-input");
        HBox scoreControl=new HBox(6,UiTheme.label("分值","editor-caption"),score);
        scoreControl.setAlignment(Pos.CENTER_LEFT);
        navigation.getChildren().add(navigation.getChildren().indexOf(spacer),scoreControl);
        final int questionIndex=index;
        var promptPreview=new VBox();promptPreview.setId("essay-prompt-preview");
        promptPreview.getStyleClass().add("essay-content-preview");
        Runnable refreshPreview=()->{
            promptPreview.getChildren().setAll(essayPreview(model.bank().questions().get(questionIndex).prompt(),"essay-edit-preview-"));
        };
        refreshPreview.run();
        Button editPrompt=new Button("编辑");editPrompt.setId("essay-edit-prompt");
        editPrompt.getStyleClass().add("essay-section-edit");
        editPrompt.setOnAction(event->editEssayContent("编辑题干",model.bank().questions().get(questionIndex).prompt(),
                content->model.setPrompt(questionIndex,content),refreshPreview));
        var promptSection=new VBox(12,new HBox(12,UiTheme.label("题干","essay-section-title"),editPrompt),promptPreview);
        promptSection.getStyleClass().add("essay-content-section");

        var referencePreview=new VBox(14);referencePreview.setId("essay-reference-preview");
        referencePreview.getStyleClass().add("essay-content-preview");
        Runnable refreshReference=()->{
            var current=model.bank().questions().get(questionIndex);
            referencePreview.getChildren().clear();
            var reference=current.essayAnswerSpec().referenceAnswer();
            var analysis=current.analysis();
            boolean hasReference=hasContent(reference),hasAnalysis=hasContent(analysis);
            if(hasReference) referencePreview.getChildren().add(essayPreview(reference,"essay-reference-preview-"));
            if(hasAnalysis) {
                if(hasReference)referencePreview.getChildren().add(UiTheme.label("解析","editor-caption"));
                referencePreview.getChildren().add(essayPreview(analysis,"essay-analysis-preview-"));
            }
            if(!hasReference && !hasAnalysis)referencePreview.getChildren().add(UiTheme.label("暂无参考答案或解析","essay-preview-empty"));
        };
        refreshReference.run();
        MenuButton editReference=new MenuButton("编辑");editReference.setId("essay-edit-reference");
        editReference.getStyleClass().add("essay-section-edit");
        var referenceItem=new MenuItem("参考答案");
        referenceItem.setOnAction(event->editEssayContent("编辑参考答案",model.bank().questions().get(questionIndex).essayAnswerSpec().referenceAnswer(),
                content->model.setReferenceAnswer(questionIndex,optionalContent(content)),refreshReference));
        var analysisItem=new MenuItem("解析");
        analysisItem.setOnAction(event->editEssayContent("编辑解析",model.bank().questions().get(questionIndex).analysis(),
                content->model.setAnalysis(questionIndex,optionalContent(content)),refreshReference));
        editReference.getItems().addAll(referenceItem,analysisItem);
        var referenceSection=new VBox(12,new HBox(12,UiTheme.label("参考答案与解析（可为空）","essay-section-title"),editReference),referencePreview);
        referenceSection.getStyleClass().add("essay-content-section");

        TextArea guidance=area(question.evaluationSpec()==null?"":question.evaluationSpec().evaluatorGuidance(),"essay-evaluation-guidance",5);
        guidance.setPromptText("填写评分细则（可为空）");guidance.getStyleClass().add("essay-guidance-input");
        guidance.textProperty().addListener((o,a,b)->model.setEvaluatorGuidance(questionIndex,b));
        var guidanceSection=new VBox(12,UiTheme.label("评分细则（可为空）","essay-section-title"),guidance);
        guidanceSection.getStyleClass().add("essay-content-section");

        var more=new TitledPane("更多设置",new VBox(10,placeholder));more.setExpanded(false);
        more.setId("essay-more-settings");more.getStyleClass().add("essay-more-settings");
        body.getChildren().addAll(promptSection,referenceSection,guidanceSection,more);
    }
    private javafx.scene.Node essayPreview(QuestionContent content,String prefix) {
        var node=QuestionContentRenderer.render(content,model.bank().resources(),resources(),prefix);
        if(node instanceof Label label) {
            label.getStyleClass().remove("question-stem");label.getStyleClass().add("authoring-essay-text");
        }
        return node;
    }
    private void editEssayContent(String title,QuestionContent content,Consumer<QuestionContent> apply,Runnable refresh) {
        try {
            var result=CanvasEditorWindow.openEditor(getScene().getWindow(),title,content==null?new TextContent(""):content,
                    model.bank().resources(),resources());
            if(result.saved()) {
                for(var added:result.addedResources()){model.addResource(added.resource());imported.put(added.resource().id(),added);}
                apply.accept(result.content());refresh.run();errors.getChildren().clear();
            }
        }catch(RuntimeException failure){showError(failure.getMessage());}
    }
    private static boolean hasContent(QuestionContent content){return content!=null && !(content instanceof TextContent text && text.text().isBlank());}
    private static QuestionContent optionalContent(QuestionContent content){return hasContent(content)?content:null;}
    private static String typeName(String type) {return "ESSAY".equals(type)?"作文题":"SINGLE_CHOICE".equals(type)?"单选题":"多选题";}

}
