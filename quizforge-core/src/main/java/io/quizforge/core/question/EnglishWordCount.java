package io.quizforge.core.question;

import java.util.regex.Pattern;

/** A word is an ASCII letter/digit run, with internal apostrophes or hyphens. */
public final class EnglishWordCount {
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9]+(?:['’\\-][A-Za-z0-9]+)*");
    private EnglishWordCount() { }
    public static int count(String text) {
        return text == null ? 0 : Math.toIntExact(WORD.matcher(text).results().count());
    }
}
