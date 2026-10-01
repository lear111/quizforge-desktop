package io.quizforge.core.question.type.subjective.essay;

import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.model.QuestionAnswerSpec;

public record EssayAnswerSpec(QuestionContent referenceAnswer) implements QuestionAnswerSpec { }
