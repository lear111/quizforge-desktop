package io.quizforge.infrastructure.testing;

import io.quizforge.core.practice.PersistentPracticeRuntime;
import io.quizforge.core.question.type.QuestionTypes;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Test input driver for installed choice extensions; production has no native choice-click command. */
public final class ExtensionPracticeTestAnswers {
    private ExtensionPracticeTestAnswers() { }
    public static Set<String> selected(PersistentPracticeRuntime runtime) {
        var answer = runtime.extensionAnswer(runtime.session().current().id());
        if (answer == null) return Set.of();
        var fields = (Map<?,?>)answer.value();
        return Set.copyOf(((List<?>)fields.get("selectedOptionIds")).stream().map(String.class::cast).toList());
    }
    public static void select(PersistentPracticeRuntime runtime, String optionId) {
        var question = runtime.session().current();
        if (question.choicePayload().options().stream().noneMatch(option -> option.id().equals(optionId)))
            throw new IllegalArgumentException("Unknown option ID");
        var selected = new HashSet<>(selected(runtime));
        if (QuestionTypes.require(question.type()).multipleSelection()) {
            if (!selected.add(optionId)) selected.remove(optionId);
        } else {
            selected.clear();selected.add(optionId);
        }
        runtime.saveChoiceDraft(selected);
    }
}
