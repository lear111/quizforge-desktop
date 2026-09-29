package io.quizforge.desktop.ui;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable question content, independent of an active session or archived entity. */
record QuestionPresentation(String type, String stem, List<Option> options,
        Set<String> correctAnswer, String analysis) {
    QuestionPresentation {
        options = List.copyOf(options);
        correctAnswer = Set.copyOf(correctAnswer);
    }

    record Option(String id, String content) { }

    String answerLabels(Set<String> answer) {
        String labels = java.util.stream.IntStream.range(0, options.size())
                .filter(index -> answer.contains(options.get(index).id()))
                .mapToObj(index -> String.valueOf((char) ('A' + index)))
                .collect(Collectors.joining("、"));
        return labels.isEmpty() ? "—" : labels;
    }
}
