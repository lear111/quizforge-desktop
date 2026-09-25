package io.quizforge.extension.question;

import java.util.List;

/** AI boundary for file-backed question generation. All returned IDs are untrusted. */
public interface SourceAwareQuestionGenerator {
    List<Candidate> generate(Request request);

    record Request(String sourceContext, List<String> questionTypes, int questionCount) {
        public Request { questionTypes = List.copyOf(questionTypes); }
    }

    record Candidate(String type, String stem, String analysis, List<Option> options,
            List<String> correctOptionKeys, List<SourceRef> sourceRefs) {
        public Candidate {
            options = options == null ? List.of() : List.copyOf(options);
            correctOptionKeys = correctOptionKeys == null ? List.of() : List.copyOf(correctOptionKeys);
            sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        }
    }

    record Option(String key, String content) { }
    record SourceRef(String documentAssetId, String sectionId) { }
}
