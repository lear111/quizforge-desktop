package io.quizforge.desktop.ui.question.shared;

import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.desktop.ui.question.objective.choice.ChoiceEditorFields;
import io.quizforge.desktop.ui.question.subjective.essay.EssayEditorFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Desktop names and editors; core owns the corresponding data and rule definitions. */
public final class QuestionTypeCatalog {
    private record Binding(String id, String label, Consumer<QuestionEditorContext> editor) { }
    private static final Map<String, Binding> BINDINGS = bindings(List.of(
            new Binding("SINGLE_CHOICE", "单选题", ChoiceEditorFields::render),
            new Binding("MULTIPLE_CHOICE", "多选题", ChoiceEditorFields::render),
            new Binding("ESSAY", "作文题", EssayEditorFields::render)));

    private QuestionTypeCatalog() { }

    private static Map<String, Binding> bindings(List<Binding> entries) {
        var result = new LinkedHashMap<String, Binding>();
        for (var entry : entries) {
            QuestionTypes.require(entry.id());
            if (result.putIfAbsent(entry.id(), entry) != null)
                throw new IllegalStateException("Duplicate question UI type: " + entry.id());
        }
        for (var definition : QuestionTypes.definitions()) {
            if (!result.containsKey(definition.id()))
                throw new IllegalStateException("Missing question UI type: " + definition.id());
        }
        return Map.copyOf(result);
    }

    private static Binding require(String id) {
        QuestionTypes.require(id);
        return BINDINGS.get(id);
    }

    public static void renderEditor(String type, QuestionEditorContext context) {
        require(type).editor().accept(context);
    }

    public static String label(String type) { return require(type).label(); }

    public static List<String> editableTypes() {
        return QuestionTypes.definitions().stream().map(type -> type.id()).toList();
    }
}
