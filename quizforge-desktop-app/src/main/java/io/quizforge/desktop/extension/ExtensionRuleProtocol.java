package io.quizforge.desktop.extension;

import java.io.IOException;
import java.io.Reader;

/** UTF-8 JSON lines over private process pipes; bound the frame before JSON parsing. */
final class ExtensionRuleProtocol {
    static final int MAX_LINE = 16 * 1024 * 1024;
    static String readLine(Reader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int c; (c = reader.read()) != -1;) {
            if (c == '\n') return line.toString();
            if (line.length() >= MAX_LINE) throw new IOException("Extension worker frame is too large");
            line.append((char)c);
        }
        return line.isEmpty() ? null : line.toString();
    }
    private ExtensionRuleProtocol() {}
}
