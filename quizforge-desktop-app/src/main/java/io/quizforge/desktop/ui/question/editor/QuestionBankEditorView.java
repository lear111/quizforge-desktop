package io.quizforge.desktop.ui.question.editor;

import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.service.QuestionBankEditorModel;
import io.quizforge.core.question.source.QuestionSourceLinkService;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.codec.QuestionDataCodec;
import io.quizforge.core.workspace.model.WorkspaceId;
import io.quizforge.desktop.dev.DevelopmentRefreshable;
import io.quizforge.desktop.ui.content.StagedContentResource;
import io.quizforge.desktop.ui.question.extension.ExtensionEditorFields;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
import io.quizforge.desktop.ui.question.shared.QuestionTypeCatalog;
import io.quizforge.desktop.ui.question.source.QuestionSourceNavigationAdapter;
import io.quizforge.desktop.ui.shared.EditorUi;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** One persistent HTML editor shell; native code owns the bank and scoped platform commands. */
public final class QuestionBankEditorView extends VBox implements DevelopmentRefreshable {
    private final QuestionBankEditorModel model;
    private final WorkspaceId workspace;
    private final QuestionSourceLinkService sourceLinks;
    private final QuestionSourceNavigationAdapter sourceNavigation;
    private final Consumer<QuestionBank> save;
    private final io.quizforge.core.port.QuestionResourceInput existingResources;
    private final Map<String,StagedContentResource> imported = new LinkedHashMap<>();
    private final List<Runnable> validateFields = new ArrayList<>();
    private final VBox body = new VBox();
    private final VBox errors = new VBox(3);
    private ExtensionEditorFields fields;
    private int index;
    private String uiQuestionId="";
    private Map<String,Boolean> ui=Map.of();
    private Consumer<Map<String,Boolean>> onUiChange=preferences->{ };
    private BiConsumer<QuestionBank,Integer> onQuestionChange = (bank,selected) -> { };

    public QuestionBankEditorView(QuestionBank bank, WorkspaceId workspace,
            QuestionSourceLinkService sourceLinks, QuestionSourceNavigationAdapter sourceNavigation,
            Consumer<QuestionBank> save) {
        this(bank,workspace,sourceLinks,sourceNavigation,save,io.quizforge.core.port.QuestionResourceInput.NONE);
    }
    public QuestionBankEditorView(QuestionBank bank, WorkspaceId workspace,
            QuestionSourceLinkService sourceLinks, QuestionSourceNavigationAdapter sourceNavigation,
            Consumer<QuestionBank> save, io.quizforge.core.port.QuestionResourceInput existingResources) {
        model = new QuestionBankEditorModel(bank);
        this.workspace = workspace; this.sourceLinks = sourceLinks;
        this.sourceNavigation = sourceNavigation; this.save = save; this.existingResources = existingResources;
        setId("question-bank-editor"); getStyleClass().add("qbank-editor"); setMaxWidth(Double.MAX_VALUE);
        setStyle("-fx-padding: 0;"); // Page width and surrounding space belong to the extension layout API.
        errors.managedProperty().bind(javafx.beans.binding.Bindings.isNotEmpty(errors.getChildren()));
        getChildren().addAll(errors,body);
        if(nativeEditor())javafx.scene.layout.VBox.setVgrow(body,javafx.scene.layout.Priority.ALWAYS);
        // Native menu/shortcut saves share the same draft flush as the HTML Save button.
        EditorUi.saveShortcut(this,this::saveChanges);
        render();
    }
    public boolean dirty() { return model.dirty(); }
    public boolean nativeEditor(){return io.quizforge.desktop.browser.webview2.WebView2LearningSurface.enabled();}
    public java.util.concurrent.CompletionStage<Void> ready(){return fields.ready();}
    public java.util.concurrent.CompletionStage<Void> prepareCloseAsync(){return fields.prepareCloseAsync();}
    public void cancelClose(){if(fields!=null)fields.cancelClose();}
    public void destroy(){if(fields!=null)fields.close();}
    public QuestionBank bank(){return model.bank();}
    public io.quizforge.desktop.browser.webview2.WebView2EditorSurface nativeSurface(){return fields.nativeSurface();}
    @Override public void refreshForDevelopment() {
        changeQuestion(this::render);
    }
    public void refreshSources() { if (fields != null) fields.showShell(context()); }
    public void jumpTo(int target) {
        if (target < 0 || target >= model.bank().questions().size() || target == index) return;
        if (getScene() == null && !model.dirty()) { index = target; render(); return; }
        changeQuestion(() -> { index = target; render(); });
    }
    private void changeQuestion(Runnable change) {
        try { validateFields.forEach(Runnable::run); change.run(); }
        catch (RuntimeException failure) { fields.showError(failure.getMessage()); }
    }
    public void moveQuestion(int from,int to) {
        if (from == to) return;
        changeQuestion(() -> { model.moveQuestion(from,to); index = to; render(); });
    }
    public void onQuestionChange(BiConsumer<QuestionBank,Integer> action) {
        onQuestionChange = action; onQuestionChange.accept(model.bank(),index);
    }
    public void onUiChange(Consumer<Map<String,Boolean>> action) { onUiChange=action;action.accept(ui); }
    public void saveChanges() {
        changeQuestion(() -> save.accept(model.bank()));
    }
    private QuestionEditorContext context() {
        return new QuestionEditorContext(model,index,body,new HBox(),new Region(),imported,
                validateFields,errors,resources(),()->getScene()==null?null:getScene().getWindow(),this::render);
    }
    private void render() {
        var id=model.bank().questions().isEmpty()?"":model.bank().questions().get(index).id();
        // Retain the current shell geometry until the staged extension publishes its preferences.
        if(!id.equals(uiQuestionId))uiQuestionId=id;
        onQuestionChange.accept(model.bank(),index);
        validateFields.clear(); errors.getChildren().clear();
        var context = context();
        if (fields == null) fields = ExtensionEditorFields.renderShell(context,new ExtensionEditorFields.Shell() {
            public Map<String,Object> state() { return shellState(); }
            public void execute(String action,Object argument) { executeCommand(action,argument); }
            public void configureUi(Map<String,Boolean> preferences) { ui=preferences;onUiChange.accept(ui); }
        });
        else fields.showShell(context);
    }
    private Map<String,Object> shellState() {
        var state = new LinkedHashMap<String,Object>();
        state.put("index",index); state.put("count",model.bank().questions().size());
        state.put("questions",java.util.stream.IntStream.range(0,model.bank().questions().size()).mapToObj(i->{var q=model.bank().questions().get(i);return Map.of("index",i,"id",q.id(),"type",q.type(),"label",QuestionTypeCatalog.label(q.type()));}).toList());
        state.put("ui",ui);
        state.put("types",QuestionTypeCatalog.editableTypes().stream()
                .map(type -> Map.of("id",type,"label",QuestionTypeCatalog.label(type))).toList());
        var question = model.bank().questions().isEmpty() ? null : model.bank().questions().get(index);
        state.put("question",question == null ? null : QuestionDataCodec.encodePersisted(question));
        state.put("editable",question != null && QuestionTypes.find(question.type()).isPresent());
        state.put("label",question == null ? "" : QuestionTypeCatalog.label(question.type()));
        state.put("sources",question == null || question.sourceRefs().isEmpty() ? List.of()
                : sourceNavigation.inspect(workspace,question.sourceRefs()).stream().map(source -> Map.of(
                        "label",source.label(),"message",source.message(),"navigable",source.navigable())).toList());
        return state;
    }
    /** Every operation is scoped to this editor's selected question, never an arbitrary file or workspace. */
    private void executeCommand(String action,Object argument) {
        if (action == null) throw new IllegalArgumentException("缺少编辑操作");
        switch (action) {
            case "save" -> { save.accept(model.bank()); return; }
            case "navigate" -> index = checkedIndex(argument,model.bank().questions().size());
            case "add" -> {
                if (!(argument instanceof String type) || !QuestionTypeCatalog.editableTypes().contains(type))
                    throw new IllegalArgumentException("缺少对应题型扩展");
                index = model.addQuestion(type);
            }
            case "duplicate" -> { requireQuestion(); index = model.duplicateQuestion(index); }
            case "delete" -> {
                requireQuestion(); model.deleteQuestion(index);
                index = Math.min(index,Math.max(0,model.bank().questions().size()-1));
            }
            case "source.add" -> {
                requireQuestion();
                if (!(argument instanceof String link)) throw new IllegalArgumentException("来源链接无效");
                var ref = sourceLinks.resolve(workspace,link); model.addSourceRef(index,ref);
            }
            case "source.remove" -> {
                requireQuestion(); model.deleteSourceRef(index,checkedIndex(argument,model.bank().questions().get(index).sourceRefs().size()));
            }
            case "source.open" -> {
                requireQuestion(); var refs = model.bank().questions().get(index).sourceRefs();
                sourceNavigation.open(workspace,refs.get(checkedIndex(argument,refs.size()))); return;
            }
            default -> throw new IllegalArgumentException("不支持的编辑操作");
        }
        render();
    }
    private void requireQuestion() {
        if (model.bank().questions().isEmpty()) throw new IllegalArgumentException("当前题库没有题目");
    }
    private static int checkedIndex(Object argument,int size) {
        if (!(argument instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue() || number.intValue() < 0 || number.intValue() >= size)
            throw new IllegalArgumentException("题目或引用位置无效");
        return number.intValue();
    }
    public io.quizforge.core.port.QuestionResourceInput resources() {
        return resource -> {
            var pending = imported.get(resource.id());
            return pending == null ? existingResources.open(resource) : pending.open();
        };
    }
}
