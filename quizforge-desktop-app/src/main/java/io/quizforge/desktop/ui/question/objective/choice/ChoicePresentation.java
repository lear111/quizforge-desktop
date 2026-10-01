package io.quizforge.desktop.ui.question.objective.choice;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable question content, independent of an active session or archived entity. */
public record ChoicePresentation(String type, String stem, List<Option> options,
        Set<String> correctAnswer, String analysis) {
    public ChoicePresentation {
        options = List.copyOf(options);
        correctAnswer = Set.copyOf(correctAnswer);
    }

    public record Option(String id, String content) { }

    public String answerLabels(Set<String> answer) {
        String labels = java.util.stream.IntStream.range(0, options.size())
                .filter(index -> answer.contains(options.get(index).id()))
                .mapToObj(index -> String.valueOf((char) ('A' + index)))
                .collect(Collectors.joining("、"));
        return labels.isEmpty() ? "—" : labels;
    }
}
