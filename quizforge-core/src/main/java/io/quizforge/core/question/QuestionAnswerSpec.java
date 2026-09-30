package io.quizforge.core.question;

public sealed interface QuestionAnswerSpec permits ChoiceAnswerSpec, EssayAnswerSpec { }
