package io.quizforge.core.question;

import io.quizforge.extension.question.SourceAwareQuestionGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Validates untrusted AI candidates, then assigns every durable question and option ID locally. */
public final class QuestionBankV2Assembler {
    public record Result(QuestionBank bank, int rejected) { }

    public Result assemble(String title, String existingId, List<SourceDocumentSnapshot> snapshots,
            Map<String, Set<String>> selectedSectionIds, List<SourceAwareQuestionGenerator.Candidate> candidates,
            Set<QuestionType> allowedTypes, int requested) {
        Map<String, SourceDocumentSnapshot> byId = new HashMap<>();
        snapshots.forEach(s -> byId.put(s.assetId(), s));
        List<Question> accepted = new ArrayList<>();
        int rejected = 0;
        for (SourceAwareQuestionGenerator.Candidate candidate : candidates) {
            if (accepted.size() >= requested) { rejected++; continue; }
            Question entry = accept(candidate, byId, selectedSectionIds, allowedTypes);
            if (entry == null) rejected++;
            else accepted.add(entry);
        }
        return new Result(new QuestionBank(existingId == null ? "qb_" + UUID.randomUUID() : existingId, title, "2.0", List.of(), accepted, List.of()), rejected);
    }

    private Question accept(SourceAwareQuestionGenerator.Candidate candidate,
            Map<String, SourceDocumentSnapshot> sources, Map<String, Set<String>> selected,
            Set<QuestionType> allowed) {
        if (candidate == null || blank(candidate.stem()) || blank(candidate.analysis())
                || candidate.options().size() < 2 || candidate.sourceRefs().isEmpty()) return null;
        QuestionType type;
        try { type = QuestionType.valueOf(candidate.type()); }
        catch (RuntimeException error) { return null; }
        if (!allowed.contains(type)) return null;
        Map<String, String> optionIds = new HashMap<>();
        List<ChoiceOption> options = new ArrayList<>();
        for (SourceAwareQuestionGenerator.Option option : candidate.options()) {
            if (option == null || blank(option.key()) || blank(option.content())
                    || optionIds.putIfAbsent(option.key(), "opt_" + UUID.randomUUID()) != null) return null;
            options.add(new ChoiceOption(optionIds.get(option.key()), new TextContent(option.content().trim())));
        }
        Set<String> correct = new HashSet<>(candidate.correctOptionKeys());
        if (correct.size() != candidate.correctOptionKeys().size() || !optionIds.keySet().containsAll(correct)
                || (type == QuestionType.SINGLE_CHOICE && correct.size() != 1)
                || (type == QuestionType.MULTIPLE_CHOICE
                    && (correct.size() < 2 || correct.size() >= options.size()))) return null;
        List<SourceRef> refs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SourceAwareQuestionGenerator.SourceRef ref : candidate.sourceRefs()) {
            if (ref == null || !seen.add(ref.documentAssetId() + "\0" + ref.sectionId())) return null;
            SourceDocumentSnapshot document = sources.get(ref.documentAssetId());
            if (document == null || !selected.getOrDefault(ref.documentAssetId(), Set.of()).contains(ref.sectionId())) return null;
            SourceDocumentSnapshot.Section section = document.chapters().stream()
                    .flatMap(chapter -> chapter.sections().stream())
                    .filter(item -> item.id().equals(ref.sectionId())).findFirst().orElse(null);
            if (section == null) return null;
            refs.add(SourceRef.anchor(document.assetId(), document.contentId(), section.id(),
                    1, document.title(), section.title()));
        }
        List<String> correctIds = candidate.options().stream()
                .filter(option -> correct.contains(option.key())).map(option -> optionIds.get(option.key())).toList();
        return Question.choice("q_" + UUID.randomUUID(), type.name(), new TextContent(candidate.stem().trim()), new TextContent(candidate.analysis().trim()), refs, new ChoicePayload(options), new ChoiceAnswerSpec(correctIds));
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
