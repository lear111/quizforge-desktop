package io.quizforge.core.question.type;

import java.util.List;

/** Executable types come exclusively from installed packages. Storage compatibility is separate. */
public final class QuestionTypes {
    private QuestionTypes(){ }
    private static final java.util.Map<String, QuestionTypeDefinition> EXTENSIONS = new java.util.LinkedHashMap<>();
    private static final java.util.Map<String, java.util.Map<String, io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition>> VERSIONS = new java.util.LinkedHashMap<>();
    public static boolean isCloze(String id){return "CLOZE".equals(id);}
    public static boolean isReading(String id){return "READING".equals(id);}
    public static boolean isMatching(String id){return "MATCHING".equals(id);}
    public static boolean isTranslation(String id){return "TRANSLATION".equals(id);}
    public static synchronized List<QuestionTypeDefinition> definitions(){
        return List.copyOf(EXTENSIONS.values());
    }
    public static synchronized void register(QuestionTypeDefinition definition) {
        java.util.Objects.requireNonNull(definition);
        if (find(definition.id()).isPresent()) throw new IllegalArgumentException("Question type already registered: " + definition.id());
        EXTENSIONS.put(definition.id(), definition);
        if (definition instanceof io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition extension)
            VERSIONS.computeIfAbsent(extension.id(), ignored -> new java.util.LinkedHashMap<>()).put(extension.version(), extension);
    }
    /** Keep old rules executable for frozen rounds; only the newest successful version is used for authoring. */
    public static synchronized void registerVersion(io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition definition) {
        var versions = VERSIONS.computeIfAbsent(definition.id(), ignored -> new java.util.LinkedHashMap<>());
        if (versions.values().stream().anyMatch(type -> !type.extensionId().equals(definition.extensionId())))
            throw new IllegalArgumentException("Question type belongs to another extension");
        if (versions.putIfAbsent(definition.version(), definition) != null)
            throw new IllegalArgumentException("Question type version already registered");
        var current = EXTENSIONS.get(definition.id());
        if (!(current instanceof io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition previous)
                || io.quizforge.core.question.type.extension.ExtensionVersion.compare(definition.version(), previous.version()) > 0)
            EXTENSIONS.put(definition.id(), definition);
    }
    public static synchronized void unregisterVersion(String id, String version) {
        var versions = VERSIONS.get(id);
        if (versions == null) return;
        versions.remove(version);
        if (versions.isEmpty()) { VERSIONS.remove(id); EXTENSIONS.remove(id); }
        else EXTENSIONS.put(id, versions.values().stream().max((a,b) ->
                io.quizforge.core.question.type.extension.ExtensionVersion.compare(a.version(),b.version())).orElseThrow());
    }
    public static synchronized io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition requireVersion(String id, String version) {
        var definition = VERSIONS.getOrDefault(id, java.util.Map.of()).get(version);
        if (definition == null) throw new IllegalStateException("Install question type extension version: " + id + "@" + version);
        return definition;
    }
    public static synchronized void unregister(String id) {
        EXTENSIONS.remove(id);
        VERSIONS.remove(id);
    }
    public static boolean isExtension(String id) { return find(id).map(type -> type instanceof io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition).orElse(false); }
    /** Storage readers may preserve an opaque type before its executable extension is installed. */
    public static QuestionTypeDefinition forData(io.quizforge.core.question.model.Question question) {
        return find(question.type()).orElseGet(() -> new io.quizforge.core.question.type.extension.MissingExtensionQuestionType(question));
    }
    public static boolean isChoice(String id) {
        return "SINGLE_CHOICE".equals(id) || "MULTIPLE_CHOICE".equals(id) || find(id).map(type -> type.payloadClass() ==
                io.quizforge.core.question.model.choice.ChoicePayload.class).orElse(false);
    }
    public static boolean isEssay(String id) {
        return "ESSAY".equals(id) || find(id).map(type -> type.payloadClass() ==
                io.quizforge.core.question.compat.essay.EssayPayload.class).orElse(false);
    }
    public static boolean isSingleChoice(String id) {
        return "SINGLE_CHOICE".equals(id) || isChoice(id) && find(id).map(type -> !type.multipleSelection()).orElse(false);
    }
    public static java.util.Optional<QuestionTypeDefinition> find(String id) {
        return definitions().stream().filter(type->type.id().equals(id)).findFirst();
    }
    public static QuestionTypeDefinition require(String id) {
        return find(id).orElseThrow(()->
                new io.quizforge.core.QuizForgeException(io.quizforge.core.ErrorCode.QUESTION_BANK_FILE_INVALID,"缺少对应题型扩展："+id+"，请安装后再使用。"));
    }
}
